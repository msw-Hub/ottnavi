package com.ottnavi.collect.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 수집 Job 조립: 제공처 목록 → 영화 후보 → 드라마 후보 (Task 043). 상세·판정 Step은 Task 044가 이어 붙인다. Step 조립은 CollectStepConfig */
@Configuration
public class CollectJobConfig {

    public static final String JOB_NAME = "collectJob";

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
}
