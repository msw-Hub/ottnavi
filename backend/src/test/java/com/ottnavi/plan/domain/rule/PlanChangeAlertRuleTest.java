package com.ottnavi.plan.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.plan.domain.rule.PlanChangeAlertRule.LeftTitle;
import com.ottnavi.plan.domain.rule.PlanChangeEvidence.AvailabilityShift;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Placement;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Unit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 변경 알림(PLAN_CHANGE) 대상 판정을 확인한다(Task 073).
 * 막으려는 사고: 다음 달 작품이 빠졌는데 알림이 나가는 일, 다른 서비스에 들어온 것을 빠짐으로 세는 일,
 * 이전 플랜 이전에 있던 변경을 이번 변경으로 세는 일.
 * 기본 상황: 지금 11월, 이전 플랜의 카지노는 11월 서비스 1에서, 오징어게임은 12월 서비스 1에서 끝난다.
 */
class PlanChangeAlertRuleTest {

	private static final LocalDate NOV = LocalDate.of(2026, 11, 1);
	private static final LocalDate DEC = LocalDate.of(2026, 12, 1);
	private static final Instant PREVIOUS_AS_OF = Instant.parse("2026-10-01T00:00:00Z");
	private static final Instant AFTER_AS_OF = Instant.parse("2026-10-20T00:00:00Z");
	private static final Instant BEFORE_AS_OF = Instant.parse("2026-09-20T00:00:00Z");

	@Test
	void 이번_달_확정_달에_배정된_작품이_그_서비스에서_빠지면_빠진_작품으로_돌려준다() {
		// given
		PlanSnapshot previous = previous();
		PlanChangeEvidence evidence = evidence(List.of(shift(10L, 1L, true, false, AFTER_AS_OF)));

		// when
		List<LeftTitle> left = PlanChangeAlertRule.findLeftTitles(previous, NOV, evidence);

		// then
		assertThat(left).hasSize(1);
		assertThat(left.get(0).watchUnitId()).isEqualTo(10L);
		assertThat(left.get(0).title()).isEqualTo("카지노");
		assertThat(left.get(0).ottServiceId()).isEqualTo(1L);
	}

	@Test
	void 다음_달_작품만_빠지면_알림_대상이_아니다() {
		// given: 12월에 끝나는 오징어게임만 서비스 1에서 빠졌다
		PlanChangeEvidence evidence = evidence(List.of(shift(11L, 1L, true, false, AFTER_AS_OF)));

		// when
		List<LeftTitle> left = PlanChangeAlertRule.findLeftTitles(previous(), NOV, evidence);

		// then
		assertThat(left).isEmpty();
	}

	@Test
	void 배정된_서비스가_아니라_다른_서비스에_들어오기만_했으면_알림_대상이_아니다() {
		// given: 카지노가 서비스 5에 새로 들어왔다(서비스 1에서는 그대로 있음)
		PlanChangeEvidence evidence = evidence(List.of(shift(10L, 5L, false, true, AFTER_AS_OF)));

		// when
		List<LeftTitle> left = PlanChangeAlertRule.findLeftTitles(previous(), NOV, evidence);

		// then
		assertThat(left).isEmpty();
	}

	@Test
	void 이전_플랜_저장_이전의_변경은_세지_않는다() {
		// given: 빠짐이 이전 플랜의 데이터 기준 시각보다 앞선 일이다(이미 이전 플랜에 반영됨)
		PlanChangeEvidence evidence = evidence(List.of(shift(10L, 1L, true, false, BEFORE_AS_OF)));

		// when
		List<LeftTitle> left = PlanChangeAlertRule.findLeftTitles(previous(), NOV, evidence);

		// then
		assertThat(left).isEmpty();
	}

	@Test
	void 빠졌다가_다시_돌아온_작품은_빠진_것이_아니다() {
		// given: 있음 → 없음 → 있음
		PlanChangeEvidence evidence = evidence(List.of(
				shift(10L, 1L, true, false, AFTER_AS_OF),
				shift(10L, 1L, false, true, AFTER_AS_OF.plusSeconds(3_600))));

		// when
		List<LeftTitle> left = PlanChangeAlertRule.findLeftTitles(previous(), NOV, evidence);

		// then
		assertThat(left).isEmpty();
	}

	private static PlanSnapshot previous() {
		List<Unit> units = List.of(
				new Unit(10L, "카지노", null, new Placement(NOV, 1L)),
				new Unit(11L, "오징어게임", 2, new Placement(DEC, 1L)),
				new Unit(12L, "배정 안 됨", null, null));
		return new PlanSnapshot(1L, PREVIOUS_AS_OF, PREVIOUS_AS_OF, List.of(), units);
	}

	private static PlanChangeEvidence evidence(List<AvailabilityShift> shifts) {
		return new PlanChangeEvidence(shifts, Set.of(), Map.of(1L, "넷플릭스"));
	}

	private static AvailabilityShift shift(long unitId, long serviceId, boolean before, boolean after, Instant changedAt) {
		return new AvailabilityShift(unitId, serviceId, before, after, changedAt);
	}
}
