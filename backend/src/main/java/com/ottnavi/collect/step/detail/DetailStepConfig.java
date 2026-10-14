package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitProviders;
import com.ottnavi.collect.job.CollectProperties;
import com.ottnavi.infra.tmdb.TmdbClient;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
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
 * Reader는 JpaPagingItemReader이고 읽은 위치를 저장해 재시작하면 이어서 읽는다. 정렬은 id 순이고
 * Step이 갱신하는 컬럼(fetched_at 등)으로 거르지 않는다(거르면 페이지가 밀려 건너뛴다, TECH 3절).
 */
@Configuration
@EnableConfigurationProperties(CollectProperties.class)
public class DetailStepConfig {

    public static final String MOVIE_DETAIL_STEP = "movieDetailStep"; // 실행 이력의 처리 건수 집계가 참조한다
    public static final String TV_DETAIL_STEP = "tvDetailStep";

    private static final int CHUNK_SIZE = 10; // 작품·단위 하나가 TMDB 호출 여러 번이라 chunk를 작게 둔다(TECH T-2)

    private static final String TITLE_QUERY = "select new com.ottnavi.collect.step.detail.DetailTarget("
            + "t.id, t.mediaType, t.tmdbId) from Title t where t.mediaType = :mediaType order by t.id";
    private static final String UNIT_QUERY = "select new com.ottnavi.collect.step.detail.AvailabilityTarget("
            + "u.id, u.unitType, t.tmdbId, u.seasonNumber) from WatchUnit u join u.title t order by u.id";

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
                .build();
    }

    @Bean
    public Step availabilityStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 JpaPagingItemReader<AvailabilityTarget> watchUnitReader,
                                 AvailabilityProcessor processor, AvailabilityWriter writer) {
        return new StepBuilder("availabilityStep", jobRepository)
                .<AvailabilityTarget, UnitProviders>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(watchUnitReader)
                .processor(processor)
                .writer(writer)
                .listener(writer) // beforeStep에서 jobExecutionId를 받는다
                .build();
    }

    private static JpaPagingItemReader<DetailTarget> titleReader(String name, EntityManagerFactory entityManagerFactory,
                                                                 MediaType mediaType) {
        return new JpaPagingItemReaderBuilder<DetailTarget>()
                .name(name) // 위치 저장 키의 접두사라 반드시 필요하다
                .entityManagerFactory(entityManagerFactory)
                .queryString(TITLE_QUERY)
                .parameterValues(Map.of("mediaType", mediaType))
                .pageSize(CHUNK_SIZE) // chunk와 같게 둬서 재시작 위치가 쪽 경계에 맞는다
                .saveState(true)
                .build();
    }
}
