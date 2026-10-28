package com.ottnavi.global.config;

import com.ottnavi.catalog.service.model.ExclusiveTitleList;
import com.ottnavi.catalog.service.model.GenreInfo;
import com.ottnavi.catalog.service.model.NewlyAvailableResult;
import com.ottnavi.catalog.service.model.TitleDetailView;
import com.ottnavi.catalog.service.model.TitleSearchResult;
import com.ottnavi.home.service.model.PopularityRankingView;
import com.ottnavi.home.service.model.WishlistedTitleListView;
import com.ottnavi.plan.service.model.PlanCalcResults;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer; // Boot 4 위치(3.2 참고)
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer; // Jackson 3용(tools.jackson)
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer; // 값 타입을 지정하는 Jackson 3 직렬화기
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.List;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/** Redis 캐시 설정: 값은 JSON(Jackson 3)으로 저장하고 캐시마다 TTL을 둔다. */
@Configuration
@EnableCaching
@EnableConfigurationProperties({HomeCacheProperties.class, ExclusiveCacheProperties.class})
public class RedisConfig {

    // 캐시 이름 (@Cacheable(cacheNames = ...)에서 이 상수를 쓴다)
    public static final String TITLE_DETAIL = "titleDetail";         // 작품 상세(Task 050·051)
    public static final String PLAN_CALC = "planCalc";               // 계산 결과, 키는 input_hash(TECH 1절)
    // 서비스별 독점 목록, 키는 서비스 ID(Task 078). 정렬 규칙(최근 6개월 개봉작 먼저)을 바꾼 배포 직후 옛 순서의 캐시가 TTL(24h) 동안 남지 않도록
    // 이름을 exclusiveTitles에서 V2로 바꿨다. 옛 키는 TTL로 저절로 사라지고, 첫 조회가 미스가 되어 새 순서로 채워진다
    public static final String EXCLUSIVE_TITLES = "exclusiveTitlesV2";

    // 홈·탐색 공개 응답 캐시(Task 107). 모두 로그인 정보 없이 모두에게 같은 목록이다
    public static final String HOME_POPULARITY = "homePopularity";     // 인기 순위, 키는 기간:서비스:개수
    public static final String HOME_NEW_ARRIVALS = "homeNewArrivals";  // 새로 볼 수 있게 된 작품, 키는 서비스:개수
    public static final String HOME_WISHLISTED = "homeWishlisted";     // 찜 많은 작품, 키는 개수
    public static final String HOME_GENRES = "homeGenres";             // 장르 목록, 키는 고정
    public static final String HOME_GENRE_TITLES = "homeGenreTitles";  // 장르별 작품, 키는 장르:페이지:크기
    public static final List<String> HOME_CACHES = List.of(HOME_POPULARITY, HOME_NEW_ARRIVALS, HOME_WISHLISTED,
            HOME_GENRES, HOME_GENRE_TITLES); // 한꺼번에 비울 때 쓰는 전체 목록

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
    public RedisCacheManagerBuilderCustomizer cacheTtlCustomizer(RedisCacheConfiguration base,
            HomeCacheProperties homeCache, ExclusiveCacheProperties exclusiveCache) {
        // 작품 상세는 값 타입(TitleDetailView) 전용 직렬화기를 쓴다. 다른 캐시는 기본(범용) 직렬화기를 그대로 둔다
        RedisCacheConfiguration titleDetailConfig = base.entryTtl(Duration.ofHours(24))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(TitleDetailView.class)));
        // 계산 결과는 세 유형 결과 묶음(PlanCalcResults) 전용 직렬화기를 쓴다
        RedisCacheConfiguration planCalcConfig = base.entryTtl(Duration.ofHours(24))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(PlanCalcResults.class)));
        // 독점 목록은 서비스 한 곳의 목록(ExclusiveTitleList) 전용 직렬화기를 쓴다. TTL은 app.exclusive.cache.ttl이다(Task 078)
        RedisCacheConfiguration exclusiveConfig = base.entryTtl(exclusiveCache.ttl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(ExclusiveTitleList.class)));
        // 홈·탐색 캐시: 캐시마다 값 타입과 TTL(app.home.cache.*)을 따로 둔다. 장르 목록은 List<GenreInfo>라 JavaType으로 지정한다
        RedisCacheConfiguration popularityConfig = base.entryTtl(homeCache.popularityTtl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(PopularityRankingView.class)));
        RedisCacheConfiguration newArrivalsConfig = base.entryTtl(homeCache.newArrivalsTtl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(NewlyAvailableResult.class)));
        RedisCacheConfiguration wishlistedConfig = base.entryTtl(homeCache.wishlistedTtl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(WishlistedTitleListView.class)));
        JavaType genreListType = JsonMapper.builder().build().getTypeFactory()
                .constructCollectionType(List.class, GenreInfo.class);
        RedisCacheConfiguration genresConfig = base.entryTtl(homeCache.genresTtl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(genreListType)));
        RedisCacheConfiguration genreTitlesConfig = base.entryTtl(homeCache.genreTitlesTtl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(TitleSearchResult.class)));
        return builder -> builder
                .withCacheConfiguration(TITLE_DETAIL, titleDetailConfig)
                .withCacheConfiguration(PLAN_CALC, planCalcConfig)
                .withCacheConfiguration(EXCLUSIVE_TITLES, exclusiveConfig)
                .withCacheConfiguration(HOME_POPULARITY, popularityConfig)
                .withCacheConfiguration(HOME_NEW_ARRIVALS, newArrivalsConfig)
                .withCacheConfiguration(HOME_WISHLISTED, wishlistedConfig)
                .withCacheConfiguration(HOME_GENRES, genresConfig)
                .withCacheConfiguration(HOME_GENRE_TITLES, genreTitlesConfig);
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
