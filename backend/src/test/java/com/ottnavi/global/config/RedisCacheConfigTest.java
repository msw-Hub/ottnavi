package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;   // 변경: 추가한 import
import org.springframework.boot.test.context.TestConfiguration;                           // 변경: 추가한 import
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;                                       // 변경: 추가한 import
import org.springframework.data.redis.cache.RedisCacheConfiguration;                      // 변경: 추가한 import
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;              // 변경: 추가한 import
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair; // 변경: 추가한 import
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RedisConfig 검증: 캐시 저장·조회, JSON 직렬화, 키 접두사, TTL, null 미저장(Task 024 V-B).
 *
 * <p>Spring Data Redis 4는 연결 팩토리가 Lettuce(ReactiveRedisConnectionFactory 구현)면 기본적으로 Cache.put·clear가 비동기다
 * (4.x 업그레이드 안내). RedisConfig에서 즉시 반영(immediateWrites)을 적용했으므로 put·clear는 메서드가 끝나면 Redis에 반영돼 있고,
 * 테스트는 별도 대기 없이 바로 Redis를 확인한다.
 */
class RedisCacheConfigTest extends IntegrationTestSupport {

    record SampleValue(Long id, String name, List<String> tags) {}   // 테스트용 값 타입

    /** 테스트 전용 캐시 설정: 이 캐시만 값 타입을 SampleValue로 지정한다(D1 = B, 캐시별 타입 지정). */
    @TestConfiguration   // 변경: 클래스 안에 중첩. 다른 테스트의 스캔에 섞이지 않는다(Task 023 경험)
    static class TestCacheConfig {

        static final String SAMPLE_CACHE = "sampleTest";   // 실제 캐시 이름(titleDetail 등)과 겹치지 않는 테스트 전용 이름

        @Bean
        RedisCacheManagerBuilderCustomizer sampleCacheCustomizer(RedisCacheConfiguration base) {
            return builder -> builder.withCacheConfiguration(SAMPLE_CACHE,
                    base.serializeValuesWith(SerializationPair.fromSerializer(
                            new JacksonJsonRedisSerializer<>(SampleValue.class))));
        }
    }

    private static final String SAMPLE_KEY = TestCacheConfig.SAMPLE_CACHE + "::1";   // 캐시 키 1L의 Redis 원본 키
    private static final long ONE_HOUR_SECONDS = 60 * 60;        // 기본 TTL(D4)
    private static final long ONE_DAY_SECONDS = 24 * 60 * 60;    // 실제 캐시 TTL(D4)

    // 테스트에서 쓰는 캐시 전부. 테스트가 끝나면 모두 비운다
    private static final List<String> USED_CACHES = List.of(
            TestCacheConfig.SAMPLE_CACHE,
            RedisConfig.TITLE_DETAIL,
            RedisConfig.PLAN_CALC,
            RedisConfig.EXCLUSIVE_TITLES);

    @Autowired CacheManager cacheManager;
    @Autowired StringRedisTemplate redis;   // 원본 값·키·TTL을 직접 읽는 용도

