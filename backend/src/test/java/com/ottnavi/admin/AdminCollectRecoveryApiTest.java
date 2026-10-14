package com.ottnavi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.admin.dto.response.CollectExecutionResponse;
import com.ottnavi.admin.service.CollectExecutionQueryService;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.CollectJobLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Task 045 단계 4: 복구(recover)·재시작(restart)과 시작 API의 중단됨 처리를 시험한다(Docker 필요).
 * 서버가 죽은 상황은 markAsCrashed로 Batch 기록을 직접 고쳐 흉내 낸다.
 */
class AdminCollectRecoveryApiTest extends AdminCollectApiTestSupport {

	private static final String DAY = "2026-03-10";

	@Autowired
	private CollectExecutionQueryService queryService;

	@Test
	void 중단된_실행을_복구하면_FAILED_endedAt_null_고정_사유_canRestart_참이다() throws Exception {
		// given
		long executionId = runToEnd();
		markAsCrashed(executionId);

		// when, then
		postAction(executionId, "recover")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.executionId").value(executionId))
				.andExpect(jsonPath("$.data.status").value("FAILED"))
				.andExpect(jsonPath("$.data.endedAt").isEmpty())
				.andExpect(jsonPath("$.data.failureReason").value(CollectJobLauncher.RECOVERED_REASON))
				.andExpect(jsonPath("$.data.canRecover").value(false))
				.andExpect(jsonPath("$.data.canRestart").value(true));
	}

	@Test
	void 완료된_실행을_복구하면_409_BATCH_NOT_STOPPED다() throws Exception {
		// given
		long executionId = runToEnd();

		// when, then
		postAction(executionId, "recover")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_NOT_STOPPED"));
	}

	@Test
	void 실패한_실행을_복구하면_409_BATCH_NOT_STOPPED다() throws Exception {
		// given
		stubFailingCollection();
		long executionId = runToEnd();

		// when, then
		postAction(executionId, "recover")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_NOT_STOPPED"));
	}

