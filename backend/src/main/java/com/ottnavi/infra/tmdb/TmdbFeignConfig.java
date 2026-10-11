package com.ottnavi.infra.tmdb;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * TMDB Feign 클라이언트 전용 설정.
 * @Configuration을 붙이지 않는다: 붙이면 컴포넌트 스캔에 잡혀 모든 Feign 클라이언트에 적용된다(6절 함정 3).
 * TmdbApi의 @FeignClient(configuration = TmdbFeignConfig.class)로만 연결한다.
 * 타임아웃·로그 수준은 여기가 아니라 application.yml(spring.cloud.openfeign.client.config.tmdb)에 있다(속성이 빈보다 우선한다, TASK024 방침).
 */
@Slf4j
public class TmdbFeignConfig {

    /** 모든 요청에 읽기 액세스 토큰을 Bearer로 붙인다. 토큰이 비어 있으면 헤더를 붙이지 않는다(호출은 TMDB가 401로 거절한다) */
    @Bean
    RequestInterceptor tmdbAuthInterceptor(TmdbProperties props) {
        String token = props.apiToken();
        if (token == null || token.isBlank()) {
            log.warn("TMDB 토큰이 비어 있다. TMDB_API_KEY 환경 변수를 설정하기 전까지 TMDB 호출은 401로 실패한다");
            return template -> { };
        }
        // 인터셉터는 재시도마다 다시 실행되지만 같은 값을 다시 넣어도 헤더가 중복되지 않는다(Feign이 값을 LinkedHashSet으로 보관)
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /**
     * 요청을 보내기 직전에 로컬 버킷에서 토큰 1개를 얻는다.
     * 인터셉터는 재시도마다 다시 실행되므로(3.2절 5번) 재시도도 한도에 포함된다. 재시도도 TMDB에 가는 실제 요청이기 때문이다(6절 함정 6).
     */
    @Bean
    RequestInterceptor tmdbRateLimitInterceptor(TmdbRateLimiter rateLimiter) {
        return template -> rateLimiter.acquire();
    }

    /** 429·5xx는 재시도 가능으로, 그 밖의 4xx는 재시도 불가로 나눈다 */
    @Bean
    ErrorDecoder tmdbErrorDecoder() {
        return new TmdbErrorDecoder();
    }

    /** 재시도 정책. Feign 기본 Retryer.NEVER_RETRY를 덮어쓴다. Feign이 호출마다 clone()해서 쓴다 */
    @Bean
    Retryer tmdbRetryer(TmdbProperties props) {
        return new TmdbRetryer(props.retry());
    }
}
