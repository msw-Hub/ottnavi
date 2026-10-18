package com.ottnavi.home.job;

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
 * 인기 수집 전용 비동기 JobOperator 조립(Task 107). 정리 쪽 RetentionLaunchConfig와 같은 방식이다.
 * 기본 JobOperator(동기)는 그대로 두고, 이 operator는 defaultCandidate=false로 등록해 타입 주입에 끼어들지 않게 한다.
 * 쓰는 곳은 반드시 {@code @Qualifier("popularityJobOperator")}로 이름을 지정한다.
 */
@Configuration
public class PopularityLaunchConfig {

	@Bean(defaultCandidate = false)
	public JobOperator popularityJobOperator(JobRepository jobRepository, Job popularityJob,
			PlatformTransactionManager transactionManager) throws Exception {
		// 1. 동시에 한 건만 돌리는 비동기 실행기
		SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("popularity-");
		taskExecutor.setConcurrencyLimit(1);

		// 2. 인기 수집 Job만 담은 레지스트리
		MapJobRegistry jobRegistry = new MapJobRegistry();
		try {
			jobRegistry.register(popularityJob);
		} catch (DuplicateJobException e) {
			throw new IllegalStateException("인기 수집 Job을 레지스트리에 등록하지 못했다", e);
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
