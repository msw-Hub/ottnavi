package com.ottnavi.engine.rule;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanType;
import java.util.Comparator;
import java.util.List;

/**
 * 플랜 유형 하나의 목적함수: 유형과 그 유형의 비교기(양수 = 첫 번째가 더 좋다).
 * 절약형·간편형은 추천형 점수로 점수 하한을 정하므로 추천형을 먼저 푼 뒤에 만든다(TECH 1절).
 * 구현 전 껍데기다(Task 039): recommended만 동작하고 saver·simple·scoreFloor는 구현해야 한다.
 */
public record PlanObjective(
		PlanType planType,              // 이 목적함수의 플랜 유형
		Comparator<Evaluation> comparator // 이 유형의 비교기. 양수면 첫 번째 평가 결과가 더 좋다
) {

	public static final int SCORE_FLOOR_PERCENT = 70; // 점수 하한: 추천형 점수의 70%(올림), PRD 11절 35번

	/** 값이 비어 있지 않은지 검증한다. */
	public PlanObjective {
		if (planType == null || comparator == null) {
			throw new IllegalArgumentException("플랜 유형과 비교기는 null일 수 없다");
		}
	}

	/** 추천형 목적함수를 만든다(기존 EvaluationComparator). */
	public static PlanObjective recommended(List<OttProduct> products) {
		return new PlanObjective(PlanType.RECOMMENDED, new EvaluationComparator(products));
	}

	/** 추천형 점수의 70%를 올림한 점수 하한을 돌려준다. 예: 10 → 7, 3 → 3(2.1 올림), 0 → 0. */
	public static int scoreFloor(int recommendedScore) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	/** 절약형 목적함수를 만든다. recommendedScore는 같은 입력의 추천형 결과 점수다. */
	public static PlanObjective saver(List<OttProduct> products, int recommendedScore) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	/** 간편형 목적함수를 만든다. recommendedScore는 같은 입력의 추천형 결과 점수다. */
	public static PlanObjective simple(List<OttProduct> products, int recommendedScore) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
