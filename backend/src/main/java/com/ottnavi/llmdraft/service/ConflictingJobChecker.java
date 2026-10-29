package com.ottnavi.llmdraft.service;

import com.ottnavi.collect.job.CollectExecutionQuery;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.collect.job.CollectExecutionRules;
import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.ServerBootTime;
import com.ottnavi.home.job.PopularityExecutionQuery;
import com.ottnavi.plan.batch.PlanRecalcExecutionQuery;
import com.ottnavi.retention.job.RetentionExecutionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 작품 줄거리를 바꾸면 안 되는 배치(수집·재계산·데이터 정리·인기 수집)가 지금 실행 중인지 확인한다.
 * 기존 "전체 동시 실행 금지" 확인(RetentionLauncher.start)과 같은 네 가지 조회를 쓰고, 서버 기동 전에 만들어진 기록은
 * 중단된 것이라 실행 중으로 보지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ConflictingJobChecker {

    private final CollectExecutionQuery collectExecutionQuery;
    private final PlanRecalcExecutionQuery planRecalcExecutionQuery;
    private final RetentionExecutionQuery retentionExecutionQuery;
    private final PopularityExecutionQuery popularityExecutionQuery;
    private final ServerBootTime serverBootTime;

    /** 수집·재계산·정리·인기 수집 중 하나라도 실행 중이면 true */
    public boolean isAnyRunning() {
        // 1. 수집: 끝나지 않은 실행 중 서버 기동 뒤에 시작된 것만 실행 중으로 본다
        for (CollectExecutionRecord record : collectExecutionQuery.findUnfinished()) {
            if (CollectExecutionRules.statusOf(record, serverBootTime.getBootedAt()) == CollectExecutionStatus.RUNNING) {
                return true;
            }
        }
        // 2. 재계산, 정리, 인기 수집
        if (planRecalcExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
            return true;
        }
        if (retentionExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
            return true;
        }
        return popularityExecutionQuery.existsRunning(serverBootTime.getBootedAt());
    }
}
