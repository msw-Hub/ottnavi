package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitProviders;
import com.ottnavi.collect.job.CollectProperties;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.infra.tmdb.TmdbClient;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 상세 수집 Step 조립(Task 044): 영화 상세 → 드라마 상세·시즌 → 제공 상태 판정.
 * 읽는 작품 범위는 Job 파라미터 tier의 refresh_tier 조건으로 좁혀진다(Task 077, TierReaderParameterListener).
 * Reader는 JpaPagingItemReader이고 읽은 위치를 저장해 재시작하면 이어서 읽는다. 정렬은 id 순이고
 * Step이 갱신하는 컬럼(fetched_at 등)으로 거르지 않는다(거르면 페이지가 밀려 건너뛴다, TECH 3절).
 */
@Configuration
@EnableConfigurationProperties(CollectProperties.class)
public class DetailStepConfig {

    public static final String MOVIE_DETAIL_STEP = "movieDetailStep"; // 실행 이력의 처리 건수 집계가 참조한다
    public static final String TV_DETAIL_STEP = "tvDetailStep";
    public static final String AVAILABILITY_STEP = "availabilityStep";
    public static final String TV_AVAILABILITY_STEP = "tvAvailabilityStep"; // scope=AVAILABILITY_TV일 때만 쓰는 시즌 전용 판정 Step

    private static final Map<String, Object> SEASON_FIXED_PARAMETERS = Map.of("unitType", UnitType.SEASON);
    private static final int CHUNK_SIZE = 10; // 작품·단위 하나가 TMDB 호출 여러 번이라 chunk를 작게 둔다(TECH T-2)

    private static final String TITLE_QUERY = "select new com.ottnavi.collect.step.detail.DetailTarget("
            + "t.id, t.mediaType, t.tmdbId) from Title t where t.mediaType = :mediaType "
            + "and t.refreshTier in :refreshTiers order by t.id";
    private static final String UNIT_QUERY = "select new com.ottnavi.collect.step.detail.AvailabilityTarget("
            + "u.id, u.unitType, t.tmdbId, u.seasonNumber) from WatchUnit u join u.title t "
            + "where t.refreshTier in :refreshTiers order by u.id";
    private static final String SEASON_UNIT_QUERY = "select new com.ottnavi.collect.step.detail.AvailabilityTarget("
            + "u.id, u.unitType, t.tmdbId, u.seasonNumber) from WatchUnit u join u.title t "
            + "where u.unitType = :unitType and t.refreshTier in :refreshTiers order by u.id";

    // :refreshTiers(대상 작품의 refresh_tier 목록)는 Step 시작 때 TierReaderParameterListener가 tier로 다시 채운다.
    // 아래 Reader 빌드 값은 리스너 없이 쓸 때를 위한 기본값(전체)이다

    @Bean
    public RuntimeDefaults runtimeDefaults(CollectProperties properties) {
        return properties.runtimeDefaults();
    }

    // Reader: 별도 빈으로 만들어야 Spring이 afterPropertiesSet을 불러 설정을 검사한다

    @Bean
    public JpaPagingItemReader<DetailTarget> movieTitleReader(EntityManagerFactory entityManagerFactory) {
        return titleReader("movieTitleReader", entityManagerFactory, MediaType.MOVIE);
    }

    @Bean
    public JpaPagingItemReader<DetailTarget> tvTitleReader(EntityManagerFactory entityManagerFactory) {
        return titleReader("tvTitleReader", entityManagerFactory, MediaType.TV);
    }

    @Bean
    public JpaPagingItemReader<AvailabilityTarget> watchUnitReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<AvailabilityTarget>()
                .name("watchUnitReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(UNIT_QUERY)
                .parameterValues(allTiersParameters(Map.of()))
                .pageSize(CHUNK_SIZE)
                .saveState(true)
                .build();
    }

    /** 드라마 시즌만 읽는 Reader. 위치 저장 키(name)를 전체 판정 Reader와 달리해 서로 섞이지 않게 한다 */
    @Bean
    public JpaPagingItemReader<AvailabilityTarget> seasonUnitReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<AvailabilityTarget>()
                .name("seasonUnitReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(SEASON_UNIT_QUERY)
                .parameterValues(allTiersParameters(SEASON_FIXED_PARAMETERS))
                .pageSize(CHUNK_SIZE)
                .saveState(true)
                .build();
    }

    // Step

