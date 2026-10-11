package com.ottnavi.infra.tmdb;

import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TMDB 연동 공통 빈: 설정 프로퍼티 등록, 로컬 버킷, 속도 제한기 */
@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbConfig {

    /**
     * TMDB 호출 한도용 로컬 버킷. 앱 전체(부모 컨텍스트)에 하나만 둔다.
     * TmdbFeignConfig는 Feign 하위 컨텍스트라 거기서 만들면 클라이언트가 늘 때 버킷도 늘어나기 때문이다.
     * 050의 단건 수집과 043 배치가 같은 버킷을 함께 쓴다. 빈 이름을 붙이는 이유: Task 049의 Redis 버킷 등 다른 Bucket 빈과 구분하려고.
     */
    @Bean("tmdbBucket")
    Bucket tmdbBucket(TmdbProperties props) {
        TmdbProperties.RateLimit limit = props.rateLimit();
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.capacity())                                // 통 크기
                        .refillGreedy(limit.refillTokens(), limit.refillPeriod())) // 시간에 비례해 조금씩 채운다
                .build();
    }

    /** 호출 직전에 토큰을 얻는 속도 제한기 */
    @Bean
    TmdbRateLimiter tmdbRateLimiter(@Qualifier("tmdbBucket") Bucket tmdbBucket, TmdbProperties props) {
        return new TmdbRateLimiter(tmdbBucket, props.rateLimit().maxWait());
    }
}
