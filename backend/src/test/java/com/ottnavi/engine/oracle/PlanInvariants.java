package com.ottnavi.engine.oracle;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 선언적 불변식 검사기: 평가 결과(Evaluator·OracleAssigner 어느 쪽이든)가 규칙 문서의 성질을 지키는지 본다.
 * 배정 알고리즘을 따라 하지 않고 "결과가 만족해야 하는 조건"만 하나씩 확인한다. 위반 설명 목록을 돌려주고 비어 있으면 통과다.
 * 단언 라이브러리 대신 목록을 돌려주는 것은 수만 번 호출해도 느려지지 않게 하려는 것이다.
 */
public final class PlanInvariants {

	private static final int MIN_PIECE_MINUTES = 120;

	private PlanInvariants() {
	}

	/** 위반 사항을 모아 돌려준다. 예산 안 조합이든 밖이든 같은 조건을 본다(시즌 순서 조건은 사슬 판정이 평가기에 의존하므로 예산 안에서만 의미가 있다). */
	public static List<String> violations(PlanInput input, Evaluation evaluation) {
		List<String> problems = new ArrayList<>();
		Selection selection = evaluation.selection();
		List<Set<Long>> visible = OracleAssigner.visibleProducts(input, selection);

		Map<Long, WatchUnit> unitsById = new HashMap<>();
		for (WatchUnit unit : input.units()) {
			unitsById.put(unit.id(), unit);
		}
		Map<Long, UnitResult> resultsById = new HashMap<>();
		for (UnitResult result : evaluation.units()) {
			if (resultsById.put(result.watchUnitId(), result) != null) {
				problems.add("단위 결과가 중복됐다: " + result.watchUnitId());
			}
		}
		if (!resultsById.keySet().equals(unitsById.keySet())) {
			problems.add("결과 단위 집합이 입력과 다르다: " + resultsById.keySet() + " vs " + unitsById.keySet());
			return problems;
		}

		int[] minutesByMonth = new int[Selection.MONTH_COUNT];
		int mustCompleted = 0;
		int score = 0;
		for (UnitResult result : evaluation.units()) {
			WatchUnit unit = unitsById.get(result.watchUnitId());
			List<Integer> visibleMonths = OracleAssigner.visibleMonths(unit, visible);
			if (!result.scheduled()) {
				if (result.reason() == null) {
					problems.add("미배정 단위에 이유 코드가 없다: " + unit.id());
				} else {
					UnitReason expected = unit.watchableProductIds().isEmpty()
							? (unit.isProviderUnknown() ? UnitReason.UNKNOWN : UnitReason.NO_PROVIDER)
							: (visibleMonths.isEmpty() ? UnitReason.BUDGET : UnitReason.TIME);
					if (result.reason() != expected) {
						problems.add("이유 코드가 판정 순서와 다르다: unit=" + unit.id() + ", 실제=" + result.reason() + ", 기대=" + expected);
					}
				}
				if (!result.assignments().isEmpty()) {
					problems.add("미배정 단위에 배정 행이 있다: " + unit.id());
				}
				continue;
			}

			// 배정된 단위: 배정 분 합 = 필요 분, 배정 달은 볼 수 있는 달, 연속, 마지막 조각 외 120분 이상, 완료 달 = 마지막 달
			int sum = 0;
			List<Assignment> pieces = result.assignments();
			for (int i = 0; i < pieces.size(); i++) {
				Assignment piece = pieces.get(i);
				sum += piece.minutes();
				minutesByMonth[piece.monthIndex()] += piece.minutes();
				if (!visibleMonths.contains(piece.monthIndex())) {
					problems.add("배정 달에 그 단위를 볼 수 있는 상품이 없다: unit=" + unit.id() + ", month=" + piece.monthIndex());
				} else if (!unit.watchableProductIds().contains(piece.productId()) || !visible.get(piece.monthIndex()).contains(piece.productId())) {
					problems.add("배정 상품을 쓸 수 없다: unit=" + unit.id() + ", product=" + piece.productId() + ", month=" + piece.monthIndex());
				}
				if (i > 0 && piece.monthIndex() != pieces.get(i - 1).monthIndex() + 1) {
					problems.add("배정 달이 연속이 아니다: unit=" + unit.id());
				}
				if (i < pieces.size() - 1 && piece.minutes() < MIN_PIECE_MINUTES) {
					problems.add("마지막이 아닌 조각이 120분 미만이다: unit=" + unit.id() + ", minutes=" + piece.minutes());
				}
			}
			if (sum != unit.requiredMinutes()) {
				problems.add("배정 분 합이 필요 분과 다르다: unit=" + unit.id() + ", 합=" + sum + ", 필요=" + unit.requiredMinutes());
			}
			if (pieces.isEmpty() || result.completedMonthIndex() != pieces.get(pieces.size() - 1).monthIndex()) {
				problems.add("시청 완료 달이 마지막 배정 달과 다르다: unit=" + unit.id());
			}
			if (unit.requiredMinutes() <= input.monthlyWatchMinutes() && pieces.size() != 1) {
				problems.add("한 달 이내 단위가 나뉘었다: unit=" + unit.id());
			}
			if (unit.priority() == Priority.MUST) {
				mustCompleted++;
			} else {
				score += unit.priority() == Priority.WANT ? 2 : 1;
			}
		}

		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			if (minutesByMonth[month] > input.monthlyWatchMinutes()) {
				problems.add("달 배정 분 합이 월 시청 시간을 넘는다: month=" + month + ", 합=" + minutesByMonth[month]);
			}
		}

		// 시즌 순서: 찜 목록의 직전 시즌만 기준. 뒤 시즌이 배정이면 앞 시즌도 배정이고 완료 달이 앞 시즌 이상이다
		for (WatchUnit unit : input.units()) {
			WatchUnit before = null;
			for (WatchUnit other : input.units()) {
				if (other.titleId() != null && other.titleId().equals(unit.titleId()) && other.seasonNumber() < unit.seasonNumber()
						&& (before == null || other.seasonNumber() > before.seasonNumber())) {
					before = other;
				}
			}
			UnitResult after = resultsById.get(unit.id());
			if (before == null || !after.scheduled()) {
				continue;
			}
			UnitResult previous = resultsById.get(before.id());
			if (!previous.scheduled()) {
				problems.add("앞 시즌이 미배정인데 뒤 시즌이 배정됐다: before=" + before.id() + ", after=" + unit.id());
			} else if (after.completedMonthIndex() < previous.completedMonthIndex()) {
				problems.add("뒤 시즌이 앞 시즌보다 먼저 시청 완료됐다: before=" + before.id() + ", after=" + unit.id());
			}
		}

		if (evaluation.mustCompleted() != mustCompleted) {
			problems.add("mustCompleted가 재계산 값과 다르다: 실제=" + evaluation.mustCompleted() + ", 재계산=" + mustCompleted);
		}
		if (evaluation.score() != score) {
			problems.add("score가 재계산 값과 다르다: 실제=" + evaluation.score() + ", 재계산=" + score);
		}
		int cost = OracleAssigner.totalCost(input, selection);
		if (evaluation.totalCost() != cost) {
			problems.add("totalCost가 재계산 값과 다르다: 실제=" + evaluation.totalCost() + ", 재계산=" + cost);
		}
		return problems;
	}
}
