package com.ottnavi.plan.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.plan.domain.enums.PlanChangeReasonType;
import com.ottnavi.plan.domain.enums.PlanTitleChangeType;
import com.ottnavi.plan.domain.rule.PlanChangeEvidence.AvailabilityShift;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Month;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Placement;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Product;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Unit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 이전·새 플랜 비교로 변경 요약을 만드는 규칙(Task 069)을 확인한다.
 * 막으려는 사고: 새 달만 생겼는데 기록이 남는 일, 이유 없이 기록이 남는 일, 이미 지난 달의 작품이 변화로 잡히는 일,
 * 이유 4종이 엉뚱한 조건에서 만들어지는 일.
 * 기본 상황: 이전 플랜 10~12월, 새 플랜 11~1월. 겹치는 달은 11·12월, 새 달은 1월, 10월은 이미 지난 달이다.
 */
class PlanChangeDetectorTest {

	private static final LocalDate OCT = LocalDate.of(2026, 10, 1);
	private static final LocalDate NOV = LocalDate.of(2026, 11, 1);
	private static final LocalDate DEC = LocalDate.of(2026, 12, 1);
	private static final LocalDate JAN = LocalDate.of(2027, 1, 1);
	private static final Instant PREVIOUS_AS_OF = Instant.parse("2026-10-01T00:00:00Z");
	private static final Instant AFTER_AS_OF = Instant.parse("2026-10-20T00:00:00Z");

	private static final Product NETFLIX = new Product(1L, List.of(1L), "넷플릭스 스탠다드", 13_500);
	private static final Product DISNEY = new Product(6L, List.of(5L), "디즈니+ 스탠다드", 9_900);
	private static final Map<Long, String> NAMES = Map.of(1L, "넷플릭스", 5L, "디즈니+");

	@Test
	void 달의_구독_상품이_바뀌면_달_변화와_요약이_만들어지고_새_달도_함께_담긴다() {
		// given: 11월이 넷플릭스에서 디즈니+로 바뀌고, 카지노 시즌 1은 넷플릭스에서 빠져 서비스를 옮겼다
		PlanSnapshot previous = previous(List.of(unit(10L, "카지노", 1, NOV, 1L)));
		PlanSnapshot current = current(months(List.of(DISNEY), List.of(NETFLIX), List.of(NETFLIX)),
				List.of(unit(10L, "카지노", 1, NOV, 5L)));
		PlanChangeEvidence evidence = evidence(List.of(shift(10L, 1L, true, false)), Set.of());

		// when
		Optional<PlanChangeSummary> result = PlanChangeDetector.detect(previous, current, evidence);

		// then: 겹치는 달 변화 1건 + 작품 변화 1건 → 요약은 첫 달 변화 + 외 1건, 새 달(1월)은 이전 비용 null로 포함
		assertThat(result).isPresent();
		PlanChangeSummary summary = result.get();
		assertThat(summary.summary()).isEqualTo("11월 구독이 넷플릭스에서 디즈니+로 바뀌었어요 (외 1건)");
		assertThat(summary.monthChanges()).extracting(PlanChangeSummary.MonthChange::month)
				.containsExactly("2026-11", "2027-01");
		assertThat(summary.monthChanges().get(0).previousCost()).isEqualTo(13_500);
		assertThat(summary.monthChanges().get(1).previousCost()).isNull();
		assertThat(summary.monthChanges().get(1).previousProducts()).isEmpty();
		assertThat(summary.titleChanges()).singleElement()
				.satisfies(change -> assertThat(change.changeType()).isEqualTo(PlanTitleChangeType.CHANGED_SERVICE));
		assertThat(summary.reasons()).extracting(PlanChangeSummary.Reason::reasonType)
				.containsExactly(PlanChangeReasonType.TITLE_LEFT_SERVICE);
	}

