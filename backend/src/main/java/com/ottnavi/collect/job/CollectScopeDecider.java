package com.ottnavi.collect.job;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;

/** Job 파라미터 scope를 보고 전체 흐름(FULL)과 일부 Step만 도는 흐름(scope 이름)으로 가른다 */
public class CollectScopeDecider implements JobExecutionDecider {

    public static final String FULL = "FULL"; // scope가 없으면 전체 Step을 순서대로 실행

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        CollectScope scope = CollectJobParameters.scopeOf(jobExecution.getJobParameters());
        return new FlowExecutionStatus(scope == null ? FULL : scope.name());
    }
}
