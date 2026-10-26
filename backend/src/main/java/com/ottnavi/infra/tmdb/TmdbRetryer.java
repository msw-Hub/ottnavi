package com.ottnavi.infra.tmdb;

import feign.RetryableException;
import feign.Retryer;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

/**
 * TMDB 재시도 정책: 429·5xx는 총 maxAttempts회, 타임아웃·연결 오류는 총 ioMaxAttempts회. Retry-After가 있으면 우선하되 너무 길면 포기한다.
 * Retryer는 상태(attempt)를 가진다. Feign이 호출마다 clone()으로 새 인스턴스를 만들어 쓰므로 스레드 간 공유 문제는 없다.
 */
@Slf4j
public class TmdbRetryer implements Retryer {

    private static final double JITTER_RATIO = 0.2;   // 대기 시간의 ±20%

    private final TmdbProperties.Retry config;
    private int attempt;   // 지금까지 실패한 시도 횟수. 호출마다 clone()돼 0에서 시작한다

    public TmdbRetryer(TmdbProperties.Retry config) {
        this.config = config;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        // 1. 실패 횟수를 세고 한도에 닿으면 재시도를 포기한다
        attempt++;   // 방금 실패한 시도 번호(1부터)
        int limit = isIoError(e) ? config.ioMaxAttempts() : config.maxAttempts();
        if (attempt >= limit) {
            log.warn("TMDB 호출 재시도를 포기한다: 상태={}, 시도={}/{}", e.status(), attempt, limit);   // e.request()는 헤더(토큰)를 품고 있어 찍지 않는다
            throw e;
        }

        // 2. 대기 시간을 정한다(Retry-After가 있으면 그 값, 너무 길면 포기, 없으면 지수 백오프)
        long waitMillis;
        Long retryAfter = e.retryAfter();   // 서버가 알려 준 "다시 해도 되는 시각"(epoch ms). 없으면 null
        if (retryAfter != null) {
            waitMillis = Math.max(0, retryAfter - System.currentTimeMillis());
            if (waitMillis > config.maxRetryAfter().toMillis()) {
                log.warn("TMDB가 너무 오래 기다리라고 해서 재시도를 포기한다: 상태={}, 요구 대기={}ms", e.status(), waitMillis);
                throw e;
            }
        } else {
            waitMillis = backoffWithJitterMillis();
        }

        // 3. 대기한 뒤 호출자가 다시 시도하게 한다(인터럽트되면 포기한다)
        log.warn("TMDB 호출을 재시도한다: 상태={}, 시도={}/{}, 대기={}ms", e.status(), attempt, limit, waitMillis);
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();   // 인터럽트 상태를 복원한 뒤 포기한다
            throw e;
        }
    }

    /** 호출마다 새 인스턴스를 만든다(시도 횟수가 0에서 다시 시작) */
    @Override
    public Retryer clone() {
        return new TmdbRetryer(config);
    }

    /** 타임아웃·연결 오류: Feign이 IOException을 상태 -1의 RetryableException으로 감싼다(3.2절 3번) */
    private static boolean isIoError(RetryableException e) {
        return e.status() == -1;
    }

    /** initial × 2^(시도-1)을 max로 자른 뒤 ±20% 흔든다 */
    private long backoffWithJitterMillis() {
        long exponential = config.initialBackoff().toMillis() * (1L << (attempt - 1));
        long capped = Math.min(exponential, config.maxBackoff().toMillis());
        double jitter = 1 + ThreadLocalRandom.current().nextDouble(-JITTER_RATIO, JITTER_RATIO);
        return Math.max(0, Math.round(capped * jitter));
    }
}
