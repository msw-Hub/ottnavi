package com.ottnavi.collect.step.candidate;

import com.ottnavi.catalog.service.model.CandidateTitle;
import com.ottnavi.collect.job.CollectProgressRegistry;
import com.ottnavi.collect.job.CollectStepCode;
import com.ottnavi.collect.step.CollectPoolMonitor;
import com.ottnavi.collect.step.StepProgressListener;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.provider.service.ProviderCollectService;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** 수집 Step 조립: 제공처 목록(Tasklet) + 영화·드라마 후보(chunk). Job 순서는 CollectJobConfig가 정한다 */
@Configuration
public class CandidateStepConfig {

    public static final String MOVIE_CANDIDATE_STEP = "movieCandidateStep"; // 진행 표시의 단계 코드(CollectStepCode)가 참조한다
    public static final String TV_CANDIDATE_STEP = "tvCandidateStep";

    private static final int CHUNK_SIZE = 20; // discover 한 쪽이 20건이라 한 쪽을 한 번에 커밋한다

    @Bean
    public Step providerListStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 ProviderListTasklet tasklet) {
        return new StepBuilder("providerListStep", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    public Step movieCandidateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   TmdbClient tmdbClient, ProviderCollectService providerCollectService,
                                   CollectPoolMonitor poolMonitor, CandidateWriter writer,
                                   CollectProgressRegistry progressRegistry) {
        CandidateReader reader = new CandidateReader("영화",
                (providers, page) -> CandidateMapper.toPage(tmdbClient.discoverMovies(providers, page),
                        CandidateMapper::fromMovie),
                providerCollectService, poolMonitor);
        StepProgressListener progressListener = StepProgressListener.forCandidates(
                CollectStepCode.MOVIE_CANDIDATE, reader, progressRegistry);
        return candidateStep(MOVIE_CANDIDATE_STEP, jobRepository, transactionManager, reader, writer, progressListener);
    }

    @Bean
    public Step tvCandidateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                TmdbClient tmdbClient, ProviderCollectService providerCollectService,
                                CollectPoolMonitor poolMonitor, CandidateWriter writer,
                                CollectProgressRegistry progressRegistry) {
        CandidateReader reader = new CandidateReader("드라마",
                (providers, page) -> CandidateMapper.toPage(tmdbClient.discoverTvs(providers, page),
                        CandidateMapper::fromTv),
                providerCollectService, poolMonitor);
        StepProgressListener progressListener = StepProgressListener.forCandidates(
                CollectStepCode.TV_CANDIDATE, reader, progressRegistry);
        return candidateStep(TV_CANDIDATE_STEP, jobRepository, transactionManager, reader, writer, progressListener);
    }

    /** chunk Step을 만든다. transactionManager를 빼면 Batch 6 기본값이 트랜잭션 없는 매니저라 반드시 지정한다 */
    private Step candidateStep(String name, JobRepository jobRepository, PlatformTransactionManager transactionManager,
                               CandidateReader reader, CandidateWriter writer, StepProgressListener progressListener) {
        return new StepBuilder(name, jobRepository)
                .<CandidateTitle, CandidateTitle>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(reader)
                .writer(writer)
                .listener(progressListener) // 청크마다 서비스·쪽 진행을 진행 표시(메모리)에 넘긴다
                .build();
    }
}
