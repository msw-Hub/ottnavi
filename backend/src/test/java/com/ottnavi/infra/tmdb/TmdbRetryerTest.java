package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TmdbRetryerTest {

    private static RetryableException retryable(int status, Long retryAfter) {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://localhost/3/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(status, "테스트", Request.HttpMethod.GET, retryAfter, request);
    }

    private Retryer newRetryer() {
        return new TmdbRetryer(TmdbTestFixtures.retry());   // 총 3회, IO는 총 2회, 대기 1~4ms
    }

    @Test
    void t23_429는_총_3번째_실패에서_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, null);

        assertThatCode(() -> retryer.continueOrPropagate(failure)).doesNotThrowAnyException();   // 1번째 실패 → 재시도
        assertThatCode(() -> retryer.continueOrPropagate(failure)).doesNotThrowAnyException();   // 2번째 실패 → 재시도
        assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);        // 3번째 실패 → 포기
    }

    @Test
    void t23_IO_오류는_총_2번째_실패에서_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException ioFailure = retryable(-1, null);

        assertThatCode(() -> retryer.continueOrPropagate(ioFailure)).doesNotThrowAnyException();
        assertThatThrownBy(() -> retryer.continueOrPropagate(ioFailure)).isSameAs(ioFailure);
    }

    @Test
    void t23_Retry_After가_max_retry_after보다_길면_기다리지_않고_바로_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, System.currentTimeMillis() + 60_000L);

        long start = System.nanoTime();
        assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);

        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(500);
    }

    @Test
    void t23_Retry_After가_짧으면_그만큼_기다린_뒤_재시도한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, System.currentTimeMillis() + 200L);

        long start = System.nanoTime();
        assertThatCode(() -> retryer.continueOrPropagate(failure)).doesNotThrowAnyException();

        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isGreaterThanOrEqualTo(150);
    }

    @Test
    void t23_clone하면_시도_횟수가_처음부터_다시_시작한다() {
        Retryer original = newRetryer();
        RetryableException failure = retryable(503, null);
        original.continueOrPropagate(failure);
        original.continueOrPropagate(failure);
        assertThatThrownBy(() -> original.continueOrPropagate(failure)).isSameAs(failure);   // 원본은 소진됨

        Retryer copy = original.clone();

        assertThatCode(() -> copy.continueOrPropagate(failure)).doesNotThrowAnyException();   // 복제본은 새로 시작
    }

    @Test
    void t23_대기_중_인터럽트되면_상태를_복원하고_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, System.currentTimeMillis() + 3_000L);   // 3초 대기(5초 상한 안)에 들어가게

        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);

        assertThat(Thread.interrupted()).isTrue();   // 상태를 읽으면서 지운다
    }
}
