package com.ottnavi.retention.job;

import com.ottnavi.collect.job.CollectExecutionQuery;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.collect.job.CollectExecutionRules;
import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.ServerBootTime;
import com.ottnavi.home.job.PopularityExecutionQuery;
import com.ottnavi.plan.batch.PlanRecalcExecutionQuery;
import com.ottnavi.retention.exception.RetentionAlreadyRunningException;
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
 * 데이터 정리 Job을 시작하는 창구(Task 083). 비동기 operator를 감싸고, 시작 전에 "정리·재계산·수집이 실행 중인가"를 검사한다.
 * 사전 검사와 시작이 한 덩어리로 실행되게 락으로 직렬화한다(서버 한 대 전제, PlanRecalcLauncher와 같다).
 *
 * <p>이 클래스와 호출하는 쪽에는 {@code @Transactional}을 붙이지 않는다(JobRepository가 열린 트랜잭션 안에서 실행 기록을
 * 만드는 것을 거부하기 때문이다).
 */
@Slf4j
@Component
public class RetentionLauncher {

	private final JobOperator operator;
	private final Job retentionJob;
	private final RetentionExecutionQuery retentionExecutionQuery;
	private final PlanRecalcExecutionQuery planRecalcExecutionQuery;
	private final CollectExecutionQuery collectExecutionQuery;
	private final PopularityExecutionQuery popularityExecutionQuery;
	private final ServerBootTime serverBootTime;
	private final Clock clock;

	private final ReentrantLock launchLock = new ReentrantLock();

	public RetentionLauncher(@Qualifier("retentionJobOperator") JobOperator operator, Job retentionJob,
			RetentionExecutionQuery retentionExecutionQuery, PlanRecalcExecutionQuery planRecalcExecutionQuery,
			CollectExecutionQuery collectExecutionQuery, PopularityExecutionQuery popularityExecutionQuery,
			ServerBootTime serverBootTime, Clock clock) {
		this.operator = operator;
		this.retentionJob = retentionJob;
		this.retentionExecutionQuery = retentionExecutionQuery;
		this.planRecalcExecutionQuery = planRecalcExecutionQuery;
		this.collectExecutionQuery = collectExecutionQuery;
		this.popularityExecutionQuery = popularityExecutionQuery;
		this.serverBootTime = serverBootTime;
		this.clock = clock;
	}

	/** 정리를 비동기로 시작하고 새 실행 ID를 돌려준다. 정리·재계산·수집이 실행 중이면 RetentionAlreadyRunningException이다 */
	public long start() {
		launchLock.lock();
		try {
			// 1. 정리가 이미 실행 중인지
			if (retentionExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new RetentionAlreadyRunningException("데이터 정리가 이미 실행 중입니다.");
			}

			// 2. 재계산이 실행 중인지(둘 다 플랜을 지우거나 바꾼다)
			if (planRecalcExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new RetentionAlreadyRunningException("재계산이 실행 중이라 데이터 정리를 시작할 수 없습니다.");
			}

			// 3. 수집이 실행 중인지(서버 기동 전에 만들어진 STARTED는 중단된 것이라 실행 중으로 보지 않는다)
			for (CollectExecutionRecord record : collectExecutionQuery.findUnfinished()) {
				if (CollectExecutionRules.statusOf(record, serverBootTime.getBootedAt()) == CollectExecutionStatus.RUNNING) {
					throw new RetentionAlreadyRunningException("수집이 실행 중이라 데이터 정리를 시작할 수 없습니다.");
				}
			}

			// 4. 인기 순위 수집이 실행 중인지(Task 107, 서로 막는다)
			if (popularityExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new RetentionAlreadyRunningException("인기 순위 수집이 실행 중이라 데이터 정리를 시작할 수 없습니다.");
			}

			// 5. 새 실행으로 시작한다
			JobExecution execution = operator.start(retentionJob, RetentionJobParameters.of(clock.instant()));
			log.info("데이터 정리를 시작했다: executionId={}", execution.getId());
			return execution.getId();
		} catch (JobExecutionAlreadyRunningException e) {
			throw new RetentionAlreadyRunningException("데이터 정리가 이미 실행 중입니다.");
		} catch (RetentionAlreadyRunningException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("정리 Job을 시작하지 못했다: " + e.getMessage(), e);
		} finally {
			launchLock.unlock();
		}
	}
}
