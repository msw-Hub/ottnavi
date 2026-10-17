package com.ottnavi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.admin.service.CollectExecutionQueryService;
import com.ottnavi.collect.job.CollectJobLauncher;
import com.ottnavi.collect.job.CollectTier;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Task 045 단계 4: 운영 모드(대상 날짜 직접 지정 금지)에서 날짜 규칙을 시험한다(Docker 필요).
 */
@TestPropertySource(properties = "app.collect.allow-target-date-override=false")
class AdminCollectProdModeApiTest extends AdminCollectApiTestSupport {

	@Autowired
	private CollectJobLauncher launcher;

	@Autowired
	private CollectExecutionQueryService queryService;

	@Test
	void 운영_모드에서_어제_날짜로_실행하면_400_fieldErrors_targetDate다() throws Exception {
		// given
		String yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString();

		// when, then
		postCreate(createBody(yesterday, "ALL"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("targetDate"));
	}

	@Test
	void 운영_모드에서_scope를_주면_400_fieldErrors_scope다() throws Exception {
		// given, when, then
		postCreate(createBody(null, "ALL", "AVAILABILITY_TV"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("scope"));
	}

	@Test
	void 운영_모드에서_날짜를_비우면_오늘로_202다() throws Exception {
		// given, when
		var result = postCreate(createBody(null, "ALL"));

		// then
		result.andExpect(status().isAccepted());
		long executionId = executionIdOf(result);
		awaitFinished(executionId);
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		assertThat(collectExecutionQuery.findById(executionId).orElseThrow().targetDate()).isEqualTo(today);
	}

	@Test
	void 운영_모드에서_어제_실패한_실행을_재시작하면_400이다() throws Exception {
		// given: 시작 API는 어제를 거절하므로 launcher로 직접 어제 실행을 만들어 실패시킨다
		long failedId = failYesterdayExecution();

		// when, then
		postAction(failedId, "restart")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("targetDate"));
	}

	@Test
	void 운영_모드에서_어제_실패의_canRestart는_거짓이다() throws Exception {
		// given
		long failedId = failYesterdayExecution();

		// when, then
		assertThat(queryService.getExecution(failedId).canRestart()).isFalse();
	}

	private long failYesterdayExecution() throws Exception {
		stubFailingCollection();
		LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
		long executionId = launcher.start(yesterday, CollectTier.ALL);
		awaitFinished(executionId);
		return executionId;
	}
}
