package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 초안이 오래됐는지 판정하는 규칙(Task 066)을 확인한다.
 * 막으려는 사고: 수집이 새로 끝났는데 옛 데이터로 계산한 초안이 저장되는 일, 경계(같은 시각)나 수집 이력이 없을 때 저장이 막히는 일.
 */
class PlanActivateServiceTest {

	private static final Instant T = Instant.parse("2026-10-15T00:00:00Z");

	@Test
	void 최신_수집이_초안_기준보다_늦으면_오래된_초안이다() {
		assertThat(PlanActivateService.isStale(T, Optional.of(T.plusSeconds(1)))).isTrue();
	}

	@Test
	void 최신_수집_시각과_같으면_통과한다() {
		assertThat(PlanActivateService.isStale(T, Optional.of(T))).isFalse();
	}

	@Test
	void 초안이_더_최신이면_통과한다() {
		assertThat(PlanActivateService.isStale(T.plusSeconds(1), Optional.of(T))).isFalse();
	}

	@Test
	void 완료된_수집이_없으면_통과한다() {
		assertThat(PlanActivateService.isStale(T, Optional.empty())).isFalse();
	}
}
