package com.ottnavi.engine.rule;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Selection;
import java.util.Comparator;
import java.util.List;

/**
 * 플랜 유형 하나의 목적함수: 유형, 그 유형의 비교기(양수 = 첫 번째가 더 좋다), 그리고 시작 조합.
 * 절약형·간편형은 추천형 결과로 점수 하한을 정하고 그 조합에서 출발해 개선하므로 추천형을 먼저 푼 뒤에 만든다(TECH 1절).
 * 구현 전 껍데기다(Task 039): recommended만 동작하고 saver·simple·scoreFloor는 구현해야 한다.
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
	public static PlanObjective recommended(List<OttProduct> products) {
		return new PlanObjective(PlanType.RECOMMENDED, new EvaluationComparator(products), null);
	}

	/** 추천형 점수의 70%를 올림한 점수 하한을 돌려준다. 예: 10 → 7, 3 → 3(2.1 올림), 0 → 0. 음수면 IllegalArgumentException. */
	public static int scoreFloor(int recommendedScore) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	/** 절약형 목적함수를 만든다. 점수 하한은 recommended.score()로 정하고 recommended.selection()을 시작 조합으로 담는다. */
	public static PlanObjective saver(List<OttProduct> products, Evaluation recommended) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	/** 간편형 목적함수를 만든다. 점수 하한은 recommended.score()로 정하고 recommended.selection()을 시작 조합으로 담는다. */
	public static PlanObjective simple(List<OttProduct> products, Evaluation recommended) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
