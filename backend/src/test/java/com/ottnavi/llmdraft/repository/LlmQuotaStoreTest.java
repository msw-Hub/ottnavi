package com.ottnavi.llmdraft.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 호출 상한 카운터: 분당·하루 상한, 태평양 날짜 경계, 쿨다운 (실제 Redis, 시계는 직접 움직인다) */
class LlmQuotaStoreTest extends IntegrationTestSupport {

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void 카운터_키를_지운다() {
        deleteKeys("llm:quota:*");
        deleteKeys("llm:cooldown:*");
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private LlmQuotaStore storeWith(int perMinute, int perDay, MovableClock clock) {
        LlmDraftProperties properties = new LlmDraftProperties(
                new LlmDraftProperties.Limits(perMinute, perDay, 60), new LlmDraftProperties.Draft(Duration.ofDays(7), 500),
                new LlmDraftProperties.Bulk(100, Duration.ZERO, Duration.ZERO, 3, 2, 1),
            new LlmDraftProperties.Guard(0.6, 0.3, 2.0, 1000, 1, 0.8, 0.1, 0.4));
        return new LlmQuotaStore(redis, properties, clock);
    }

    @Test
    void 분당_상한까지는_통과하고_그다음은_MINUTE_EXCEEDED이다() {
        // given: 분당 2회
        MovableClock clock = new MovableClock(Instant.parse("2031-03-05T12:00:10Z"));
        LlmQuotaStore store = storeWith(2, 100, clock);

        // when
        QuotaDecision first = store.tryAcquire();
        QuotaDecision second = store.tryAcquire();
        QuotaDecision third = store.tryAcquire();

        // then
        assertThat(first).isEqualTo(QuotaDecision.ACQUIRED);
        assertThat(second).isEqualTo(QuotaDecision.ACQUIRED);
        assertThat(third).isEqualTo(QuotaDecision.MINUTE_EXCEEDED);
        assertThat(store.usedToday()).isEqualTo(2);   // 거절된 요청은 세지 않는다
    }

    @Test
    void 다음_1분이_되면_분당_카운터가_새로_시작한다() {
        // given
        MovableClock clock = new MovableClock(Instant.parse("2031-03-05T12:00:10Z"));
        LlmQuotaStore store = storeWith(1, 100, clock);
        store.tryAcquire();
        assertThat(store.tryAcquire()).isEqualTo(QuotaDecision.MINUTE_EXCEEDED);

        // when
        clock.set(Instant.parse("2031-03-05T12:01:10Z"));

        // then
        assertThat(store.tryAcquire()).isEqualTo(QuotaDecision.ACQUIRED);
    }

    @Test
    void 하루_상한을_넘으면_DAY_EXCEEDED이다() {
        // given: 하루 3회
        MovableClock clock = new MovableClock(Instant.parse("2031-03-05T12:00:00Z"));
        LlmQuotaStore store = storeWith(100, 3, clock);

        // when
        for (int i = 0; i < 3; i++) {
            clock.set(clock.instant().plusSeconds(60));   // 분당 상한과 섞이지 않게 분을 바꾼다
            assertThat(store.tryAcquire()).isEqualTo(QuotaDecision.ACQUIRED);
        }
        clock.set(clock.instant().plusSeconds(60));

        // then
        assertThat(store.tryAcquire()).isEqualTo(QuotaDecision.DAY_EXCEEDED);
        assertThat(store.usedToday()).isEqualTo(3);
    }

    @Test
    void 하루_기준은_한국_날짜가_아니라_태평양_날짜여서_태평양_자정에_리셋된다() {
        // given: 2031-03-05 07:59Z = 태평양 3월 4일 23:59(KST로는 이미 3월 5일 16:59). 하루 상한 2회를 다 쓴다
        MovableClock clock = new MovableClock(Instant.parse("2031-03-05T07:57:00Z"));
        LlmQuotaStore store = storeWith(100, 2, clock);
        store.tryAcquire();
        clock.set(Instant.parse("2031-03-05T07:58:00Z"));
        store.tryAcquire();
        clock.set(Instant.parse("2031-03-05T07:59:00Z"));
        assertThat(store.tryAcquire()).isEqualTo(QuotaDecision.DAY_EXCEEDED);

        // when: 태평양 자정(08:00Z)을 지난다
        clock.set(Instant.parse("2031-03-05T08:01:00Z"));

        // then: 새 날짜로 세므로 다시 통과하고 오늘 사용량은 1이다
        assertThat(store.tryAcquire()).isEqualTo(QuotaDecision.ACQUIRED);
        assertThat(store.usedToday()).isEqualTo(1);
    }

    @Test
    void 쿨다운을_시작한_작품만_쿨다운_중이다() {
        // given
        LlmQuotaStore store = storeWith(12, 400, new MovableClock(Instant.parse("2031-03-05T12:00:00Z")));

        // when
        store.startCooldown(7L);

        // then
        assertThat(store.isCoolingDown(7L)).isTrue();
        assertThat(store.isCoolingDown(8L)).isFalse();
    }

    /** 시험에서 시간을 직접 옮기는 시계. withZone은 현재 시각을 고정한 시계를 돌려준다 */
    private static class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