    @AfterEach
    void cleanUp() {   // 다른 테스트에 영향이 없게 사용한 키를 지운다
        // 즉시 반영(immediateWrites) 적용으로 clear()도 끝나면 바로 지워져 있으므로 invalidate() 우회 대신 clear()로 되돌림
        USED_CACHES.forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void 저장한_값을_같은_타입으로_조회한다() {
        Cache cache = cacheManager.getCache(TestCacheConfig.SAMPLE_CACHE);   // 변경: TITLE_DETAIL → SAMPLE_CACHE
        SampleValue value = new SampleValue(1L, "더 글로리", List.of("드라마"));

        cache.put(1L, value);

        assertThat(cache.get(1L, SampleValue.class)).isEqualTo(value);
    }

    @Test
    void 값은_JSON으로_저장된다() {
        Cache cache = cacheManager.getCache(TestCacheConfig.SAMPLE_CACHE);
        cache.put(1L, new SampleValue(1L, "더 글로리", List.of("드라마")));

        String raw = redis.opsForValue().get(SAMPLE_KEY);

        // JDK 직렬화였다면 0xACED로 시작하는 바이너리라 '{'로 시작하지 않는다
        assertThat(raw).startsWith("{").contains("\"name\"").contains("더 글로리");
    }

    @Test
    void 키는_캐시이름_접두사가_붙은_문자열이다() {
        Cache cache = cacheManager.getCache(TestCacheConfig.SAMPLE_CACHE);
        cache.put(1L, new SampleValue(1L, "더 글로리", List.of("드라마")));

        assertThat(redis.keys(TestCacheConfig.SAMPLE_CACHE + "::*")).containsExactly(SAMPLE_KEY);
    }

    @Test
    void 기본_TTL은_1시간_이하로_설정된다() {
        Cache cache = cacheManager.getCache(TestCacheConfig.SAMPLE_CACHE);
        cache.put(1L, new SampleValue(1L, "더 글로리", List.of("드라마")));

        Long ttl = redis.getExpire(SAMPLE_KEY);   // 초 단위. 만료 없음이면 -1, 키 없음이면 -2

        assertThat(ttl).isPositive().isLessThanOrEqualTo(ONE_HOUR_SECONDS);
    }

    @Test
    void 실제_캐시의_TTL은_1시간_초과_24시간_이하다() {
        // 이 캐시들은 기본 직렬화기라 값 타입을 검증하지 않고 put과 TTL만 본다
        for (String name : List.of(RedisConfig.TITLE_DETAIL, RedisConfig.PLAN_CALC, RedisConfig.EXCLUSIVE_TITLES)) {
            String key = name + "::ttl-check";
            cacheManager.getCache(name).put("ttl-check", "값");

            Long ttl = redis.getExpire(key);

            assertThat(ttl).as("%s TTL", name)
                    .isGreaterThan(ONE_HOUR_SECONDS)
                    .isLessThanOrEqualTo(ONE_DAY_SECONDS);
        }
    }

    @Test
    void null_값은_저장하지_않고_예외가_난다() {
        Cache cache = cacheManager.getCache(TestCacheConfig.SAMPLE_CACHE);

        // 실측: disableCachingNullValues면 RedisCache.put(key, null)은 조용히 무시하지 않고 IllegalArgumentException을 던진다.
        // 예외는 Redis에 보내기 전(호출 스레드)에 나므로 키가 생기지 않는다.
        // @Cacheable을 쓸 때는 unless = "#result == null"로 null 반환을 캐시하지 않게 해야 한다
        assertThatThrownBy(() -> cache.put(1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(redis.hasKey(SAMPLE_KEY)).isFalse();
    }

    @Test
    void 캐시_쓰기는_즉시_반영으로_설정된다() {
        // 비동기 여부는 공개 API로 노출되지 않아 내부 필드를 직접 읽는다(spring-data-redis 4.1.1 DefaultRedisCacheWriter 기준).
        // 라이브러리 업그레이드로 필드 이름이 바뀌면 이 테스트가 먼저 깨지므로 그때 확인 방법을 다시 정한다
        RedisCacheWriter writer = (RedisCacheWriter) ReflectionTestUtils.getField(cacheManager, "cacheWriter");

        // Lettuce(반응형 연결)라 비동기 쓰기가 가능한 환경임을 먼저 확인한다. 아니면 아래 검증이 의미가 없다
        assertThat(writer.supportsAsyncRetrieve()).isTrue();
        assertThat(ReflectionTestUtils.getField(writer, "asynchronousWrites")).isEqualTo(false);
    }

    @Test
    void put과_clear는_대기_없이_바로_반영된다() {
        Cache cache = cacheManager.getCache(TestCacheConfig.SAMPLE_CACHE);

        cache.put(1L, new SampleValue(1L, "더 글로리", List.of("드라마")));
        assertThat(redis.hasKey(SAMPLE_KEY)).isTrue();    // 대기 없이 바로 확인

        cache.clear();
        assertThat(redis.hasKey(SAMPLE_KEY)).isFalse();   // invalidate()가 아닌 clear()로도 바로 지워진다
    }

    // 즉시 반영(immediateWrites) 적용으로 불필요해져 주석 처리(호출부는 삭제). 비동기 쓰기로 되돌리면 다시 필요하다
    // /** 비동기 put이 Redis에 반영될 때까지 기다린다. */
    // private void waitUntilStored(String rawKey) {
    //     await().atMost(Duration.ofSeconds(2)).until(() -> redis.hasKey(rawKey));
    // }
}
