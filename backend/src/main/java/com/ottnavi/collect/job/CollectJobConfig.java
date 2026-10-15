package com.ottnavi.collect.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  수집 Job 조립: 제공처 목록 → 영화 후보 → 드라마 후보 (Task 043) → 영화 상세 → 드라마 상세·시즌 → 제공 상태 판정 (Task 044).
 *  Step 조립은 step/candidate/CandidateStepConfig, step/detail/DetailStepConfig
 */
@Configuration
public class CollectJobConfig {

    public static final String JOB_NAME = "collectJob";

    @Bean
    public Job collectJob(JobRepository jobRepository, Step providerListStep, Step movieCandidateStep,
                          Step tvCandidateStep, Step movieDetailStep, Step tvDetailStep, Step availabilityStep,
                          TitleCacheEvictListener titleCacheEvictListener) {
        // 후보 Step이 모두 끝난 뒤에 상세 Step이 시작하므로 상세 Step이 읽는 title 목록은 그 시점에 고정이다
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new CollectJobParameters())
                .listener(titleCacheEvictListener) // 완료 시 작품 상세 캐시를 비운다(Task 051)
                .start(providerListStep)
                .next(movieCandidateStep)
                .next(tvCandidateStep)
                .next(movieDetailStep)
                .next(tvDetailStep)
                .next(availabilityStep)
                .build();
    }
}
