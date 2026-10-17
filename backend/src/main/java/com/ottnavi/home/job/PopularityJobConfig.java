package com.ottnavi.home.job;

import com.ottnavi.home.step.PopularityCollectTasklet;
import com.ottnavi.home.step.PopularityPurgeTasklet;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 인기 순위 수집 Job 조립(Task 107): 스냅샷 수집 → 오래된 스냅샷 삭제 두 Step.
 * 수집이 실패해도 삭제 Step으로 넘어간다("*" 전이, 보관 기간 정리는 수집 성공과 무관하다). 실패한 Step은
 * BATCH_STEP_EXECUTION에 남고 error 로그에 스택이 찍힌다. Tasklet에는 트랜잭션 관리자를 주지 않는다(정리 Job과 같다).
 * 저장·삭제는 각 서비스가 자기 트랜잭션을 열고, TMDB 호출은 그 트랜잭션 밖에서 한다.
 */
@Configuration
public class PopularityJobConfig {

	public static final String JOB_NAME = "popularityJob";
	public static final String COLLECT_STEP_NAME = "popularityCollectStep";
	public static final String PURGE_STEP_NAME = "popularityPurgeStep";

	@Bean
	public Job popularityJob(JobRepository jobRepository, Step popularityCollectStep, Step popularityPurgeStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.validator(new PopularityJobParameters())
				.start(popularityCollectStep).on("*").to(popularityPurgeStep)
				.from(popularityPurgeStep).on("*").end()
				.end()
				.build();
	}

	@Bean
	public Step popularityCollectStep(JobRepository jobRepository, PopularityCollectTasklet tasklet) {
		return new StepBuilder(COLLECT_STEP_NAME, jobRepository).tasklet(tasklet).build();
	}

	@Bean
	public Step popularityPurgeStep(JobRepository jobRepository, PopularityPurgeTasklet tasklet) {
		return new StepBuilder(PURGE_STEP_NAME, jobRepository).tasklet(tasklet).build();
	}
}