    @Bean
    public Step movieDetailStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                JpaPagingItemReader<DetailTarget> movieTitleReader, TmdbClient tmdbClient,
                                RuntimeDefaults runtimeDefaults, DetailWriter writer) {
        return new StepBuilder(MOVIE_DETAIL_STEP, jobRepository)
                .<DetailTarget, TitleDetail>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager) // 지정하지 않으면 Batch 6 기본값이 트랜잭션 없는 매니저다
                .reader(movieTitleReader)
                .processor(new MovieDetailProcessor(tmdbClient, runtimeDefaults))
                .writer(writer)
                .listener(new TierReaderParameterListener(movieTitleReader, Map.of("mediaType", MediaType.MOVIE))) // tier로 읽을 작품 범위를 정한다
                .build();
    }

    @Bean
    public Step tvDetailStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             JpaPagingItemReader<DetailTarget> tvTitleReader, TmdbClient tmdbClient,
                             RuntimeDefaults runtimeDefaults, MeterRegistry meterRegistry, DetailWriter writer) {
        TvDetailProcessor processor = new TvDetailProcessor(tmdbClient, runtimeDefaults, meterRegistry);
        return new StepBuilder(TV_DETAIL_STEP, jobRepository)
                .<DetailTarget, TitleDetail>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(tvTitleReader)
                .processor(processor)
                .writer(writer)
                .listener(processor) // beforeStep에서 기준일(targetDate)을 받는다
                .listener(new TierReaderParameterListener(tvTitleReader, Map.of("mediaType", MediaType.TV)))
                .build();
    }

    @Bean
    public Step availabilityStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 JpaPagingItemReader<AvailabilityTarget> watchUnitReader,
                                 AvailabilityProcessor processor, AvailabilityWriter writer) {
        return buildAvailabilityStep(AVAILABILITY_STEP, jobRepository, transactionManager, watchUnitReader,
                Map.of(), processor, writer);
    }

    /** scope=AVAILABILITY_TV 전용: 판정 Step과 같은 Processor·Writer로 드라마 시즌 단위만 다시 판정한다 */
    @Bean
    public Step tvAvailabilityStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   JpaPagingItemReader<AvailabilityTarget> seasonUnitReader,
                                   AvailabilityProcessor processor, AvailabilityWriter writer) {
        return buildAvailabilityStep(TV_AVAILABILITY_STEP, jobRepository, transactionManager, seasonUnitReader,
                SEASON_FIXED_PARAMETERS, processor, writer);
    }

    /** 판정 Step 조립: 전체 판정과 시즌 전용 판정이 Reader와 이름만 다르고 나머지는 같다 */
    private static Step buildAvailabilityStep(String stepName, JobRepository jobRepository,
                                              PlatformTransactionManager transactionManager,
                                              JpaPagingItemReader<AvailabilityTarget> reader,
                                              Map<String, Object> readerFixedParameters,
                                              AvailabilityProcessor processor, AvailabilityWriter writer) {
        return new StepBuilder(stepName, jobRepository)
                .<AvailabilityTarget, UnitProviders>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(writer) // beforeStep에서 jobExecutionId를 받는다
                .listener(processor) // beforeStep에서 작품 단위 임시 저장을 비우고 afterStep에서 대체 판정 횟수를 남긴다
                .listener(new TierReaderParameterListener(reader, readerFixedParameters)) // tier로 읽을 작품 범위를 정한다
                .build();
    }

    /** 고정 파라미터에 "모든 refresh_tier"를 더한 기본 파라미터 맵을 만든다 */
    private static Map<String, Object> allTiersParameters(Map<String, Object> fixedParameters) {
        Map<String, Object> parameters = new HashMap<>(fixedParameters);
        parameters.put(TierReaderParameterListener.REFRESH_TIERS, CollectTier.ALL.targetRefreshTiers());
        return parameters;
    }

    private static JpaPagingItemReader<DetailTarget> titleReader(String name, EntityManagerFactory entityManagerFactory,
                                                                 MediaType mediaType) {
        return new JpaPagingItemReaderBuilder<DetailTarget>()
                .name(name) // 위치 저장 키의 접두사라 반드시 필요하다
                .entityManagerFactory(entityManagerFactory)
                .queryString(TITLE_QUERY)
                .parameterValues(allTiersParameters(Map.of("mediaType", mediaType)))
                .pageSize(CHUNK_SIZE) // chunk와 같게 둬서 재시작 위치가 쪽 경계에 맞는다
                .saveState(true)
                .build();
    }
}
