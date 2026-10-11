package com.ottnavi.infra.tmdb;

import java.time.Duration;

/** 단위 테스트에서 TmdbProperties를 손으로 만들 때 쓰는 값 모음 */
final class TmdbTestFixtures {

    private TmdbTestFixtures() {
    }

    static TmdbProperties.RateLimit rateLimit() {
        return new TmdbProperties.RateLimit(10, 25, Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    /** 테스트가 느려지지 않도록 대기를 1~4ms로 줄인 재시도 설정 (총 3회, IO 오류는 총 2회) */
    static TmdbProperties.Retry retry() {
        return new TmdbProperties.Retry(3, 2, Duration.ofMillis(1), Duration.ofMillis(4), Duration.ofSeconds(5));
    }

    static TmdbProperties props(String token) {
        return new TmdbProperties("http://localhost/3", token, rateLimit(), retry());
    }
}
