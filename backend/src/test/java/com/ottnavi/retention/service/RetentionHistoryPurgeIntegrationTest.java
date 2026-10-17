package com.ottnavi.retention.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.plan.domain.entity.CalcRun;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.repository.CalcRunRepository;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오래된 배치 이력(BATCH_*)과 계산 기록(calc_run) 정리(Task 083, 결정 b·c)를 실제 DB로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 기준 이내 데이터가 지워지는 일, 실행 중인 실행이 지워져 재시작이 깨지는 일, 마지막 완료 수집 이력이 지워져
 * 플랜의 데이터 기준 시각이 사라지는 일, BATCH_* FK(ON DELETE 없음) 순서가 틀려 삭제가 실패하는 일, 플랜과 연결된 calc_run이 지워지는 일.
 */
@Transactional // 테스트마다 롤백한다: 같은 DB의 다른 테스트 데이터를 지우는 정리(BATCH_*)가 밖으로 새지 않게 한다
class RetentionHistoryPurgeIntegrationTest extends IntegrationTestSupport {

	private static final long ID_BASE = 9_000_000L; // 실제 Batch 시퀀스와 겹치지 않는 ID 대역
	private static final String JOB_PREFIX = "retentionTestJob";

	@Autowired private BatchHistoryPurger batchHistoryPurger;
	@Autowired private RetentionService retentionService;
	@Autowired private CalcRunRepository calcRunRepository;
	@Autowired private PlanRepository planRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void 기준보다_오래전에_끝난_인스턴스만_하위_테이블까지_지우고_나머지는_남긴다() {
		// given: 오래된 COMPLETED(지운다), 오래된 STARTED(끝나지 않음), 최근 COMPLETED, 오래된 FAILED+최근 COMPLETED 두 실행을 가진 인스턴스, 실행 없는 인스턴스
		LocalDateTime now = LocalDateTime.now();
		insertInstance(1, JOB_PREFIX + "A");
		insertExecution(11, 1, "COMPLETED", now.minusDays(200));
		insertStep(21, 11);
		insertInstance(2, JOB_PREFIX + "B");
		insertExecution(12, 2, "STARTED", null);
		insertInstance(3, JOB_PREFIX + "C");
		insertExecution(13, 3, "COMPLETED", now.minusDays(10));
		insertInstance(4, JOB_PREFIX + "D");
		insertExecution(14, 4, "FAILED", now.minusDays(200));
		insertExecution(15, 4, "COMPLETED", now.minusDays(10));
		insertInstance(5, JOB_PREFIX + "E");

		// when
		int deleted = batchHistoryPurger.purgeEndedBefore(now.minusDays(180));

		// then: 지운 것은 인스턴스 A 하나이고 그 하위 행(실행·Step·컨텍스트·파라미터)도 모두 사라졌다
		assertThat(deleted).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID = ?", id(1))).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?", id(1))).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?", id(11))).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID = ?", id(21))).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID = ?", id(11))).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = ?", id(11))).isEqualTo(0);

		// then: 끝나지 않은 실행, 최근 실행, 일부만 오래된 인스턴스, 실행 없는 인스턴스는 그대로다
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID IN (?, ?, ?, ?)", id(2), id(3), id(4),
				id(5))).isEqualTo(4);
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID IN (?, ?, ?, ?)", id(12), id(13), id(14),
				id(15))).isEqualTo(4);
	}

	@Test
	void 기준을_넘겨도_마지막_COMPLETED_수집_실행은_남기고_그보다_오래된_수집_실행은_지운다() {
		// given: 수집 Job의 COMPLETED 실행 둘. X는 가장 늦게 끝났고 Y는 1년 전에 끝났다. 기준은 둘 다 넘긴다
		LocalDateTime now = LocalDateTime.now();
		insertInstance(1, "collectJob");
		insertExecution(11, 1, "COMPLETED", now.plusDays(1));
		insertInstance(2, "collectJob");
		insertExecution(12, 2, "COMPLETED", now.minusDays(365));

		// when: 기준을 X의 종료 시각 뒤로 잡는다
		batchHistoryPurger.purgeEndedBefore(now.plusDays(2));

		// then: 오래된 Y는 지워지고, 마지막 완료 수집 X는 데이터 기준 시각을 위해 남는다
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", id(12))).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", id(11))).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID = ?", id(1))).isEqualTo(1);
	}

	@Test
	void 플랜과_끊긴_계산_기록만_보관_기간이_지나면_지우고_플랜과_연결된_기록은_남긴다() {
		// given: 오래됐지만 플랜과 연결된 기록, 오래되고 끊긴 기록, 최근에 끊긴 기록
		User user = userRepository.save(User.create("task083-history-" + UUID.randomUUID() + "@example.com", "테스터", OAuthProvider.GOOGLE,
				UUID.randomUUID().toString().replace("-", "")));
		Plan plan = planRepository.save(Plan.createDraft(user.getId(), null, PlanType.RECOMMENDED, LocalDate.of(2026, 1, 1), 30_000,
				600, SolverType.EXACT, 1, 1, 1, 1_000, 2_000, Instant.now()));
		long linkedOld = saveCalcRun(plan.getId(), 200);
		long detachedOld = saveCalcRun(null, 200);
		long detachedRecent = saveCalcRun(null, 10);

		// when
		int deleted = retentionService.purgeCalcRuns();

		// then: 오래되고 끊긴 기록만 지워진다(다른 곳에서 남은 오래된 끊긴 기록이 있으면 함께 지워질 수 있어 1건 이상으로 본다)
		assertThat(deleted).isGreaterThanOrEqualTo(1);
		assertThat(calcRunRepository.existsById(detachedOld)).isFalse();
		assertThat(calcRunRepository.existsById(linkedOld)).isTrue();
		assertThat(calcRunRepository.existsById(detachedRecent)).isTrue();
	}

	// 데이터 준비

	private long id(int offset) {
		return ID_BASE + offset;
	}

	private void insertInstance(int offset, String jobName) {
		jdbcTemplate.update("INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) VALUES (?, 0, ?, ?)",
				id(offset), jobName, UUID.randomUUID().toString().replace("-", ""));
	}

	/** 실행 한 건과 컨텍스트·파라미터를 넣는다. endTime이 null이면 끝나지 않은 실행이다 */
	private void insertExecution(int offset, int instanceOffset, String status, LocalDateTime endTime) {
		LocalDateTime create = LocalDateTime.now().minusDays(400);
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION (JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, END_TIME, STATUS) "
				+ "VALUES (?, 0, ?, ?, ?, ?)", id(offset), id(instanceOffset), create, endTime, status);
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION_CONTEXT (JOB_EXECUTION_ID, SHORT_CONTEXT) VALUES (?, '{}')", id(offset));
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION_PARAMS (JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_TYPE, PARAMETER_VALUE, "
				+ "IDENTIFYING) VALUES (?, 'p', 'java.lang.String', 'v', 'Y')", id(offset));
	}

	private void insertStep(int offset, int executionOffset) {
		jdbcTemplate.update("INSERT INTO BATCH_STEP_EXECUTION (STEP_EXECUTION_ID, VERSION, STEP_NAME, JOB_EXECUTION_ID, CREATE_TIME) "
				+ "VALUES (?, 0, 's', ?, ?)", id(offset), id(executionOffset), LocalDateTime.now().minusDays(400));
		jdbcTemplate.update("INSERT INTO BATCH_STEP_EXECUTION_CONTEXT (STEP_EXECUTION_ID, SHORT_CONTEXT) VALUES (?, '{}')", id(offset));
	}

	/** 계산 기록을 저장하고 기록 시각을 daysAgo일 전으로 바꾼다(@CreationTimestamp가 저장 시각을 넣기 때문에 SQL로 고친다) */
	private long saveCalcRun(Long planId, int daysAgo) {
		CalcRun run = calcRunRepository.save(CalcRun.create(planId, "a".repeat(64), PlanType.RECOMMENDED, SolverType.EXACT, 1, 1, 1,
				1, 1));
		jdbcTemplate.update("UPDATE calc_run SET created_at = ? WHERE id = ?",
				java.sql.Timestamp.from(Instant.now().minus(java.time.Duration.ofDays(daysAgo))), run.getId());
		return run.getId();
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
