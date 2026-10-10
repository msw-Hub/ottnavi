package com.ottnavi.engine.oracle;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.resultOf;
import static com.ottnavi.engine.model.Priority.WANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.engine.rule.Evaluator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알려진 모서리(문서화 테스트, 현재 동작을 고정한다): 평가기는 예산을 검사하지 않는다(D7).
 * 예산 밖 조합을 직접 넣으면, 평가기가 "정적으로 시청 완료가 불가능한 앞 시즌"을 조합과 상관없이 막힌 것으로 보고
 * 그 뒤 시즌을 TIME으로 만든다. 그런데 예산 밖 조합에서는 그 앞 시즌이 실제로 배정될 수 있어(정적 불가능 ①은 예산 안 후보가 없다는 뜻이라
 * 예산 밖 조합을 고르면 볼 수 있다) 앞 시즌은 배정이고 뒤 시즌은 TIME인, 규칙 문서를 그대로 읽은 결과(OracleAssigner)와 다른 결과가 된다.
 *
 * <p>Solver는 달마다 비용이 예산 이하인 조합만 만들어 평가기에 넣으므로(MonthCandidates) 운영 경로에는 영향이 없다.
 * 예산 안 조합에서는 정적 불가능 시즌이 절대 배정되지 않아 차이가 없다(무작위 차분 테스트가 증명한다).
 */
class KnownCornerOffBudgetTest {

	// 월 예산 10,000원. 상품 1은 20,000원이라 어떤 달에도 예산 안에서는 고를 수 없고, 상품 2는 무료다
	// 시즌 1(WANT 100분)은 상품 1로만, 시즌 2(WANT 100분)는 상품 1·2로 볼 수 있다. 월 시청 300분
	private static final PlanInput INPUT = new PlanInput(List.of(
			new WatchUnit(1, WANT, 100, Set.of(1L), false, 7L, 1),
			new WatchUnit(2, WANT, 100, Set.of(1L, 2L), false, 7L, 2)),
			List.of(product(1L, 20_000), free(2L)), 10_000, 300);

	@Test
	@DisplayName("알려진 모서리: 예산 밖 조합에서 평가기는 앞 시즌이 배정돼도 뒤 시즌을 TIME으로 만든다")
	void 알려진_모서리_예산_밖_조합() {
		Selection offBudget = Selection.of(Set.of(1L), Set.of(), Set.of()); // 0번째 달 비용 20,000원 > 예산 10,000원

		Evaluation actual = new Evaluator(INPUT).evaluate(offBudget);
		Evaluation oracle = OracleAssigner.evaluate(INPUT, offBudget);

		// 현재 동작(고정): 시즌 1은 배정되고 시즌 2는 앞 시즌이 "항상 막힘"이라 TIME
		assertThat(resultOf(actual, 1).scheduled()).isTrue();
		assertThat(resultOf(actual, 2).reason()).isEqualTo(UnitReason.TIME);
		assertThat(actual.score()).isEqualTo(2);
		// 규칙 문서를 그대로 읽은 오라클: 시즌 2도 0번째 달에 배정(시즌 1과 같은 달, 100 + 100 <= 300)
		assertThat(resultOf(oracle, 2).assignments()).containsExactly(new Assignment(0, 1L, 100));
		assertThat(oracle.score()).isEqualTo(4);
		assertThat(actual).isNotEqualTo(oracle);
	}

	@Test
	@DisplayName("알려진 모서리와 같은 입력도 예산 안 조합에서는 평가기와 오라클이 같다")
	void 예산_안_조합은_같다() {
		for (Selection selection : List.of(
				Selection.of(Set.of(), Set.of(), Set.of()),
				Selection.of(Set.of(2L), Set.of(2L), Set.of(2L)))) {
			assertThat(new Evaluator(INPUT).evaluate(selection)).isEqualTo(OracleAssigner.evaluate(INPUT, selection));
		}
		// 시즌 1은 예산 안에서 볼 수 없으므로 어떤 예산 안 조합에서도 미배정(BUDGET)이고 시즌 2는 TIME이다
		Evaluation inBudget = new Evaluator(INPUT).evaluate(Selection.of(Set.of(), Set.of(), Set.of()));
		assertThat(resultOf(inBudget, 1).reason()).isEqualTo(UnitReason.BUDGET);
		assertThat(resultOf(inBudget, 2).reason()).isEqualTo(UnitReason.TIME);
	}
}
