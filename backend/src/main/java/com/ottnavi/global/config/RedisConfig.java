package com.ottnavi.global.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer; // Boot 4 위치(3.2 참고)
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer; // Jackson 3용(tools.jackson)
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/** Redis 캐시 설정: 값은 JSON(Jackson 3)으로 저장하고 캐시마다 TTL을 둔다. */
@Configuration
@EnableCaching
public class RedisConfig {

    // 캐시 이름 (@Cacheable(cacheNames = ...)에서 이 상수를 쓴다)
    public static final String TITLE_DETAIL = "titleDetail";         // 작품 상세(Task 050·051)
    public static final String PLAN_CALC = "planCalc";               // 계산 결과, 키는 input_hash(TECH 1절)
    public static final String EXCLUSIVE_TITLES = "exclusiveTitles"; // 독점 목록(2단계)

    /** 모든 캐시의 기본 설정: 문자열 키, JSON 값, null 미저장, 기본 TTL. */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))   // 기본 TTL (D4 추천안)
                .disableCachingNullValues()      // 없는 값을 캐시하면 수집 직후에도 계속 "없음"이 나온다
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer()));
    }

    /** 캐시별 TTL. 기본 설정을 바탕으로 TTL만 바꾼다. */
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheTtlCustomizer(RedisCacheConfiguration base) {
        return builder -> builder
                .withCacheConfiguration(TITLE_DETAIL, base.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration(PLAN_CALC, base.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration(EXCLUSIVE_TITLES, base.entryTtl(Duration.ofHours(24)));
    }

    /** 캐시 쓰기(put·evict·clear)를 즉시 반영으로 바꾼다. */
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheWriterCustomizer(RedisConnectionFactory connectionFactory) {
        // Lettuce에서는 기본 writer의 쓰기가 비동기라 수집 직후 clear()해도 잠시 옛 값이 읽힐 수 있다.
        // 캐시 규모가 작아 블로킹 비용이 작으므로 일관성을 택한다. 잠금 없음·keys 배치 전략은 기본 writer와 같다
        // 통계(spring.cache.redis.enable-statistics)는 builder.build()가 이 writer에 덧씌우므로 그대로 동작한다
        return builder -> builder.cacheWriter(
                RedisCacheWriter.create(connectionFactory, configurer -> configurer.immediateWrites()));
    }

    private RedisSerializer<Object> valueSerializer() {
        // D1 = B: 타입 정보를 넣지 않는 범용 직렬화기를 기본으로 둔다.
        // 캐시별 값 타입은 해당 DTO가 생기는 Task에서 JacksonJsonRedisSerializer<T>로 withCacheConfiguration에 따로 지정한다
        return GenericJacksonJsonRedisSerializer.builder().build();
    }
}
