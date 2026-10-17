package com.ottnavi.collect.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  수집 Job 조립: 제공처 목록 → 영화 후보 → 드라마 후보 (Task 043) → 영화 상세 → 드라마 상세·시즌 → 제공 상태 판정 (Task 044).
 *  scope=AVAILABILITY_TV 실행은 앞 5개 Step을 건너뛰고 시즌 판정 Step(tvAvailabilityStep)만 돈다(CollectScopeDecider).
 *  Step 조립은 step/candidate/CandidateStepConfig, step/detail/DetailStepConfig
 */
@Configuration
public class CollectJobConfig {

    public static final String JOB_NAME = "collectJob";

    @Bean
    public Job collectJob(JobRepository jobRepository, Step providerListStep, Step movieCandidateStep,
                          Step tvCandidateStep, Step movieDetailStep, Step tvDetailStep, Step availabilityStep,
                          Step tvAvailabilityStep, TitleCacheEvictListener titleCacheEvictListener,
                          CollectCompletionListener collectCompletionListener) {
        // 후보 Step이 모두 끝난 뒤에 상세 Step이 시작하므로 상세 Step이 읽는 title 목록은 그 시점에 고정이다
        // 1. Decider가 scope 파라미터로 갈래를 정한다: AVAILABILITY_TV면 시즌 판정 Step 하나만, 그 밖(FULL)은 전체
        CollectScopeDecider scopeDecider = new CollectScopeDecider();
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new CollectJobParameters())
                // afterJob은 등록의 역순으로 불린다: 캐시를 먼저 비우고 마지막에 완료 이벤트를 발행한다(재계산 시작, Task 069)
                .listener(collectCompletionListener)
                .listener(titleCacheEvictListener) // 완료 시 작품 상세 캐시를 비운다(Task 051)
                .start(scopeDecider).on(CollectScope.AVAILABILITY_TV.name()).to(tvAvailabilityStep)
                // 2. 전체 흐름은 기존 Step 6개를 그대로 이어 붙인다
                .from(scopeDecider).on("*").to(providerListStep)
                .next(movieCandidateStep)
                .next(tvCandidateStep)
                .next(movieDetailStep)
                .next(tvDetailStep)
                .next(availabilityStep)
                .end()
                .build();
    }
}
