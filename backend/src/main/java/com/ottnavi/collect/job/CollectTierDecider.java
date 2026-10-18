package com.ottnavi.collect.job;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;

/**
 * Job 파라미터 tier를 보고 후보 발견을 건너뛸지 가른다(Task 077).
 * DAILY면 "DAILY"(제공처 목록·후보 Step을 건너뛰고 상세 Step부터), 그 밖(WEEKLY·ALL)은 "FULL"(후보 발견부터 전체 흐름)이다.
 * WEEKLY와 ALL의 차이는 흐름이 아니라 상세·판정 Step이 읽는 작품 범위(refresh_tier)에만 있다.
 */
public class CollectTierDecider implements JobExecutionDecider {

    public static final String DAILY = "DAILY";
    public static final String FULL = "FULL";

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        CollectTier tier = CollectJobParameters.tierOf(jobExecution.getJobParameters());
        if (tier == CollectTier.DAILY) {
            return new FlowExecutionStatus(DAILY);
        }
        return new FlowExecutionStatus(FULL);
    }
}
