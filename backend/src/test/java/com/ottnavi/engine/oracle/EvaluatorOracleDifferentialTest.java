package com.ottnavi.engine.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.rule.Evaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 평가기(Evaluator)와 독립 구현 오라클(OracleAssigner)의 무작위 차분 테스트.
 * 예산 안 조합에서 두 결과(단위별 배정 여부·완료 달·이유 코드·배정 행, mustCompleted, score, totalCost)가 모두 같고
 * 둘 다 선언적 불변식을 지키는지 본다. 시드를 고정해 항상 같은 입력·조합을 쓴다.
 */
class EvaluatorOracleDifferentialTest {

	private static final long SEED = 20261010L;
	private static final int INPUT_COUNT = 400;     // 무작위 입력 수
	private static final int SELECTIONS_PER_INPUT = 250; // 입력마다 평가할 예산 안 조합 수 상한

	@Test
	@DisplayName("차분: 예산 안 조합에서 Evaluator와 OracleAssigner의 결과가 모두 같고 불변식을 지킨다")
	void 예산_안_조합_차분() {
		Random random = new Random(SEED);
		long evaluated = 0;
		long scheduledUnits = 0;
		List<String> failures = new ArrayList<>();
		for (int i = 0; i < INPUT_COUNT && failures.size() < 3; i++) {
			PlanInput input = OracleInputs.randomInput(random, 2, 4);
			Evaluator evaluator = new Evaluator(input);
			for (Selection selection : OracleInputs.selections(input, true, SELECTIONS_PER_INPUT, random)) {
				Evaluation actual = evaluator.evaluate(selection);
				Evaluation expected = OracleAssigner.evaluate(input, selection);
				evaluated++;
				scheduledUnits += expected.units().stream().filter(unit -> unit.scheduled()).count();
				List<String> problems = new ArrayList<>();
				if (!actual.equals(expected)) {
					problems.add("평가기와 오라클이 다르다\n 평가기=" + actual + "\n 오라클=" + expected);
				}
				PlanInvariants.violations(input, actual).forEach(problem -> problems.add("평가기 불변식: " + problem));
				PlanInvariants.violations(input, expected).forEach(problem -> problems.add("오라클 불변식: " + problem));
				if (!problems.isEmpty()) {
					failures.add(OracleInputs.describe(input) + "\n selection=" + selection.productIdsByMonth() + "\n" + String.join("\n", problems));
					break;
				}
			}
		}
		assertThat(failures).as("반례").isEmpty();
		assertThat(evaluated).as("평가한 조합 수").isGreaterThan(10_000);
		assertThat(scheduledUnits).as("배정된 단위가 충분히 섞여 있어야 한다").isGreaterThan(evaluated);
	}
}
