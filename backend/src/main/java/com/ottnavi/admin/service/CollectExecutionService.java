package com.ottnavi.admin.service;

import com.ottnavi.admin.dto.response.CollectExecutionResponse;
import com.ottnavi.admin.exception.BatchAlreadyCompletedException;
import com.ottnavi.admin.exception.BatchAlreadyRunningException;
import com.ottnavi.admin.exception.BatchNotStoppedException;
import com.ottnavi.admin.exception.BatchRecoveryRequiredException;
import com.ottnavi.admin.service.model.CollectViewContext;
import com.ottnavi.collect.job.CollectExecutionQuery;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.collect.job.CollectExecutionRules;
import com.ottnavi.collect.job.CollectExecutionRules.DateVerdict;
import com.ottnavi.collect.job.CollectExecutionRules.LaunchVerdict;
import com.ottnavi.collect.job.CollectExecutionRules.ScopeVerdict;
import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.CollectJobLauncher;
import com.ottnavi.collect.job.CollectProperties;
import com.ottnavi.collect.job.CollectScope;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.collect.job.ServerBootTime;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.plan.batch.PlanRecalcExecutionQuery;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.stereotype.Service;

/**
 * 수집 실행의 시작·재시작·복구 명령을 처리한다(Task 045).
 *
 * <p>이 클래스에는 {@code @Transactional}을 붙이지 않는다(backend.md의 "서비스는 readOnly 기본"의 예외).
 * JobRepository는 이미 열린 트랜잭션 안에서 실행 기록을 만드는 것을 거부하고,
 * 바깥 트랜잭션이 커밋되기 전에는 비동기 스레드가 방금 만든 실행 행을 읽지 못하기 때문이다.
 *
 * <p>"사전 검사 → 시작"이 하나의 덩어리로 실행되도록 {@code launchLock}으로 직렬화한다.
 * 서버가 한 대라는 전제이며, 전체 동시 실행 금지(결정 0)를 이 락과 사전 검사로 지킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectExecutionService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final CollectExecutionQuery collectExecutionQuery;
	private final CollectJobLauncher collectJobLauncher;
	private final CollectExecutionQueryService collectExecutionQueryService;
	private final CollectProperties collectProperties;
	private final ServerBootTime serverBootTime;
	private final PlanRecalcExecutionQuery planRecalcExecutionQuery;

	private final ReentrantLock launchLock = new ReentrantLock(); // 사전 검사와 시작 사이에 다른 요청이 끼지 못하게 한다

	/**
	 * 수집을 새로 시작하고 새 실행 ID를 돌려준다. 같은 날짜·범위의 최신 실행이 실패면 재시작한다.
	 * 예: targetDate=null, tier=ALL, scope=null → 오늘(KST) 기준 전체 수집으로 시작.
	 * scope가 있으면(로컬 전용) 그 범위의 Step만 실행하며, 중복·재시작 판정은 scope별로 따로 한다.
	 */
	public long start(LocalDate targetDateOrNull, CollectTier tier, CollectScope scope) {
		// 1. 날짜가 비면 오늘(KST)로 채운다
		LocalDate today = LocalDate.now(KST);
		LocalDate targetDate = targetDateOrNull;
		if (targetDate == null) {
			targetDate = today;
		}

		// 2. MVP는 ALL만 실행한다
		if (tier != CollectTier.ALL) {
			throw new FieldValidationException("tier", "MVP는 ALL만 실행할 수 있습니다.");
		}

		// 3. 날짜 규칙: 운영은 오늘만, local은 미래만 막는다
		validateDate(targetDate, today);

		// 4. 실행 범위 규칙: 운영은 전체만, local은 일부 범위도 허용
		validateScope(scope);

		// 5. 사전 검사와 시작을 한 덩어리로 직렬화한다
		launchLock.lock();
		try {
			// 6. 같은 날짜·범위·scope의 최신 실행과 그 상태
			Optional<CollectExecutionRecord> latest = collectExecutionQuery.findLatest(targetDate, tier, scope);
			CollectExecutionStatus latestStatus = null;
			long sameInstanceId = -1;
			if (latest.isPresent()) {
				latestStatus = CollectExecutionRules.statusOf(latest.get(), serverBootTime.getBootedAt());
				sameInstanceId = latest.get().instanceId();
			}

			// 7. 다른 날짜·범위의 수집이 실행 중인지(scope가 달라도 전체 동시 실행은 금지)
			CollectViewContext context = collectExecutionQueryService.createContext();
			CollectExecutionRecord otherRunning = context.findRunningOtherThan(sameInstanceId);

			// 8. 판정 결과가 거절이면 예외로 바꾼다
			LaunchVerdict verdict = CollectExecutionRules.judgeLaunch(latestStatus, otherRunning != null);
			throwIfRejected(verdict, otherRunning);
			throwIfRecalcRunning();

			// 9. 실패한 최신 실행이 있으면 재시작, 없으면 새로 시작
			long executionId;
			if (latestStatus == CollectExecutionStatus.FAILED) {
				executionId = restartLaunch(latest.get().executionId());
			} else {
				executionId = startLaunch(targetDate, tier, scope);
			}

			// 10. 정상 흐름 기록
			log.info("수집 실행을 접수했다: executionId={}, targetDate={}, tier={}, scope={}",
					executionId, targetDate, tier, scope);
			return executionId;
		} finally {
			launchLock.unlock();
		}
	}

	/**
	 * 실패한 실행을 새 실행으로 재시작하고 새 실행 ID를 돌려준다.
	 * 더 오래된 실행을 가리켜도 같은 날짜·범위의 가장 최근 실행 상태로 판단한다.
	 */
	public long restart(long executionId) {
		// 1. 실행을 찾는다
		Optional<CollectExecutionRecord> found = collectExecutionQuery.findById(executionId);
		if (found.isEmpty()) {
			throw new ResourceNotFoundException("해당 수집 실행을 찾을 수 없습니다.");
		}
		CollectExecutionRecord record = found.get();

		// 2. 그 실행의 날짜가 지금 허용되는지(운영은 오늘만)
		validateDate(record.targetDate(), LocalDate.now(KST));

		// 3. 사전 검사와 시작을 한 덩어리로 직렬화한다
		launchLock.lock();
		try {
			// 4. 같은 날짜·범위·scope(같은 Job 인스턴스)의 최신 실행으로 판정한다
			CollectExecutionRecord latest = record;
			Optional<CollectExecutionRecord> latestFound =
					collectExecutionQuery.findLatestOfInstance(record.instanceId());
			if (latestFound.isPresent()) {
				latest = latestFound.get();
			}
			CollectExecutionStatus latestStatus =
					CollectExecutionRules.statusOf(latest, serverBootTime.getBootedAt());
			CollectViewContext context = collectExecutionQueryService.createContext();
			CollectExecutionRecord otherRunning = context.findRunningOtherThan(latest.instanceId());
			LaunchVerdict verdict = CollectExecutionRules.judgeLaunch(latestStatus, otherRunning != null);
			throwIfRejected(verdict, otherRunning);
			throwIfRecalcRunning();

			// 5. 허용이면 최신 실행을 이어서 새 실행으로 시작한다
			long newExecutionId = restartLaunch(latest.executionId());
			log.info("수집 실행을 재시작했다: 이전 executionId={}, 새 executionId={}, targetDate={}",
					latest.executionId(), newExecutionId, latest.targetDate());
			return newExecutionId;
		} finally {
			launchLock.unlock();
		}
	}

	/** 중단된 실행을 실패로 복구하고 복구된 실행을 돌려준다. 중단됨이 아니면 409 BATCH_NOT_STOPPED이다 */
	public CollectExecutionResponse recover(long executionId) {
		launchLock.lock();
		try {
			// 1. 실행을 찾는다
			Optional<CollectExecutionRecord> found = collectExecutionQuery.findById(executionId);
			if (found.isEmpty()) {
				throw new ResourceNotFoundException("해당 수집 실행을 찾을 수 없습니다.");
			}

			// 2. 중단됨으로 판정된 실행만 복구한다
			CollectExecutionStatus status =
					CollectExecutionRules.statusOf(found.get(), serverBootTime.getBootedAt());
			if (status != CollectExecutionStatus.STOPPED) {
				throw new BatchNotStoppedException("중단된 실행만 복구할 수 있습니다.");
			}

			// 3. FAILED로 바꾸고 종료 코드를 RECOVERED로 남긴다
			collectJobLauncher.recover(executionId);
			log.info("중단된 수집 실행을 실패로 복구했다: executionId={}", executionId);
		} finally {
			launchLock.unlock();
		}

		// 4. 락을 푼 뒤 복구된 실행을 다시 읽어 돌려준다
		return collectExecutionQueryService.getExecution(executionId);
	}

	/** 날짜 규칙 위반이면 400(필드 targetDate)으로 거절한다 */
	private void validateDate(LocalDate targetDate, LocalDate today) {
		DateVerdict verdict = CollectExecutionRules.judgeDate(
				targetDate, today, collectProperties.allowTargetDateOverride());
		if (verdict == DateVerdict.NOT_TODAY) {
			throw new FieldValidationException("targetDate", "운영 환경에서는 오늘(KST) 날짜만 실행할 수 있습니다.");
		}
		if (verdict == DateVerdict.FUTURE) {
			throw new FieldValidationException("targetDate", "미래 날짜는 실행할 수 없습니다.");
		}
	}

	/** 운영에서 scope를 주면 400(필드 scope)으로 거절한다 */
	private void validateScope(CollectScope scope) {
		ScopeVerdict verdict = CollectExecutionRules.judgeScope(scope, collectProperties.allowTargetDateOverride());
		if (verdict == ScopeVerdict.NOT_ALLOWED) {
			throw new FieldValidationException("scope", "운영 환경에서는 scope를 지정할 수 없습니다. 전체 수집만 실행할 수 있습니다.");
		}
	}

	/** 재계산이 실행 중이면 수집 시작·재시작을 409 BATCH_ALREADY_RUNNING으로 막는다(서버 기동 전 기록은 실행 중으로 보지 않는다) */
	private void throwIfRecalcRunning() {
		if (planRecalcExecutionQuery.existsRunning(serverBootTime.getBootedAt())) {
			throw new BatchAlreadyRunningException("재계산이 실행 중이라 수집을 시작할 수 없습니다.");
		}
	}

	/** 시작·재시작이 불가능한 판정을 409 예외로 바꾼다. ALLOWED면 아무것도 하지 않는다 */
	private void throwIfRejected(LaunchVerdict verdict, CollectExecutionRecord otherRunning) {
		if (verdict == LaunchVerdict.ALREADY_RUNNING) {
			throw new BatchAlreadyRunningException("같은 날짜의 수집이 이미 실행 중입니다.");
		}
		if (verdict == LaunchVerdict.RECOVERY_REQUIRED) {
			throw new BatchRecoveryRequiredException("중단된 실행이 있어 먼저 복구해야 합니다.");
		}
		if (verdict == LaunchVerdict.ALREADY_COMPLETED) {
			throw new BatchAlreadyCompletedException("해당 날짜의 수집은 이미 완료되었습니다.");
		}
		if (verdict == LaunchVerdict.OTHER_RUNNING) {
			throw new BatchAlreadyRunningException(
					"수집이 이미 실행 중입니다(실행 중인 대상 날짜: " + otherRunning.targetDate() + ").");
		}
	}

	private long startLaunch(LocalDate targetDate, CollectTier tier, CollectScope scope) {
		try {
			return collectJobLauncher.start(targetDate, tier, scope);
		} catch (IllegalStateException e) {
			throw translateRace(e);
		}
	}

	private long restartLaunch(long latestExecutionId) {
		try {
			return collectJobLauncher.restart(latestExecutionId);
		} catch (IllegalStateException e) {
			throw translateRace(e);
		}
	}

	/**
	 * 사전 검사를 통과한 뒤 Batch가 경합으로 던진 "이미 실행 중"·"이미 완료" 예외를 409로 바꾼다.
	 * Launcher가 Batch 검사 예외를 IllegalStateException으로 감싸 올리므로 원인(cause)으로 구분한다.
	 * 그 밖의 예외는 그대로 돌려줘 500으로 처리된다.
	 */
	private RuntimeException translateRace(IllegalStateException e) {
		Throwable cause = e.getCause();
		if (cause instanceof JobExecutionAlreadyRunningException) {
			return new BatchAlreadyRunningException("수집이 이미 실행 중입니다.");
		}
		if (cause instanceof JobInstanceAlreadyCompleteException) {
			return new BatchAlreadyCompletedException("해당 날짜의 수집은 이미 완료되었습니다.");
		}
		return e;
	}
}
