package com.ottnavi.plan.batch;

import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.JobOperatorFactoryBean;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 재계산 전용 비동기 JobOperator 조립(Task 069). 수집 쪽 CollectLaunchConfig와 같은 방식이다.
 * 기본 JobOperator(동기)는 그대로 두고, 이 operator는 defaultCandidate=false로 등록해 타입 주입에 끼어들지 않게 한다.
 * 쓰는 곳은 반드시 {@code @Qualifier("planRecalcJobOperator")}로 이름을 지정한다.
 */
@Configuration
public class PlanRecalcLaunchConfig {

	@Bean(defaultCandidate = false)
	public JobOperator planRecalcJobOperator(JobRepository jobRepository, Job planRecalcJob,
			PlatformTransactionManager transactionManager) throws Exception {
		// 1. 동시에 한 건만 돌리는 비동기 실행기
		SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("recalc-");
		taskExecutor.setConcurrencyLimit(1);

		// 2. 재계산 Job만 담은 레지스트리
		MapJobRegistry jobRegistry = new MapJobRegistry();
		try {
			jobRegistry.register(planRecalcJob);
		} catch (DuplicateJobException e) {
			throw new IllegalStateException("재계산 Job을 레지스트리에 등록하지 못했다", e);
		}

		// 3. 위 둘을 묶은 operator
		JobOperatorFactoryBean factoryBean = new JobOperatorFactoryBean();
		factoryBean.setJobRepository(jobRepository);
		factoryBean.setJobRegistry(jobRegistry);
		factoryBean.setTransactionManager(transactionManager);
		factoryBean.setTaskExecutor(taskExecutor);
		factoryBean.afterPropertiesSet();
		return factoryBean.getObject();
	}
}
