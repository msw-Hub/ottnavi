package com.ottnavi.collect.job;

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
 * 수집 전용 비동기 JobOperator 조립(Task 045).
 * Boot가 만드는 기본 JobOperator는 동기라 그대로 두고(기존 수집 테스트가 타입으로 주입받는다),
 * 관리자 API가 쓰는 비동기 operator는 defaultCandidate=false로 등록해 타입 주입에 끼어들지 않게 한다.
 * 쓰는 곳은 반드시 {@code @Qualifier("collectJobOperator")}로 이름을 지정한다.
 */
@Configuration
public class CollectLaunchConfig {

    @Bean(defaultCandidate = false)
    public JobOperator collectJobOperator(JobRepository jobRepository, Job collectJob,
                                          PlatformTransactionManager transactionManager) throws Exception {
        // 1. 동시에 한 건만 돌리는 비동기 실행기(전체 동시 실행 금지의 보조 장치)
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("collect-");
        taskExecutor.setConcurrencyLimit(1);

        // 2. 수집 Job만 담은 레지스트리(restart가 Job 이름으로 Job을 찾는다)
        MapJobRegistry jobRegistry = new MapJobRegistry();
        try {
            jobRegistry.register(collectJob);
        } catch (DuplicateJobException e) {
            throw new IllegalStateException("수집 Job을 레지스트리에 등록하지 못했다", e);
        }

        // 3. 위 둘을 묶은 operator. 트랜잭션 프록시는 팩토리 빈이 씌워 준다
        JobOperatorFactoryBean factoryBean = new JobOperatorFactoryBean();
        factoryBean.setJobRepository(jobRepository);
        factoryBean.setJobRegistry(jobRegistry);
        factoryBean.setTransactionManager(transactionManager);
        factoryBean.setTaskExecutor(taskExecutor);
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }
}
