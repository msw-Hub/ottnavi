package com.ottnavi.plan.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 매월 재계산 Job 조립(Task 069): Step 하나가 대상 플랜을 고르고 플랜마다 재계산한다.
 * Tasklet에는 트랜잭션 관리자를 주지 않는다(Batch가 기본 Resourceless 관리자를 쓴다). 플랜마다 PlanRecalcWriter가
 * 자기 JPA 트랜잭션을 열어 커밋하므로 한 플랜의 실패가 다른 플랜을 되돌리지 않는다.
 */
@Configuration
@EnableConfigurationProperties(PlanRecalcProperties.class)
public class PlanRecalcJobConfig {

	public static final String JOB_NAME = "planRecalcJob";
	public static final String STEP_NAME = "planRecalcStep";

	@Bean
	public Job planRecalcJob(JobRepository jobRepository, Step planRecalcStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.validator(new PlanRecalcJobParameters())
				.start(planRecalcStep)
				.build();
	}

	@Bean
	public Step planRecalcStep(JobRepository jobRepository, PlanRecalcTasklet tasklet) {
		return new StepBuilder(STEP_NAME, jobRepository).tasklet(tasklet).build();
	}
}
