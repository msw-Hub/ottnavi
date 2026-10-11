package com.ottnavi.infra.tmdb;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** TMDB 연동 설정(app.tmdb.*). 값은 application.yml에 두고 토큰만 환경 변수(TMDB_API_KEY)로 받는다 */
@ConfigurationProperties(prefix = "app.tmdb")
public record TmdbProperties(
        String baseUrl,        // TMDB API 주소. 끝의 /3까지 포함한다 (예: https://api.themoviedb.org/3)
        String apiToken,       // 읽기 액세스 토큰(Bearer). 기본값이 빈 문자열이라 토큰이 없어도 기동된다
        RateLimit rateLimit,   // 로컬 속도 제한(Bucket4j)
        Retry retry) {         // 재시도

    /** 로컬 버킷 설정. 한도 계산: capacity + 1초 동안 채워지는 토큰이 TMDB 한도(약 40)보다 작아야 한다 (10 + 25 = 35) */
    public record RateLimit(
            int capacity,           // 통 크기: 순간에 몰아 쓸 수 있는 최대 호출 수
            int refillTokens,       // refillPeriod마다 채워지는 토큰 수
            Duration refillPeriod,  // 채우는 주기
            Duration maxWait) {     // 토큰이 없을 때 최대 기다리는 시간
    }

    /** 재시도 설정 */
    public record Retry(
            int maxAttempts,          // 429·5xx의 총 시도 횟수(첫 시도 포함)
            int ioMaxAttempts,        // 타임아웃·연결 오류의 총 시도 횟수
            Duration initialBackoff,  // 첫 재시도 전 대기. 이후 두 배씩
            Duration maxBackoff,      // 지수 대기의 상한
            Duration maxRetryAfter) { // 서버가 이보다 오래 기다리라고 하면 기다리지 않고 포기
    }

    /** 토큰이 로그에 찍히지 않게 가린다. record 기본 toString()은 모든 필드를 출력한다 */
    @Override
    public String toString() {
        return "TmdbProperties[baseUrl=" + baseUrl + ", apiToken=***, rateLimit=" + rateLimit + ", retry=" + retry + "]";
    }
}
