package com.ottnavi.engine.rule;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Selection;
import java.util.Comparator;

/**
 * 플랜 유형 하나의 목적함수: 유형, 그 유형의 비교기(양수 = 첫 번째가 더 좋다), 그리고 시작 조합.
 * 절약형·간편형은 추천형 결과로 점수 하한을 정하고 그 조합에서 출발해 개선하므로 추천형을 먼저 푼 뒤에 만든다(TECH 1절).
 */
public record PlanObjective(
		PlanType planType,                // 이 목적함수의 플랜 유형
		Comparator<Evaluation> comparator, // 이 유형의 비교기. 양수면 첫 번째 평가 결과가 더 좋다
		Selection baseline                 // 시작 조합. 추천형은 null, 절약형·간편형은 추천형 결과의 조합(그리디 개선의 출발점)
) {

	public static final int SCORE_FLOOR_PERCENT = 70; // 점수 하한: 추천형 점수의 70%(올림), PRD 11절 35번

	/** 값을 검증한다. 절약형·간편형은 시작 조합이 반드시 있어야 한다. */
	public PlanObjective {
		if (planType == null || comparator == null) {
			throw new IllegalArgumentException("플랜 유형과 비교기는 null일 수 없다");
		}
		if (planType != PlanType.RECOMMENDED && baseline == null) {
			throw new IllegalArgumentException("절약형·간편형은 시작 조합(baseline)이 필요하다: planType=" + planType);
		}
	}

	/** 추천형 목적함수를 만든다(기존 EvaluationComparator, 시작 조합 없음). */
	public static PlanObjective recommended(PlanInput input) {
		return new PlanObjective(PlanType.RECOMMENDED, new EvaluationComparator(input), null);
	}

	/** 추천형 점수의 70%를 올림한 점수 하한을 돌려준다. 예: 10 → 7, 3 → 3(2.1 올림), 0 → 0. 음수면 IllegalArgumentException. */
	public static int scoreFloor(int recommendedScore) {
		if (recommendedScore < 0) {
			throw new IllegalArgumentException("추천형 점수는 0 이상이어야 한다: recommendedScore=" + recommendedScore);
		}
		// 정수 올림 나눗셈: ceil(score × 70 / 100) = (score × 70 + 99) / 100. 안내서의 (score × 7 + 9) / 10과 값이 같다
		return (recommendedScore * SCORE_FLOOR_PERCENT + 99) / 100;
	}

	/** 절약형 목적함수를 만든다. 점수 하한은 recommended.score()로 정하고 recommended.selection()을 시작 조합으로 담는다. */
	public static PlanObjective saver(PlanInput input, Evaluation recommended) {
		requireRecommended(recommended);
		return new PlanObjective(PlanType.SAVER, new SaverComparator(input, scoreFloor(recommended.score())), recommended.selection());
	}

	/** 간편형 목적함수를 만든다. 점수 하한은 recommended.score()로 정하고 recommended.selection()을 시작 조합으로 담는다. */
	public static PlanObjective simple(PlanInput input, Evaluation recommended) {
		requireRecommended(recommended);
		return new PlanObjective(PlanType.SIMPLE, new SimpleComparator(input, scoreFloor(recommended.score())), recommended.selection());
	}

	/** 추천형 결과가 있는지 확인한다(절약형·간편형은 그 점수와 조합이 필요하다). */
	private static void requireRecommended(Evaluation recommended) {
		if (recommended == null) {
			throw new IllegalArgumentException("절약형·간편형을 만들려면 추천형 결과가 필요하다");
		}
	}
}
