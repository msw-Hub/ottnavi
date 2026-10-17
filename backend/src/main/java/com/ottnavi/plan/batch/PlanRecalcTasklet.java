package com.ottnavi.plan.batch;

import com.ottnavi.plan.service.PlanRecalcService;
import com.ottnavi.plan.service.PlanRecalcTargetService;
import com.ottnavi.plan.service.model.PlanRecalcOutcome;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * 매월 재계산 Step: 대상 플랜을 고르고 플랜마다 재계산한다(Task 069).
 * 한 플랜이 예외로 실패해도 error 로그(스택 포함)만 남기고 다음 플랜으로 넘어간다. 플랜마다 쓰기 트랜잭션이 따로 커밋되기 때문이다.
 * 대상 수는 READ_COUNT에 센다(수집 이력의 재계산 대상 플랜 수가 이 값이다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanRecalcTasklet implements Tasklet {

	private static final long COLLECT_WAIT_LIMIT_MS = 60_000; // 부른 수집이 COMPLETED로 저장되길 기다리는 최대 시간
	private static final long COLLECT_WAIT_STEP_MS = 200;

	private final PlanRecalcTargetService planRecalcTargetService;
	private final PlanRecalcService planRecalcService;
	private final PlanRecalcExecutionQuery planRecalcExecutionQuery;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws InterruptedException {
		// 1. 수집이 불렀다면 그 수집의 COMPLETED 저장을 기다린다. 데이터 기준 시각(마지막 COMPLETED 수집의 종료 시각)에 이번 수집이 들어가야 한다
		Long collectExecutionId = contribution.getStepExecution().getJobParameters()
				.getLong(PlanRecalcJobParameters.COLLECT_EXECUTION_ID);
		if (collectExecutionId != null && collectExecutionId > PlanRecalcJobParameters.NO_COLLECT_EXECUTION) {
			waitUntilCollectCompleted(collectExecutionId);
		}

		// 2. 재계산 대상 플랜을 고른다
		List<Long> planIds = planRecalcTargetService.findTargetPlanIds();

		// 3. 플랜마다 재계산한다. 실패는 세어 두고 다음 플랜으로 넘어간다
		int updated = 0;
		int skipped = 0;
		int failed = 0;
		for (Long planId : planIds) {
			contribution.incrementReadCount();
			try {
				PlanRecalcOutcome outcome = planRecalcService.recalcOne(planId);
				if (outcome == PlanRecalcOutcome.UPDATED) {
					updated++;
				} else {
					skipped++;
				}
			} catch (RuntimeException e) {
				failed++;
				log.error("플랜 재계산에 실패했다(다음 플랜으로 넘어간다): planId={}", planId, e);
			}
		}

		// 4. 요약 로그. 실패가 있으면 경고 수준으로 남긴다
		if (failed > 0) {
			log.warn("매월 재계산 종료: 대상={}건, 갱신={}건, 건너뜀={}건, 실패={}건", planIds.size(), updated, skipped, failed);
		} else {
			log.info("매월 재계산 종료: 대상={}건, 갱신={}건, 건너뜀={}건", planIds.size(), updated, skipped);
		}
		return RepeatStatus.FINISHED;
	}

	/** 부른 수집이 DB에 COMPLETED로 저장될 때까지 기다린다. 시간이 넘으면 경고만 남기고 진행한다(기준 시각이 한 수집 전 값일 뿐이다) */
	private void waitUntilCollectCompleted(long collectExecutionId) throws InterruptedException {
		long waited = 0;
		while (!planRecalcExecutionQuery.isCollectCompleted(collectExecutionId)) {
			if (waited >= COLLECT_WAIT_LIMIT_MS) {
				log.warn("수집 완료 저장을 기다리다 포기하고 진행한다: collectExecutionId={}", collectExecutionId);
				return;
			}
			Thread.sleep(COLLECT_WAIT_STEP_MS);
			waited += COLLECT_WAIT_STEP_MS;
		}
	}
}
