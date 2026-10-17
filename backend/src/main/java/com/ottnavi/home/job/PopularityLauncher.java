package com.ottnavi.home.job;

import com.ottnavi.collect.job.CollectExecutionQuery;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.collect.job.CollectExecutionRules;
import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.ServerBootTime;
import com.ottnavi.home.exception.PopularityAlreadyRunningException;
import com.ottnavi.plan.batch.PlanRecalcExecutionQuery;
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
 * 인기 수집 Job을 시작하는 창구(Task 107). 비동기 operator를 감싸고, 시작 전에 "인기 수집·정리·재계산·수집이 실행 중인가"를 검사한다.
 * 사전 검사와 시작이 한 덩어리로 실행되게 락으로 직렬화한다(서버 한 대 전제, RetentionLauncher와 같다).
 *
 * <p>이 클래스와 호출하는 쪽에는 {@code @Transactional}을 붙이지 않는다(JobRepository가 열린 트랜잭션 안에서 실행 기록을
 * 만드는 것을 거부하기 때문이다).
 */
@Slf4j
@Component
public class PopularityLauncher {

	private final JobOperator operator;
	private final Job popularityJob;
	private final PopularityExecutionQuery popularityExecutionQuery;
	private final RetentionExecutionQuery retentionExecutionQuery;
	private final PlanRecalcExecutionQuery planRecalcExecutionQuery;
	private final CollectExecutionQuery collectExecutionQuery;
	private final ServerBootTime serverBootTime;
	private final Clock clock;

	private final ReentrantLock launchLock = new ReentrantLock();

	public PopularityLauncher(@Qualifier("popularityJobOperator") JobOperator operator, Job popularityJob,
			PopularityExecutionQuery popularityExecutionQuery, RetentionExecutionQuery retentionExecutionQuery,
			PlanRecalcExecutionQuery planRecalcExecutionQuery, CollectExecutionQuery collectExecutionQuery,
			ServerBootTime serverBootTime, Clock clock) {
		this.operator = operator;
		this.popularityJob = popularityJob;
		this.popularityExecutionQuery = popularityExecutionQuery;
		this.retentionExecutionQuery = retentionExecutionQuery;
		this.planRecalcExecutionQuery = planRecalcExecutionQuery;
		this.collectExecutionQuery = collectExecutionQuery;
		this.serverBootTime = serverBootTime;
		this.clock = clock;
	}

	/** 인기 수집을 비동기로 시작하고 새 실행 ID를 돌려준다. 인기 수집·정리·재계산·수집이 실행 중이면 PopularityAlreadyRunningException이다 */
	public long start() {
		launchLock.lock();
		try {
			// 1. 인기 수집이 이미 실행 중인지
			if (popularityExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new PopularityAlreadyRunningException("인기 순위 수집이 이미 실행 중입니다.");
			}

			// 2. 정리가 실행 중인지
			if (retentionExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new PopularityAlreadyRunningException("데이터 정리가 실행 중이라 인기 순위 수집을 시작할 수 없습니다.");
			}

			// 3. 재계산이 실행 중인지
			if (planRecalcExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
				throw new PopularityAlreadyRunningException("재계산이 실행 중이라 인기 순위 수집을 시작할 수 없습니다.");
			}

			// 4. 수집이 실행 중인지(서버 기동 전에 만들어진 STARTED는 중단된 것이라 실행 중으로 보지 않는다)
			for (CollectExecutionRecord record : collectExecutionQuery.findUnfinished()) {
				if (CollectExecutionRules.statusOf(record, serverBootTime.getBootedAt()) == CollectExecutionStatus.RUNNING) {
					throw new PopularityAlreadyRunningException("수집이 실행 중이라 인기 순위 수집을 시작할 수 없습니다.");
				}
			}

			// 5. 새 실행으로 시작한다
			JobExecution execution = operator.start(popularityJob, PopularityJobParameters.of(clock.instant()));
			log.info("인기 순위 수집을 시작했다: executionId={}", execution.getId());
			return execution.getId();
		} catch (JobExecutionAlreadyRunningException e) {
			throw new PopularityAlreadyRunningException("인기 순위 수집이 이미 실행 중입니다.");
		} catch (PopularityAlreadyRunningException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("인기 수집 Job을 시작하지 못했다: " + e.getMessage(), e);
		} finally {
			launchLock.unlock();
		}
	}
}
