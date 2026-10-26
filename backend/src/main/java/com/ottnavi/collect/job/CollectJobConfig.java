package com.ottnavi.collect.job;

import com.ottnavi.home.job.HomeCacheEvictListener;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  수집 Job 조립: 찜 기준 refresh_tier 재맞춤 (Task 077) → 제공처 목록 → 영화 후보 → 드라마 후보 (Task 043) → 영화 상세 → 드라마 상세·시즌 → 제공 상태 판정 (Task 044).
 *  scope=AVAILABILITY_TV 실행은 앞 Step을 모두 건너뛰고 시즌 판정 Step(tvAvailabilityStep)만 돈다(CollectScopeDecider).
 *  tier=DAILY 실행은 재맞춤 뒤 제공처 목록·후보 Step을 건너뛰고 상세·판정 Step만 돈다(CollectTierDecider).
 *  tier=WEEKLY·ALL은 전체 흐름이며, 상세·판정 Step이 읽는 작품 범위만 tier(refresh_tier)로 다르다(ALL은 전체).
 *  Step 조립은 step/refresh/RefreshTierStepConfig, step/candidate/CandidateStepConfig, step/detail/DetailStepConfig
 */
@Configuration
public class CollectJobConfig {

    public static final String JOB_NAME = "collectJob";

    @Bean
    public Job collectJob(JobRepository jobRepository, Step refreshTierStep, Step providerListStep,
                          Step movieCandidateStep, Step tvCandidateStep, Step movieDetailStep, Step tvDetailStep,
                          Step availabilityStep, Step tvAvailabilityStep,
                          TitleCacheEvictListener titleCacheEvictListener,
                          HomeCacheEvictListener homeCacheEvictListener,
                          ExclusiveTitlesRefreshListener exclusiveTitlesRefreshListener,
                          CollectCompletionListener collectCompletionListener) {
        // 후보 Step이 모두 끝난 뒤에 상세 Step이 시작하므로 상세 Step이 읽는 title 목록은 그 시점에 고정이다
        // 1. Decider가 scope 파라미터로 갈래를 정한다: AVAILABILITY_TV면 시즌 판정 Step 하나만, 그 밖(FULL)은 전체
        CollectScopeDecider scopeDecider = new CollectScopeDecider();
        // 2. 두 번째 Decider가 tier로 갈래를 정한다: DAILY면 후보 발견을 건너뛰고 상세 Step부터, 그 밖은 전체
        CollectTierDecider tierDecider = new CollectTierDecider();
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new CollectJobParameters())
                // afterJob은 등록의 역순으로 불린다: 캐시를 먼저 비우고 마지막에 완료 이벤트를 발행한다(재계산 시작, Task 069)
                .listener(collectCompletionListener)
                .listener(titleCacheEvictListener) // 완료 시 작품 상세 캐시를 비운다(Task 051)
                .listener(homeCacheEvictListener) // 완료 시 홈·탐색 캐시를 비운다(Task 107)
                .listener(exclusiveTitlesRefreshListener) // 성공하면 독점 목록을 새로 계산해 채우고, 아니면 비운다(Task 078)
                .start(scopeDecider).on(CollectScope.AVAILABILITY_TV.name()).to(tvAvailabilityStep)
                // 3. scope가 없으면 먼저 찜 여부로 작품의 refresh_tier를 맞춘다(세 tier 모두)
                .from(scopeDecider).on("*").to(refreshTierStep)
                .next(tierDecider).on(CollectTierDecider.DAILY).to(movieDetailStep)
                // 4. DAILY가 아니면 기존 Step 6개를 그대로 이어 붙인다(상세·판정 Step의 대상 범위는 tier가 정한다)
                .from(tierDecider).on("*").to(providerListStep)
                .next(movieCandidateStep)
                .next(tvCandidateStep)
                .next(movieDetailStep)
                .next(tvDetailStep)
                .next(availabilityStep)
                .end()
                .build();
    }
}
