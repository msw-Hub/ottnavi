package com.ottnavi.infra.tmdb;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import lombok.RequiredArgsConstructor;

/**
 * TMDB 호출 속도를 서버 한 대(JVM) 안에서 제한하는 로컬 버킷.
 * 버킷은 "토큰이 든 통"이다. 호출 한 번에 토큰 한 개를 쓰고 통은 정해진 속도로 다시 채워진다.
 * 이 클래스는 TMDB 한도(약 초당 40회)를 서버 전체가 넘지 않게 하는 안전망이고, 익명 방문자별 요청 제한(Task 049)과는 다른 장치다(2절 3번).
 */
@RequiredArgsConstructor
public class TmdbRateLimiter {

    private final Bucket bucket;      // 토큰 통(JVM 메모리). 빈 이름 tmdbBucket
    private final Duration maxWait;   // 토큰이 없을 때 최대 기다리는 시간

    /** 토큰 1개를 얻을 때까지 최대 maxWait 기다린다. 못 얻으면 TmdbUnavailableException으로 포기한다 */
    public void acquire() {
        try {
            // Bucket4j는 기다리는 tryConsume에 0을 허용하지 않아서, 0 이하면 기다리지 않는 tryConsume(1)을 쓴다
            boolean acquired = (maxWait.isZero() || maxWait.isNegative())
                    ? bucket.tryConsume(1)
                    : bucket.asBlocking().tryConsume(1, maxWait);
            if (!acquired) {
                throw new TmdbUnavailableException("TMDB 호출 한도 대기 시간을 넘겼다");   // 로컬 한도 포기
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 인터럽트 상태를 복원한 뒤 포기한다
            throw new TmdbUnavailableException("TMDB 호출 대기 중 중단되었다", e);
        }
    }
}
