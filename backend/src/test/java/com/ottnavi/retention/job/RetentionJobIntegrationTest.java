package com.ottnavi.retention.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.ottnavi.admin.exception.BatchAlreadyRunningException;
import com.ottnavi.admin.service.CollectExecutionService;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.plan.batch.PlanRecalcLauncher;
import com.ottnavi.plan.exception.PlanRecalcAlreadyRunningException;
import com.ottnavi.retention.exception.RetentionAlreadyRunningException;
import com.ottnavi.retention.service.RetentionService;
import com.ottnavi.retention.service.TitleExistenceProbe;
import com.ottnavi.retention.service.model.TitleExistence;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 데이터 정리 Job(Task 083)의 시작 제한(409)과 실행 흐름을 실제 DB·Batch로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 수집·재계산·정리가 겹쳐 돌아 플랜을 동시에 지우고 바꾸는 일(네 방향 모두), 세 Step 중 일부가 안 도는 일,
 * 앞 Step 실패가 뒤 Step을 막는 일.
 */
class RetentionJobIntegrationTest extends IntegrationTestSupport {

	private static final long ID_BASE = 9_100_000L; // 실제 Batch 시퀀스와 겹치지 않는 ID 대역
	private static final String COLLECT_JOB = "collectJob";
	private static final String RECALC_JOB = "planRecalcJob";

	@Autowired private RetentionLauncher retentionLauncher;
	@Autowired private PlanRecalcLauncher planRecalcLauncher;
	@Autowired private CollectExecutionService collectExecutionService;
	@Autowired private JdbcTemplate jdbcTemplate;
	@MockitoBean private TitleExistenceProbe titleExistenceProbe; // 실제 TMDB를 부르지 않는다
	@MockitoSpyBean private RetentionService retentionService;

	@BeforeEach
	void 초기화() {
		cleanUp();
		when(titleExistenceProbe.probe(any())).thenReturn(TitleExistence.UNKNOWN);
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	private void cleanUp() {
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID >= " + ID_BASE
				+ " AND JOB_EXECUTION_ID < " + (ID_BASE + 1000));
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID >= " + ID_BASE
				+ " AND JOB_EXECUTION_ID < " + (ID_BASE + 1000));
		jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID >= " + ID_BASE
				+ " AND JOB_INSTANCE_ID < " + (ID_BASE + 1000));
	}

	@Test
	void 수집이_실행_중이면_정리_시작은_409다() {
		// given
		insertRunning(1, COLLECT_JOB);

		// when, then
		assertThatThrownBy(() -> retentionLauncher.start()).isInstanceOf(RetentionAlreadyRunningException.class);
	}

	@Test
	void 재계산이_실행_중이면_정리_시작은_409다() {
		// given
		insertRunning(1, RECALC_JOB);

		// when, then
		assertThatThrownBy(() -> retentionLauncher.start()).isInstanceOf(RetentionAlreadyRunningException.class);
	}

	@Test
	void 정리가_이미_실행_중이면_정리_시작은_409다() {
		// given
		insertRunning(1, RetentionJobConfig.JOB_NAME);

		// when, then
		assertThatThrownBy(() -> retentionLauncher.start()).isInstanceOf(RetentionAlreadyRunningException.class);
	}

	@Test
	void 정리가_실행_중이면_수집과_재계산_시작은_409다() {
		// given
		insertRunning(1, RetentionJobConfig.JOB_NAME);

		// when, then
		assertThatThrownBy(() -> collectExecutionService.start(null, CollectTier.ALL, null))
				.isInstanceOf(BatchAlreadyRunningException.class);
		assertThatThrownBy(() -> planRecalcLauncher.startManual()).isInstanceOf(PlanRecalcAlreadyRunningException.class);
	}

	@Test
	void 정리_Job은_세_Step을_끝까지_돈다() throws Exception {
		// when
		long executionId = retentionLauncher.start();
		awaitFinished(executionId);

		// then
		assertThat(stepStatuses(executionId)).containsExactly("titleRetentionStep=COMPLETED", "batchHistoryPurgeStep=COMPLETED",
				"calcRunPurgeStep=COMPLETED");
	}

	@Test
	void 첫_Step이_실패해도_나머지_두_Step은_돈다() throws Exception {
		// given
		doThrow(new IllegalStateException("점검 실패")).when(retentionService).retainTitles();

		// when
		long executionId = retentionLauncher.start();
		awaitFinished(executionId);

		// then: 실패한 Step은 Batch가 다음 Step으로 넘어가며 ABANDONED로 바꾸지만 종료 코드는 FAILED로 남는다
		assertThat(stepStatuses(executionId)).containsExactly("titleRetentionStep=ABANDONED", "batchHistoryPurgeStep=COMPLETED",
				"calcRunPurgeStep=COMPLETED");
		String exitCode = jdbcTemplate.queryForObject("SELECT EXIT_CODE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ? "
				+ "AND STEP_NAME = 'titleRetentionStep'", String.class, executionId);
		assertThat(exitCode).isEqualTo("FAILED");
	}

	// 데이터 준비·조회 보조

	/** 실행 중(STARTED, 서버 기동 이후에 만들어짐)인 Job을 직접 넣는다. 수집은 조회가 요구하는 파라미터도 넣는다 */
	private void insertRunning(int offset, String jobName) {
		jdbcTemplate.update("INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) VALUES (?, 0, ?, ?)",
				ID_BASE + offset, jobName, "retention-test-" + offset);
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION (JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, STATUS) "
				+ "VALUES (?, 0, ?, ?, 'STARTED')", ID_BASE + offset, ID_BASE + offset, LocalDateTime.now().plusSeconds(1));
		if (COLLECT_JOB.equals(jobName)) {
			insertParam(offset, "targetDate", "java.time.LocalDate", "2099-01-01");
			insertParam(offset, "tier", "java.lang.String", "ALL");
		}
	}

	private void insertParam(int offset, String name, String type, String value) {
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION_PARAMS (JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_TYPE, "
				+ "PARAMETER_VALUE, IDENTIFYING) VALUES (?, ?, ?, ?, 'Y')", ID_BASE + offset, name, type, value);
	}

	/** 정리 실행이 끝날 때까지 기다린다(최대 60초) */
	private void awaitFinished(long executionId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			String status = jdbcTemplate.queryForObject("SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?",
					String.class, executionId);
			if (!"STARTING".equals(status) && !"STARTED".equals(status)) {
				return;
			}
			Thread.sleep(100);
		}
		throw new IllegalStateException("정리가 60초 안에 끝나지 않았다");
	}

	/** 실행한 Step을 시작 순서대로 "이름=상태"로 돌려준다 */
	private List<String> stepStatuses(long executionId) {
		return jdbcTemplate.queryForList("SELECT STEP_NAME || '=' || STATUS FROM BATCH_STEP_EXECUTION "
				+ "WHERE JOB_EXECUTION_ID = ? ORDER BY STEP_EXECUTION_ID", String.class, executionId);
	}
}