	@Test
	void 새_달만_생기고_다른_변화가_없으면_이유가_있어도_기록하지_않는다() {
		// given: 겹치는 달은 그대로, 1월만 새로 생겼다. 다 봤음 체크가 있어 이유 후보는 있다
		PlanSnapshot previous = previous(List.of(unit(10L, "카지노", 1, NOV, 1L)));
		PlanSnapshot current = current(months(List.of(NETFLIX), List.of(NETFLIX), List.of(NETFLIX)),
				List.of(unit(10L, "카지노", 1, NOV, 1L)));
		PlanChangeEvidence evidence = evidence(List.of(), Set.of(10L));

		// when
		Optional<PlanChangeSummary> result = PlanChangeDetector.detect(previous, current, evidence);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	void 이전_자리가_이미_지난_달인_작품은_변화로_세지_않는다() {
		// given: 11월 구독은 바뀌었다. 작품 11은 이전에 10월(지난 달)에 보기로 했고 새 플랜에는 없다
		PlanSnapshot previous = previous(List.of(unit(10L, "카지노", 1, NOV, 1L), unit(11L, "무빙", null, OCT, 1L)));
		PlanSnapshot current = current(months(List.of(DISNEY), List.of(NETFLIX), List.of(NETFLIX)),
				List.of(unit(10L, "카지노", 1, NOV, 1L)));
		PlanChangeEvidence evidence = evidence(List.of(shift(10L, 1L, true, false)), Set.of(11L));

		// when
		PlanChangeSummary summary = PlanChangeDetector.detect(previous, current, evidence).orElseThrow();

		// then: 무빙은 작품 변화에도 다 봤음 이유에도 나오지 않는다(다 봤음은 지난 달 자리라 겹치는 달이 아니다)
		assertThat(summary.titleChanges()).isEmpty();
		assertThat(summary.reasons()).extracting(PlanChangeSummary.Reason::reasonType)
				.containsExactly(PlanChangeReasonType.TITLE_LEFT_SERVICE);
	}

	@Test
	void 빠짐과_들어옴은_가장_이른_이전_상태와_가장_늦은_이후_상태로_판정한다() {
		// given: 카지노 시즌 1이 넷플릭스에서 빠지고 디즈니+에 들어왔다. 이전 플랜 기준시각 이전의 변경은 무시된다
		PlanSnapshot previous = previous(List.of(unit(10L, "카지노", 1, NOV, 1L)));
		PlanSnapshot current = current(months(List.of(DISNEY), List.of(NETFLIX), List.of(NETFLIX)),
				List.of(unit(10L, "카지노", 1, NOV, 5L)));
		List<AvailabilityShift> shifts = new ArrayList<>();
		shifts.add(shift(10L, 1L, true, false));
		shifts.add(shift(10L, 5L, false, true));
		shifts.add(new AvailabilityShift(10L, 6L, true, false, PREVIOUS_AS_OF.minusSeconds(60))); // 이전 플랜 전의 변경
		PlanChangeEvidence evidence = evidence(shifts, Set.of());

		// when
		PlanChangeSummary summary = PlanChangeDetector.detect(previous, current, evidence).orElseThrow();

		// then
		assertThat(summary.reasons()).extracting(PlanChangeSummary.Reason::description).containsExactly(
				"'카지노 시즌 1'이 넷플릭스에서 빠진 것으로 확인됐어요",
				"'카지노 시즌 1'이 디즈니+에서 볼 수 있는 것으로 확인됐어요");
	}

	@Test
	void 이전_플랜_저장_뒤_다_봤음으로_체크한_작품은_다_봤음_이유가_된다() {
		// given: 오징어 게임이 이전 플랜에서 11월에 배정돼 있었고 다 봤음으로 체크되어 새 플랜에서 빠졌다
		PlanSnapshot previous = previous(List.of(unit(12L, "오징어 게임", null, NOV, 1L)));
		PlanSnapshot current = current(months(List.of(NETFLIX), List.of(NETFLIX), List.of(NETFLIX)), List.of());
		PlanChangeEvidence evidence = evidence(List.of(), Set.of(12L));

		// when
		PlanChangeSummary summary = PlanChangeDetector.detect(previous, current, evidence).orElseThrow();

		// then
		assertThat(summary.summary()).isEqualTo("'오징어 게임'을 플랜에서 뺐어요");
		assertThat(summary.reasons()).singleElement().satisfies(reason -> {
			assertThat(reason.reasonType()).isEqualTo(PlanChangeReasonType.MARKED_WATCHED);
			assertThat(reason.description()).isEqualTo("'오징어 게임'을 다 봤음으로 체크해 계산에서 뺐어요");
		});
	}

	@Test
	void 이전_플랜에_있던_상품의_가격이_달라지면_요금_변경_이유가_된다() {
		// given: 넷플릭스 스탠다드 요금이 13,500원에서 14,500원으로 올랐다(11월 상품 집합은 그대로)
		Product raised = new Product(1L, List.of(1L), "넷플릭스 스탠다드", 14_500);
		PlanSnapshot previous = previous(List.of());
		PlanSnapshot current = current(months(List.of(raised), List.of(NETFLIX), List.of(NETFLIX)), List.of());
		PlanChangeEvidence evidence = evidence(List.of(), Set.of());

		// when
		PlanChangeSummary summary = PlanChangeDetector.detect(previous, current, evidence).orElseThrow();

		// then
		assertThat(summary.summary()).isEqualTo("11월 넷플릭스 구독 요금이 바뀌었어요");
		assertThat(summary.reasons()).singleElement().satisfies(reason -> {
			assertThat(reason.reasonType()).isEqualTo(PlanChangeReasonType.PRICE_CHANGED);
			assertThat(reason.description()).isEqualTo("넷플릭스 스탠다드 요금이 13,500원에서 14,500원으로 바뀌었어요");
		});
	}

	@Test
	void 변화는_있어도_이유가_하나도_없으면_기록하지_않는다() {
		// given: 11월이 이전 플랜에 없던 새 상품으로 바뀌었지만 가격·제공 상태·시청 체크 근거가 없다
		PlanSnapshot previous = previous(List.of());
		PlanSnapshot current = current(months(List.of(DISNEY), List.of(NETFLIX), List.of(NETFLIX)), List.of());
		PlanChangeEvidence evidence = evidence(List.of(), Set.of());

		// when
		Optional<PlanChangeSummary> result = PlanChangeDetector.detect(previous, current, evidence);

		// then
		assertThat(result).isEmpty();
	}

	// 픽스처

	/** 이전 플랜: 10~12월 모두 넷플릭스 */
	private static PlanSnapshot previous(List<Unit> units) {
		List<Month> months = new ArrayList<>();
		months.add(new Month(OCT, true, 13_500, List.of(NETFLIX)));
		months.add(new Month(NOV, false, 13_500, List.of(NETFLIX)));
		months.add(new Month(DEC, false, 13_500, List.of(NETFLIX)));
		return new PlanSnapshot(1L, PREVIOUS_AS_OF, PREVIOUS_AS_OF, months, units);
	}

	/** 새 플랜: 11·12·1월 */
	private static PlanSnapshot current(List<Month> months, List<Unit> units) {
		return new PlanSnapshot(2L, AFTER_AS_OF, AFTER_AS_OF, months, units);
	}

	private static List<Month> months(List<Product> nov, List<Product> dec, List<Product> jan) {
		List<Month> months = new ArrayList<>();
		months.add(new Month(NOV, true, 13_500, nov));
		months.add(new Month(DEC, false, 13_500, dec));
		months.add(new Month(JAN, false, 13_500, jan));
		return months;
	}

	private static Unit unit(long id, String title, Integer season, LocalDate month, long serviceId) {
		return new Unit(id, title, season, new Placement(month, serviceId));
	}

	private static AvailabilityShift shift(long unitId, long serviceId, boolean before, boolean after) {
		return new AvailabilityShift(unitId, serviceId, before, after, AFTER_AS_OF.minusSeconds(3_600));
	}

	private static PlanChangeEvidence evidence(List<AvailabilityShift> shifts, Set<Long> watchedUnitIds) {
		return new PlanChangeEvidence(shifts, watchedUnitIds, NAMES);
	}
}
