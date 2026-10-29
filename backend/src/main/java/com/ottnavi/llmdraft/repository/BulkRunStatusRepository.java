package com.ottnavi.llmdraft.repository;

import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.llmdraft.service.model.BulkRunStatus;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 최근 일괄 번역의 진행 상황 1건을 Redis(llm:bulk:latest, JSON, TTL = 초안 보관 기간)에 둔다 */
@Slf4j
@Component
public class BulkRunStatusRepository {

    private static final String KEY = "llm:bulk:latest";

    private final StringRedisTemplate redis;
    private final LlmDraftProperties properties;
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)   // 나중에 더한 숫자 필드(attempts, warningCount)가 없는 옛 JSON을 0으로 읽는다(Jackson 3은 기본이 오류)
            .build();

    public BulkRunStatusRepository(StringRedisTemplate redis, LlmDraftProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 진행 상황을 덮어쓴다 */
    public void save(BulkRunStatus status) {
        redis.opsForValue().set(KEY, mapper.writeValueAsString(status), properties.draft().ttl());
    }

    /** 최근 일괄의 진행 상황. 한 번도 돌린 적 없거나 사라졌으면 비어 있다 */
    public Optional<BulkRunStatus> find() {
        String json = redis.opsForValue().get(KEY);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, BulkRunStatus.class));
        } catch (JacksonException e) {
            log.warn("일괄 번역 상태 JSON을 읽지 못해 없는 것으로 본다: error={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
