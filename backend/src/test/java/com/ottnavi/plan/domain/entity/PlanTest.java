package com.ottnavi.plan.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.plan.domain.enums.PlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 재계산으로 만드는 플랜의 생성·변경 요약 기록을 확인한다.
 * 막으려는 사고: 재계산 플랜이 DRAFT로 남거나 저장 시각이 비는 일(DB CHECK 위반), 변경 요약이 덮어써지거나 DRAFT에 남는 일.
 */
class PlanTest {

	private static final Instant NOW = Instant.parse("2026-11-01T03:50:00Z");

	@Test
	void 재계산_플랜은_ACTIVE이고_저장_시각과_이전_플랜이_채워지며_변경_요약은_한_번만_기록된다() {
		// when
		Plan plan = recalculated(100L);
		plan.recordChangeSummary("{\"v\":1}");

		// then
		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
		assertThat(plan.getActivatedAt()).isEqualTo(NOW);
		assertThat(plan.getPreviousPlanId()).isEqualTo(100L);
		assertThat(plan.getChangeSummary()).isEqualTo("{\"v\":1}");
		assertThatThrownBy(() -> plan.recordChangeSummary("{\"v\":1}")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 이전_플랜이_없는_재계산_플랜과_DRAFT의_변경_요약은_거절한다() {
		assertThatThrownBy(() -> recalculated(null)).isInstanceOf(IllegalArgumentException.class);
		Plan draft = Plan.createDraft(1L, null, PlanType.RECOMMENDED, LocalDate.of(2026, 11, 1), 30_000, 1_200,
				SolverType.EXACT, 3, 3, 90, 13_500, 40_500, NOW);
		assertThatThrownBy(() -> draft.recordChangeSummary("{\"v\":1}")).isInstanceOf(IllegalStateException.class);
	}

	private static Plan recalculated(Long previousPlanId) {
		return Plan.createRecalculated(1L, previousPlanId, PlanType.RECOMMENDED, LocalDate.of(2026, 11, 1), 30_000,
				1_200, SolverType.EXACT, 3, 3, 90, 13_500, 40_500, NOW, NOW);
	}
}
