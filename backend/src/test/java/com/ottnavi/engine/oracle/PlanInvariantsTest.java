package com.ottnavi.engine.oracle;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.model.Priority.MAYBE;
import static com.ottnavi.engine.model.Priority.MUST;
import static com.ottnavi.engine.model.Priority.WANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 불변식 검사기 자체 검증: 일부러 규칙을 어긴 결과를 만들어 검사기가 잡아내는지 본다. */
class PlanInvariantsTest {

	private static WatchUnit season(long id, Priority priority, int minutes, long titleId, int seasonNumber, Long... productIds) {
		return new WatchUnit(id, priority, minutes, Set.of(productIds), false, titleId, seasonNumber);
	}

	private static PlanInput twoSeasons(int monthlyMinutes) {
		return new PlanInput(List.of(season(1, MAYBE, 100, 7, 1, 1L), season(2, MUST, 100, 7, 2, 1L)),
				List.of(product(1L, 10_000)), 20_000, monthlyMinutes);
	}

	private static Selection selection(Set<Long> a, Set<Long> b, Set<Long> c) {
		return Selection.of(a, b, c);
	}

	private static UnitResult done(long id, int month, int minutes) {
		return UnitResult.scheduled(id, month, List.of(new Assignment(month, 1L, minutes)));
	}

	@Test
	@DisplayName("올바른 결과는 위반이 없다")
	void 올바른_결과() {
		PlanInput input = twoSeasons(300);
		Evaluation evaluation = new Evaluation(selection(Set.of(1L), Set.of(), Set.of()), 1, 1, 10_000, List.of(done(1, 0, 100), done(2, 0, 100)));

		assertThat(PlanInvariants.violations(input, evaluation)).isEmpty();
	}

	@Test
	@DisplayName("앞 시즌이 미배정인데 뒤 시즌이 배정이면 잡는다")
	void 앞_시즌_미배정() {
		PlanInput input = twoSeasons(300);
		Evaluation evaluation = new Evaluation(selection(Set.of(1L), Set.of(), Set.of()), 1, 0, 10_000,
				List.of(UnitResult.unscheduled(1, UnitReason.TIME), done(2, 0, 100)));

		assertThat(PlanInvariants.violations(input, evaluation)).anyMatch(text -> text.contains("앞 시즌이 미배정"));
	}

	@Test
	@DisplayName("뒤 시즌이 앞 시즌보다 먼저 시청 완료되면 잡는다")
	void 완료_달_역전() {
		PlanInput input = twoSeasons(300);
		Evaluation evaluation = new Evaluation(selection(Set.of(1L), Set.of(1L), Set.of()), 1, 1, 20_000, List.of(done(1, 1, 100), done(2, 0, 100)));

		assertThat(PlanInvariants.violations(input, evaluation)).anyMatch(text -> text.contains("먼저 시청 완료"));
	}

	@Test
	@DisplayName("달 배정 분 합이 월 시청 시간을 넘으면 잡는다")
	void 월_용량_초과() {
		PlanInput input = twoSeasons(150);
		Evaluation evaluation = new Evaluation(selection(Set.of(1L), Set.of(), Set.of()), 1, 1, 10_000, List.of(done(1, 0, 100), done(2, 0, 100)));

		assertThat(PlanInvariants.violations(input, evaluation)).anyMatch(text -> text.contains("월 시청 시간을 넘는다"));
	}

	@Test
	@DisplayName("선택하지 않은 상품의 달에 배정하면 잡는다")
	void 볼_수_없는_달에_배정() {
		PlanInput input = twoSeasons(300);
		Evaluation evaluation = new Evaluation(selection(Set.of(1L), Set.of(), Set.of()), 1, 1, 10_000, List.of(done(1, 0, 100), done(2, 1, 100)));

		assertThat(PlanInvariants.violations(input, evaluation)).anyMatch(text -> text.contains("볼 수 있는 상품이 없다"));
	}

	@Test
	@DisplayName("점수·비용·이유 코드가 규칙과 다르면 잡는다")
	void 점수_비용_이유_코드() {
		PlanInput input = new PlanInput(List.of(new WatchUnit(1, WANT, 100, Set.of(1L), false), new WatchUnit(2, WANT, 100, Set.of(2L), false)),
				List.of(product(1L, 10_000), free(2L)), 20_000, 300);
		// 단위 2는 FREE 상품이라 볼 수 있는데 BUDGET으로 적고, 점수·비용도 틀리게 적었다
		Evaluation evaluation = new Evaluation(selection(Set.of(1L), Set.of(), Set.of()), 0, 4, 0,
				List.of(done(1, 0, 100), UnitResult.unscheduled(2, UnitReason.BUDGET)));

		List<String> problems = PlanInvariants.violations(input, evaluation);

		assertThat(problems).anyMatch(text -> text.contains("이유 코드가 판정 순서와 다르다"));
		assertThat(problems).anyMatch(text -> text.contains("score가 재계산 값과 다르다"));
		assertThat(problems).anyMatch(text -> text.contains("totalCost가 재계산 값과 다르다"));
	}
}