	@Test
	void 없는_실행을_복구하면_404_RESOURCE_NOT_FOUND다() throws Exception {
		// given, when, then
		postAction(999999L, "recover")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void executionId가_0이면_400_VALIDATION_FAILED다() throws Exception {
		// given, when, then
		postAction("0", "recover")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		postAction("0", "restart")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 중단된_실행이_있는_날짜를_새로_실행하면_409_BATCH_RECOVERY_REQUIRED다() throws Exception {
		// given
		long executionId = runToEnd();
		markAsCrashed(executionId);

		// when, then
		postCreate(createBody(DAY, "ALL"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_RECOVERY_REQUIRED"));
	}

	@Test
	void 중단된_실행을_재시작하면_409_BATCH_RECOVERY_REQUIRED다() throws Exception {
		// given
		long executionId = runToEnd();
		markAsCrashed(executionId);

		// when, then
		postAction(executionId, "restart")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_RECOVERY_REQUIRED"));
	}

	@Test
	void 완료된_실행을_재시작하면_409_BATCH_ALREADY_COMPLETED다() throws Exception {
		// given
		long executionId = runToEnd();

		// when, then
		postAction(executionId, "restart")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_COMPLETED"));
	}

	@Test
	void 실패한_실행을_재시작하면_202이고_같은_인스턴스의_새_실행이다() throws Exception {
		// given
		stubFailingCollection();
		long failedId = runToEnd();
		wireMock.resetAll();
		stubSuccessfulCollection(0);

		// when
		var result = postAction(failedId, "restart");

		// then
		result.andExpect(status().isAccepted());
		long newId = executionIdOf(result);
		awaitFinished(newId);
		CollectExecutionRecord failed = collectExecutionQuery.findById(failedId).orElseThrow();
		CollectExecutionRecord restarted = collectExecutionQuery.findById(newId).orElseThrow();
		assertThat(newId).isGreaterThan(failedId);
		assertThat(restarted.instanceId()).isEqualTo(failed.instanceId());
		assertThat(restarted.batchStatus()).isEqualTo(BatchStatus.COMPLETED);
	}

	@Test
	void 실패한_날짜를_시작_API로_요청해도_재시작된다() throws Exception {
		// given
		stubFailingCollection();
		long failedId = runToEnd();
		wireMock.resetAll();
		stubSuccessfulCollection(0);

		// when
		var result = postCreate(createBody(DAY, "ALL"));

		// then
		result.andExpect(status().isAccepted());
		long newId = executionIdOf(result);
		awaitFinished(newId);
		CollectExecutionRecord failed = collectExecutionQuery.findById(failedId).orElseThrow();
		CollectExecutionRecord restarted = collectExecutionQuery.findById(newId).orElseThrow();
		assertThat(restarted.instanceId()).isEqualTo(failed.instanceId());
		assertThat(restarted.batchStatus()).isEqualTo(BatchStatus.COMPLETED);
	}

	@Test
	void 복구_재시작_또_중단_다시_복구가_차례로_동작한다() throws Exception {
		// given: A는 TMDB 오류로 실패한 뒤 서버가 죽은 것처럼 중단됨 상태가 된다
		stubFailingCollection();
		long a = runToEnd();
		markAsCrashed(a);

		// 1. A는 중단됨이라 복구만 켜지고 재시작은 꺼진다
		CollectExecutionResponse aStopped = queryService.getExecution(a);
		assertThat(aStopped.status()).isEqualTo(CollectExecutionStatus.STOPPED);
		assertThat(aStopped.canRecover()).isTrue();
		assertThat(aStopped.canRestart()).isFalse();

		// 2. A 복구 → 실패(RECOVERED), 재시작이 켜진다
		postAction(a, "recover")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("FAILED"))
				.andExpect(jsonPath("$.data.canRestart").value(true));
		assertThat(collectExecutionQuery.findById(a).orElseThrow().exitCode())
				.isEqualTo(CollectJobLauncher.RECOVERED_EXIT_CODE);

		// 3. A 재시작 → B 완료 후 B도 중단됨으로 만든다
		wireMock.resetAll();
		stubSuccessfulCollection(0);
		var restartA = postAction(a, "restart");
		restartA.andExpect(status().isAccepted());
		long b = executionIdOf(restartA);
		awaitFinished(b);
		markAsCrashed(b);
		assertThat(queryService.getExecution(a).canRestart()).isFalse();
		CollectExecutionResponse bStopped = queryService.getExecution(b);
		assertThat(bStopped.status()).isEqualTo(CollectExecutionStatus.STOPPED);
		assertThat(bStopped.canRecover()).isTrue();
		postCreate(createBody(DAY, "ALL"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_RECOVERY_REQUIRED"));

		// 4. B 복구 → 두 번째 복구도 동작해야 한다(복구 표시가 컨텍스트에 남아 막히지 않는다)
		postAction(b, "recover")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("FAILED"))
				.andExpect(jsonPath("$.data.failureReason").value(CollectJobLauncher.RECOVERED_REASON))
				.andExpect(jsonPath("$.data.canRestart").value(true));
		assertThat(collectExecutionQuery.findById(b).orElseThrow().exitCode())
				.isEqualTo(CollectJobLauncher.RECOVERED_EXIT_CODE);

		// 5. B 재시작 → C 완료. B의 재시작은 꺼지고 같은 날짜 시작은 완료로 거절된다
		var restartB = postAction(b, "restart");
		restartB.andExpect(status().isAccepted());
		long c = executionIdOf(restartB);
		awaitFinished(c);
		assertThat(collectExecutionQuery.findById(c).orElseThrow().batchStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(queryService.getExecution(b).canRestart()).isFalse();
		postCreate(createBody(DAY, "ALL"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_COMPLETED"));

		// 6. 세 실행은 같은 Job 인스턴스다
		long instanceA = collectExecutionQuery.findById(a).orElseThrow().instanceId();
		assertThat(collectExecutionQuery.findById(b).orElseThrow().instanceId()).isEqualTo(instanceA);
		assertThat(collectExecutionQuery.findById(c).orElseThrow().instanceId()).isEqualTo(instanceA);
	}

	/** 수집을 요청해 끝날 때까지 기다리고 실행 ID를 돌려준다(성공·실패는 호출 전에 등록한 TMDB 응답이 정한다) */
	private long runToEnd() throws Exception {
		long executionId = executionIdOf(postCreate(createBody(DAY, "ALL")).andExpect(status().isAccepted()));
		awaitFinished(executionId);
		return executionId;
	}
}
