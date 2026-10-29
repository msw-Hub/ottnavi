package com.ottnavi.llmdraft.repository;

import com.ottnavi.llmdraft.config.LlmDraftProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Gemini 호출 상한 카운터(분당·하루)와 같은 작품 쿨다운을 Redis에 둔다.
 * 키:
 * <ul>
 *   <li>llm:quota:min:{epochMinute} — 이번 1분(고정 창)의 호출 수, TTL 2분</li>
 *   <li>llm:quota:day:{yyyy-MM-dd} — 태평양 시간(America/Los_Angeles) 날짜의 호출 수, TTL 48시간.
 *       구글 무료 일일 한도가 태평양 시간 자정에 리셋되므로 같은 기준으로 센다</li>
 *   <li>llm:cooldown:{titleId} — 같은 작품 재번역 대기, TTL = 쿨다운 시간</li>
 * </ul>
 * 두 상한 검사와 카운터 증가는 Lua 스크립트 하나로 원자적으로 한다(동시 요청이 상한을 함께 넘지 않게).
 */
@Component
public class LlmQuotaStore {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final String MINUTE_PREFIX = "llm:quota:min:";
    private static final String DAY_PREFIX = "llm:quota:day:";
    private static final String COOLDOWN_PREFIX = "llm:cooldown:";
    private static final long MINUTE_TTL_MS = Duration.ofMinutes(2).toMillis();
    private static final long DAY_TTL_MS = Duration.ofHours(48).toMillis();

    /**
     * KEYS[1]=분 키, KEYS[2]=날 키. ARGV: 분당 상한, 하루 상한, 분 TTL(ms), 날 TTL(ms).
     * 반환: 0 통과(두 카운터 +1), 1 분당 초과, 2 하루 초과. 하루 초과를 먼저 본다.
     */
    private static final String ACQUIRE_SCRIPT = """
            local m = tonumber(redis.call('GET', KEYS[1]) or '0')
            local d = tonumber(redis.call('GET', KEYS[2]) or '0')
            if d >= tonumber(ARGV[2]) then
              return 2
            end
            if m >= tonumber(ARGV[1]) then
              return 1
            end
            redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            redis.call('INCR', KEYS[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            return 0
            """;

    private final StringRedisTemplate redis;
    private final LlmDraftProperties properties;
    private final Clock clock;
    private final DefaultRedisScript<Long> acquireScript = new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);

    public LlmQuotaStore(StringRedisTemplate redis, LlmDraftProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /** Gemini를 호출하기 직전에 부른다. 통과하면 분당·하루 카운터를 1 올린다(호출이 실패해도 되돌리지 않는다) */
    public QuotaDecision tryAcquire() {
        Long result = redis.execute(acquireScript, List.of(minuteKey(), dayKey()),
                String.valueOf(properties.limits().requestsPerMinute()),
                String.valueOf(properties.limits().requestsPerDay()),
                String.valueOf(MINUTE_TTL_MS), String.valueOf(DAY_TTL_MS));
        if (result == null || result == 0L) {
            return QuotaDecision.ACQUIRED;
        }
        if (result == 1L) {
            return QuotaDecision.MINUTE_EXCEEDED;
        }
        return QuotaDecision.DAY_EXCEEDED;
    }

    /** 오늘(태평양 시간 날짜) 호출한 수 */
    public long usedToday() {
        String value = redis.opsForValue().get(dayKey());
        if (value == null) {
            return 0;
        }
        return Long.parseLong(value);
    }

    /** 같은 작품이 쿨다운 중인지 */
    public boolean isCoolingDown(Long titleId) {
        return Boolean.TRUE.equals(redis.hasKey(COOLDOWN_PREFIX + titleId));
    }

    /** 같은 작품의 쿨다운을 시작한다 */
    public void startCooldown(Long titleId) {
        redis.opsForValue().set(COOLDOWN_PREFIX + titleId, "1",
                Duration.ofSeconds(properties.limits().titleCooldownSeconds()));
    }

    private String minuteKey() {
        return MINUTE_PREFIX + (clock.millis() / 60_000L);
    }

    private String dayKey() {
        return DAY_PREFIX + LocalDate.now(clock.withZone(PACIFIC));
    }
}
