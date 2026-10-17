package com.ottnavi.plan.batch;

import com.ottnavi.collect.job.CollectCompletedEvent;
import com.ottnavi.plan.exception.PlanRecalcAlreadyRunningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 수집 완료 이벤트를 받아 재계산을 시작한다(Task 069, R-21). 수집이 실패하면 이벤트가 없어 재계산도 없다.
 * 설정 app.plan-recalc.auto-start=false면 시작하지 않는다(테스트 기본값). 이벤트는 수집 스레드에서 동기로 전달되지만
 * 재계산 Job은 비동기 operator가 별도 스레드에서 돌려서 수집 종료를 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanRecalcTrigger {

	private final PlanRecalcProperties properties;
	private final PlanRecalcLauncher launcher;

	@EventListener
	public void onCollectCompleted(CollectCompletedEvent event) {
		// 1. 자동 시작이 꺼져 있으면 아무것도 하지 않는다
		if (!properties.autoStart()) {
			log.info("재계산 자동 시작이 꺼져 있어 시작하지 않는다: collectExecutionId={}", event.executionId());
			return;
		}

		// 2. 시작한다. 이미 실행 중이면 경고만 남긴다(관리자 수동 재실행 API로 복구할 수 있다)
		try {
			launcher.startAfterCollect(event.executionId());
		} catch (PlanRecalcAlreadyRunningException e) {
			log.warn("재계산을 자동으로 시작하지 못했다: collectExecutionId={}, 사유={}", event.executionId(), e.getMessage());
		}
	}
}
