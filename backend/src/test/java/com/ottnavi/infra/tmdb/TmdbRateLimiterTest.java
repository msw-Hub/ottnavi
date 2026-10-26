package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TmdbRateLimiterTest {

    private static Bucket bucket(long capacity, long refillTokens, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillGreedy(refillTokens, refillPeriod))
                .build();
    }

    // T16-a: 결정적 케이스. 토큰이 없고 기다리지 않으면 즉시 포기
    @Test
    void t16_토큰이_없고_max_wait가_0이면_즉시_TmdbUnavailableException이다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofMinutes(1)), Duration.ZERO);

        limiter.acquire();   // 통에 있던 토큰 1개를 쓴다

        assertThatThrownBy(limiter::acquire).isInstanceOf(TmdbUnavailableException.class);
    }

    // T16-b: 타이밍 케이스. 토큰이 200ms마다 1개씩 채워지면 3회 연속 호출에 약 400ms가 걸린다 (CI 흔들림에 대비해 300ms 이상으로 검사)
    @Test
    void t16_토큰이_채워질_때까지_기다린_뒤_통과한다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofMillis(200)), Duration.ofSeconds(2));

        long start = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
    }

    // T16-c: 필요한 대기가 max-wait보다 길면 기다리지 않고 포기한다
    @Test
    void t16_필요한_대기가_max_wait보다_길면_포기한다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofMinutes(1)), Duration.ofMillis(50));
        limiter.acquire();

        long start = System.nanoTime();
        assertThatThrownBy(limiter::acquire).isInstanceOf(TmdbUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(500);   // 1분을 기다리지 않는다
    }

    // T16-d: 기다리는 도중 인터럽트되면 인터럽트 상태를 복원하고 포기한다
    @Test
    void t16_대기_중_인터럽트되면_상태를_복원하고_포기한다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofSeconds(1)), Duration.ofSeconds(5));
        limiter.acquire();   // 토큰 소진. 다음 토큰까지 약 1초라 max-wait(5초) 안이므로 실제로 대기에 들어간다

        Thread.currentThread().interrupt();
        assertThatThrownBy(limiter::acquire).isInstanceOf(TmdbUnavailableException.class);

        assertThat(Thread.interrupted()).isTrue();   // 상태를 읽으면서 지운다. 지우지 않으면 다음 테스트가 오염된다
    }
}
