package com.ottnavi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.collect.job.CollectExecutionRecord;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.http.MediaType;

/**
 * Task 045 단계 4: 수집 실행 요청 API(POST /api/admin/collect/executions)를 시험한다(Docker 필요).
 * 비동기 실행은 시험 끝에서 기다려 정리한다.
 */
class AdminCollectCommandApiTest extends AdminCollectApiTestSupport {

	private static final String DAY = "2026-03-10";
	private static final String NEXT_DAY = "2026-03-11";

	@Test
	void 토큰_없이_실행하면_401_UNAUTHORIZED다() throws Exception {
		// given, when, then
		mockMvc.perform(post(EXECUTIONS_PATH).contentType(MediaType.APPLICATION_JSON)
						.content(createBody(DAY, "ALL")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void 틀린_토큰은_401이다() throws Exception {
		// given, when, then
		mockMvc.perform(post(EXECUTIONS_PATH).header("Authorization", "Bearer wrong-token")
						.contentType(MediaType.APPLICATION_JSON).content(createBody(DAY, "ALL")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void 토큰_없이_공개_API는_200이다() throws Exception {
		// given, when, then
		mockMvc.perform(get("/api/public/ott-services")).andExpect(status().isOk());
	}

	@Test
	void 토큰으로_실행하면_202와_executionId를_주고_완료된다() throws Exception {
		// given
		long executionId;

		// when
		var result = postCreate(createBody(DAY, "ALL"));
		result.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.executionId").isNumber());
		executionId = executionIdOf(result);
		awaitFinished(executionId);

		// then
		CollectExecutionRecord record = collectExecutionQuery.findById(executionId).orElseThrow();
		assertThat(record.batchStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(record.targetDate()).isEqualTo(LocalDate.parse(DAY));
	}

	@Test
	void scope_AVAILABILITY_TV는_같은_날짜_전체_수집이_완료돼도_시작되고_판정_Step만_실행한다() throws Exception {
		// given: 같은 날짜의 전체 수집을 먼저 완료시킨다
		var full = postCreate(createBody(DAY, "ALL"));
		full.andExpect(status().isAccepted());
		awaitFinished(executionIdOf(full));

		// when
		var scoped = postCreate(createBody(DAY, "ALL", "AVAILABILITY_TV"));
		scoped.andExpect(status().isAccepted());
		long scopedId = executionIdOf(scoped);
		awaitFinished(scopedId);

		// then: 후보·상세 Step은 실행 기록이 없고 시즌 판정 Step만 있다
		assertThat(collectExecutionQuery.findById(scopedId).orElseThrow().batchStatus()).isEqualTo(BatchStatus.COMPLETED);
		List<String> stepNames = jdbcTemplate.queryForList(
				"SELECT STEP_NAME FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?", String.class, scopedId);
		assertThat(stepNames).containsExactly("tvAvailabilityStep");

		// then: 같은 scope를 한 번 더 요청하면 이미 완료로 409이다
		postCreate(createBody(DAY, "ALL", "AVAILABILITY_TV"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_COMPLETED"));
	}

	@Test
	void 실행_중에_같은_날짜를_다시_요청하면_409_BATCH_ALREADY_RUNNING이다() throws Exception {
		// given: 제공처 목록 응답을 1.5초 늦춰 첫 요청이 아직 실행 중이게 한다
		stubSuccessfulCollection(1500);
		postCreate(createBody(DAY, "ALL")).andExpect(status().isAccepted());

		// when, then
		postCreate(createBody(DAY, "ALL"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_RUNNING"));
	}

	@Test
	void 실행_중에_다른_날짜를_요청하면_409이고_detail에_실행_중인_날짜가_있다() throws Exception {
		// given
		stubSuccessfulCollection(1500);
		postCreate(createBody(DAY, "ALL")).andExpect(status().isAccepted());

		// when, then
		postCreate(createBody(NEXT_DAY, "ALL"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_RUNNING"))
				.andExpect(jsonPath("$.detail").value(containsString(DAY)));
	}

	@Test
	void 완료된_날짜를_다시_요청하면_409_BATCH_ALREADY_COMPLETED다() throws Exception {
		// given
		long executionId = executionIdOf(postCreate(createBody(DAY, "ALL")).andExpect(status().isAccepted()));
		awaitFinished(executionId);

		// when, then
		postCreate(createBody(DAY, "ALL"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_COMPLETED"));
	}

	@Test
	void 로컬에서는_다른_과거_targetDate로_202다() throws Exception {
		// given
		long first = executionIdOf(postCreate(createBody(DAY, "ALL")).andExpect(status().isAccepted()));
		awaitFinished(first);

		// when
		var result = postCreate(createBody(NEXT_DAY, "ALL"));

		// then
		result.andExpect(status().isAccepted());
		long second = executionIdOf(result);
		assertThat(second).isGreaterThan(first);
		awaitFinished(second);
	}

	@Test
	void 미래_날짜는_400_VALIDATION_FAILED_targetDate다() throws Exception {
		// given
		String tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString();

		// when, then
		postCreate(createBody(tomorrow, "ALL"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("targetDate"));
	}

	@Test
	void DAILY와_WEEKLY도_202로_시작하고_같은_날짜의_ALL과_별개의_실행이다() throws Exception {
		// given: 같은 날짜의 ALL을 먼저 완료시킨다
		awaitFinished(executionIdOf(postCreate(createBody(DAY, "ALL")).andExpect(status().isAccepted())));

		// when: 같은 날짜의 DAILY를 요청한다
		var daily = postCreate(createBody(DAY, "DAILY"));
		daily.andExpect(status().isAccepted());
		long dailyId = executionIdOf(daily);
		awaitFinished(dailyId);

		// then: ALL이 완료돼도 막히지 않고 끝까지 돈다. 그 뒤 WEEKLY도 별개의 실행이다
		assertThat(collectExecutionQuery.findById(dailyId).orElseThrow().batchStatus()).isEqualTo(BatchStatus.COMPLETED);
		long weeklyId = executionIdOf(postCreate(createBody(DAY, "WEEKLY")).andExpect(status().isAccepted()));
		awaitFinished(weeklyId);
		assertThat(collectExecutionQuery.findById(weeklyId).orElseThrow().batchStatus()).isEqualTo(BatchStatus.COMPLETED);

		// then: 같은 tier를 한 번 더 요청하면 이미 완료로 409이다
		postCreate(createBody(DAY, "DAILY"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_COMPLETED"));
	}

	@Test
	void 모르는_tier는_400_VALIDATION_FAILED_tier다() throws Exception {
		// given, when, then
		postCreate(createBody(DAY, "MONTHLY"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("tier"));
	}

	@Test
	void ALL이_실행_중이면_같은_날짜의_DAILY도_409_BATCH_ALREADY_RUNNING이다() throws Exception {
		// given: 제공처 목록 응답을 늦춰 ALL이 아직 실행 중이게 한다
		stubSuccessfulCollection(1500);
		postCreate(createBody(DAY, "ALL")).andExpect(status().isAccepted());

		// when, then: tier가 달라도 수집은 동시에 하나만 돈다
		postCreate(createBody(DAY, "DAILY"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_RUNNING"));
	}

	@Test
	void tier가_없으면_400이다() throws Exception {
		// given, when, then
		postCreate(createBody(DAY, null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("tier"));
	}

	@Test
	void 날짜_형식이_틀리면_400_fieldErrors_targetDate다() throws Exception {
		// given, when, then
		postCreate(createBody("2026-13-45", "ALL"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("targetDate"));
	}

	@Test
	void 깨진_JSON은_400_INVALID_REQUEST다() throws Exception {
		// given, when, then
		postCreate("{\"targetDate\": ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
	}

	@Test
	void 서로_다른_날짜_두_요청이_동시에_와도_하나만_202다() throws Exception {
		// given: 첫 요청이 아직 실행 중일 때 두 번째가 사전 검사를 하도록 제공처 목록 응답을 늦춘다
		stubSuccessfulCollection(1500);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		int[] statuses = new int[2];
		String[] bodies = new String[2];
		String[] dates = {DAY, NEXT_DAY};
		Thread[] threads = new Thread[2];
		for (int i = 0; i < 2; i++) {
			final int index = i;
			threads[i] = new Thread(() -> {
				try {
					ready.countDown();
					go.await();
					var result = postCreate(createBody(dates[index], "ALL")).andReturn().getResponse();
					statuses[index] = result.getStatus();
					bodies[index] = result.getContentAsString();
				} catch (Exception e) {
					statuses[index] = -1;
				}
			});
			threads[i].start();
		}

		// when
		ready.await();
		go.countDown();
		threads[0].join();
		threads[1].join();

		// then
		int accepted = 0;
		int conflict = 0;
		for (int status : statuses) {
			if (status == 202) {
				accepted++;
			}
			if (status == 409) {
				conflict++;
			}
		}
		assertThat(accepted).isEqualTo(1);
		assertThat(conflict).isEqualTo(1);
		for (int i = 0; i < 2; i++) {
			if (statuses[i] == 409) {
				assertThat(bodies[i]).contains("BATCH_ALREADY_RUNNING");
			}
		}
	}

	@Test
	void 재계산이_실행_중이면_수집_시작은_409이고_detail에_재계산이_원인임이_나온다() throws Exception {
		// given: 재계산 Job이 실행 중인 기록을 만든다(생성 시각이 서버 기동 이후라 실행 중으로 본다)
		long instanceId = jdbcTemplate.queryForObject("SELECT nextval('BATCH_JOB_INSTANCE_SEQ')", Long.class);
		long executionId = jdbcTemplate.queryForObject("SELECT nextval('BATCH_JOB_EXECUTION_SEQ')", Long.class);
		LocalDateTime startedAt = LocalDateTime.now().plusMinutes(1); // JVM 기본 시간대 기준(Batch 시각과 같은 기준)
		jdbcTemplate.update("INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) VALUES (?, 0, 'planRecalcJob', 'test-recalc')", instanceId);
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION (JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, START_TIME, STATUS, EXIT_CODE) "
				+ "VALUES (?, 0, ?, ?, ?, 'STARTED', 'EXECUTING')", executionId, instanceId, startedAt, startedAt);

		try {
			// when, then: 수집 시작은 재계산이 끝나기 전까지 409 BATCH_ALREADY_RUNNING이다
			postCreate(createBody(DAY, "ALL"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_RUNNING"))
					.andExpect(jsonPath("$.detail").value(containsString("재계산")));
		} finally {
			// 다음 테스트가 실행 중인 기록을 기다리지 않도록 끝난 것으로 바꾼다
			jdbcTemplate.update("UPDATE BATCH_JOB_EXECUTION SET STATUS = 'COMPLETED', END_TIME = now() WHERE JOB_EXECUTION_ID = ?", executionId);
		}
	}
}
