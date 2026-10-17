package com.ottnavi.retention.job;

import com.ottnavi.retention.step.BatchHistoryPurgeTasklet;
import com.ottnavi.retention.step.CalcRunPurgeTasklet;
import com.ottnavi.retention.step.TitleRetentionTasklet;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 데이터 정리 Job 조립(Task 083): 작품 점검 → 배치 이력 정리 → 계산 기록 정리 세 Step.
 * 앞 Step이 실패해도 다음 Step으로 넘어간다("*" 전이). 서로 독립된 정리라 하나의 실패가 나머지를 막지 않게 한다.
 * 실패한 Step은 BATCH_STEP_EXECUTION에 FAILED로 남고 error 로그에 스택이 찍힌다.
 * Tasklet에는 트랜잭션 관리자를 주지 않는다(재계산 Job과 같다). 삭제는 각 서비스가 자기 트랜잭션을 열어 커밋한다.
 */
@Configuration
public class RetentionJobConfig {

	public static final String JOB_NAME = "retentionJob";
	public static final String TITLE_STEP_NAME = "titleRetentionStep";
	public static final String BATCH_HISTORY_STEP_NAME = "batchHistoryPurgeStep";
	public static final String CALC_RUN_STEP_NAME = "calcRunPurgeStep";

	@Bean
	public Job retentionJob(JobRepository jobRepository, Step titleRetentionStep, Step batchHistoryPurgeStep,
			Step calcRunPurgeStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.validator(new RetentionJobParameters())
				.start(titleRetentionStep).on("*").to(batchHistoryPurgeStep)
				.from(batchHistoryPurgeStep).on("*").to(calcRunPurgeStep)
				.from(calcRunPurgeStep).on("*").end()
				.end()
				.build();
	}

	@Bean
	public Step titleRetentionStep(JobRepository jobRepository, TitleRetentionTasklet tasklet) {
		return new StepBuilder(TITLE_STEP_NAME, jobRepository).tasklet(tasklet).build();
	}

	@Bean
	public Step batchHistoryPurgeStep(JobRepository jobRepository, BatchHistoryPurgeTasklet tasklet) {
		return new StepBuilder(BATCH_HISTORY_STEP_NAME, jobRepository).tasklet(tasklet).build();
	}

	@Bean
	public Step calcRunPurgeStep(JobRepository jobRepository, CalcRunPurgeTasklet tasklet) {
		return new StepBuilder(CALC_RUN_STEP_NAME, jobRepository).tasklet(tasklet).build();
	}
}
