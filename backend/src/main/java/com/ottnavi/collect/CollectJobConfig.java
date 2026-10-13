package com.ottnavi.collect;

import com.ottnavi.catalog.application.CandidateTitle;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.TitleDetailRules;
import com.ottnavi.collect.CandidateReader.CandidatePage;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.provider.application.ProviderCollectService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** 수집 Job 조립: 제공처 목록 → 영화 후보 → 드라마 후보 (Task 043). 상세·판정 Step은 Task 044가 이어 붙인다 */
@Configuration
public class CollectJobConfig {

    public static final String JOB_NAME = "collectJob";
    private static final int CHUNK_SIZE = 20; // discover 한 쪽이 20건이라 한 쪽을 한 번에 커밋한다
    private static final int MAX_TITLE_LENGTH = 300; // title.title 컬럼 길이

    @Bean
    public Job collectJob(JobRepository jobRepository, Step providerListStep, Step movieCandidateStep,
                          Step tvCandidateStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new CollectJobParameters())
                .start(providerListStep)
                .next(movieCandidateStep)
                .next(tvCandidateStep)
                .build();
    }

    @Bean
    public Step providerListStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 ProviderListTasklet tasklet) {
        return new StepBuilder("providerListStep", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    public Step movieCandidateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   TmdbClient tmdbClient, ProviderCollectService providerCollectService,
                                   CollectPoolMonitor poolMonitor, CandidateWriter writer) {
        CandidateReader reader = new CandidateReader("영화",
                (providers, page) -> toPage(tmdbClient.discoverMovies(providers, page), CollectJobConfig::fromMovie),
                providerCollectService, poolMonitor);
        return candidateStep("movieCandidateStep", jobRepository, transactionManager, reader, writer);
    }

    @Bean
    public Step tvCandidateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                TmdbClient tmdbClient, ProviderCollectService providerCollectService,
                                CollectPoolMonitor poolMonitor, CandidateWriter writer) {
        CandidateReader reader = new CandidateReader("드라마",
                (providers, page) -> toPage(tmdbClient.discoverTvs(providers, page), CollectJobConfig::fromTv),
                providerCollectService, poolMonitor);
        return candidateStep("tvCandidateStep", jobRepository, transactionManager, reader, writer);
    }

    /** chunk Step을 만든다. transactionManager를 빼면 Batch 6 기본값이 트랜잭션 없는 매니저라 반드시 지정한다 */
    private Step candidateStep(String name, JobRepository jobRepository, PlatformTransactionManager transactionManager,
                               CandidateReader reader, CandidateWriter writer) {
        return new StepBuilder(name, jobRepository)
                .<CandidateTitle, CandidateTitle>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }

    // TMDB 응답 -> 후보 변환 (응답 DTO를 이 밖으로 넘기지 않는다)

    private static <T> CandidatePage toPage(TmdbPage<T> page, Function<T, CandidateTitle> mapper) {
        if (page == null || page.results() == null) {
            return new CandidatePage(List.of(), 0);
        }
        List<CandidateTitle> items = page.results().stream().map(mapper).filter(Objects::nonNull).toList();
        return new CandidatePage(items, page.totalPages());
    }

    private static CandidateTitle fromMovie(TmdbMovieSummary movie) {
        return toCandidate(MediaType.MOVIE, movie.id(), movie.title(), movie.originalTitle(), movie.releaseDate(),
                movie.posterPath(), movie.backdropPath(), movie.originalLanguage(), movie.popularity(),
                movie.voteAverage());
    }

    private static CandidateTitle fromTv(TmdbTvSummary tv) {
        // 드라마의 날짜 필드는 first_air_date다(영화는 release_date)
        return toCandidate(MediaType.TV, tv.id(), tv.name(), tv.originalName(), tv.firstAirDate(),
                tv.posterPath(), tv.backdropPath(), tv.originalLanguage(), tv.popularity(), tv.voteAverage());
    }

    /** 한국어 제목이 없으면 원제를 쓴다. 둘 다 없으면 저장할 수 없어 null(건너뜀). 컬럼 길이·소수 자릿수에 맞춰 다듬는다 */
    private static CandidateTitle toCandidate(MediaType mediaType, long id, String title, String originalTitle,
                                              String date, String posterPath, String backdropPath,
                                              String originalLanguage, Double popularity, Double voteAverage) {
        String chosen = (title != null && !title.isBlank()) ? title : originalTitle;
        if (chosen == null || chosen.isBlank()) {
            return null;
        }
        return new CandidateTitle(mediaType, Math.toIntExact(id), cut(chosen, MAX_TITLE_LENGTH),
                originalTitle == null ? null : cut(originalTitle, MAX_TITLE_LENGTH),
                TitleDetailRules.parseDate(date), posterPath, backdropPath, originalLanguage,
                decimal(popularity, 3), decimal(voteAverage, 2));
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Double을 NUMERIC 자릿수(scale)에 맞춘 BigDecimal로 바꾼다. 비어 있으면 null */
    private static BigDecimal decimal(Double value, int scale) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
