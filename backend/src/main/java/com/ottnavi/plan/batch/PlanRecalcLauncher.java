package com.ottnavi.plan.batch;

import com.ottnavi.collect.job.CollectExecutionQuery;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.collect.job.CollectExecutionRules;
import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.ServerBootTime;
import com.ottnavi.plan.exception.PlanRecalcAlreadyRunningException;
import com.ottnavi.retention.job.RetentionExecutionQuery;
import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 재계산 Job을 시작하는 창구(Task 069). 비동기 operator를 감싸고, 시작 전에 "재계산이나 수집이 실행 중인가"를 검사한다.
 * 사전 검사와 시작이 한 덩어리로 실행되게 락으로 직렬화한다(서버 한 대 전제, 수집 쪽 CollectExecutionService와 같다).
 *
 * <p>이 클래스와 호출하는 쪽에는 {@code @Transactional}을 붙이지 않는다(JobRepository가 열린 트랜잭션 안에서 실행 기록을
 * 만드는 것을 거부하고, 바깥 트랜잭션이 커밋되기 전에는 비동기 스레드가 실행 행을 읽지 못하기 때문이다).
 */
@Slf4j
@Component
public class PlanRecalcLauncher {

	private final JobOperator operator;
	private final Job planRecalcJob;
	private final PlanRecalcExecutionQuery planRecalcExecutionQuery;
	private final CollectExecutionQuery collectExecutionQuery;
	private final RetentionExecutionQuery retentionExecutionQuery;
	private final ServerBootTime serverBootTime;
	private final Clock clock;

	private final ReentrantLock launchLock = new ReentrantLock();

	public PlanRecalcLauncher(@Qualifier("planRecalcJobOperator") JobOperator operator, Job planRecalcJob,
			PlanRecalcExecutionQuery planRecalcExecutionQuery, CollectExecutionQuery collectExecutionQuery,
			RetentionExecutionQuery retentionExecutionQuery, ServerBootTime serverBootTime, Clock clock) {
		this.operator = operator;
		this.planRecalcJob = planRecalcJob;
		this.planRecalcExecutionQuery = planRecalcExecutionQuery;
		this.collectExecutionQuery = collectExecutionQuery;
		this.retentionExecutionQuery = retentionExecutionQuery;
		this.serverBootTime = serverBootTime;
		this.clock = clock;
	}

	/**
	 * 수집이 COMPLETED로 끝난 뒤 재계산을 시작한다. 이 수집 자신은 afterJob 시점이라 DB에 아직 실행 중으로 남아 있어 실행 중 검사에서 뺀다.
	 * 재계산이나 다른 수집이 실행 중이면 PlanRecalcAlreadyRunningException이다.
	 */
	public long startAfterCollect(long collectExecutionId) {
		return start(collectExecutionId, PlanRecalcJobParameters.TRIGGER_COLLECT, collectExecutionId);
	}

	/** 관리자가 수동으로 재계산을 시작한다. 재계산이나 수집이 실행 중이면 PlanRecalcAlreadyRunningException이다 */
	public long startManual() {
		return start(PlanRecalcJobParameters.NO_COLLECT_EXECUTION, PlanRecalcJobParameters.TRIGGER_MANUAL, -1L);
	}

	private long start(long collectExecutionId, String trigger, long ignoreCollectExecutionId) {
		launchLock.lock();
		try {
			// 1. 재계산이 이미 실행 중인지
			if (planRecalcExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new PlanRecalcAlreadyRunningException("재계산이 이미 실행 중입니다.");
			}

			// 2. 수집이 실행 중인지(서버 기동 전에 만들어진 STARTED는 중단된 것이라 실행 중으로 보지 않는다)
			for (CollectExecutionRecord record : collectExecutionQuery.findUnfinished()) {
				if (record.executionId() == ignoreCollectExecutionId) {
					continue;
				}
				if (CollectExecutionRules.statusOf(record, serverBootTime.getBootedAt()) == CollectExecutionStatus.RUNNING) {
					throw new PlanRecalcAlreadyRunningException("수집이 실행 중이라 재계산을 시작할 수 없습니다.");
				}
			}

			// 3. 데이터 정리가 실행 중인지(정리가 플랜을 지우는 중에 재계산이 끼면 안 된다)
			if (retentionExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new PlanRecalcAlreadyRunningException("데이터 정리가 실행 중이라 재계산을 시작할 수 없습니다.");
			}

			// 4. 새 실행으로 시작한다
			JobExecution execution = operator.start(planRecalcJob,
					PlanRecalcJobParameters.of(collectExecutionId, trigger, clock.instant()));
			log.info("재계산을 시작했다: executionId={}, trigger={}, collectExecutionId={}", execution.getId(), trigger,
					collectExecutionId);
			return execution.getId();
		} catch (JobExecutionAlreadyRunningException e) {
			throw new PlanRecalcAlreadyRunningException("재계산이 이미 실행 중입니다.");
		} catch (PlanRecalcAlreadyRunningException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("재계산 Job을 시작하지 못했다: " + e.getMessage(), e);
		} finally {
			launchLock.unlock();
		}
	}
}
