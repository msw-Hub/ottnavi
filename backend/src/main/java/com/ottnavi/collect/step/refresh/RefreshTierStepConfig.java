package com.ottnavi.collect.step.refresh;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** 갱신 주기 재맞춤 Step 조립(Task 077). Job 순서는 CollectJobConfig가 정한다 */
@Configuration
public class RefreshTierStepConfig {

    public static final String REFRESH_TIER_STEP = "refreshTierStep";

    @Bean
    public Step refreshTierStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                RefreshTierTasklet tasklet) {
        return new StepBuilder(REFRESH_TIER_STEP, jobRepository).tasklet(tasklet, transactionManager).build();
    }
}
