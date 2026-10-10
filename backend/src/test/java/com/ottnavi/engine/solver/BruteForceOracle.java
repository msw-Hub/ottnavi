package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.engine.rule.Evaluator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 테스트 전용 무차별 대입 오라클(명세 S3·S4·S6·S8을 Solver 구현과 독립적으로 다시 해석한 두 번째 구현).
 * Solver·PlanObjective를 쓰지 않고 {@link Evaluator}만 쓴다. 세 유형의 비교기도 명세 3절 표대로 이 클래스 안에 직접 구현한다.
 * 상품이 3~4개인 작은 입력에서만 쓴다.
 */
public final class BruteForceOracle {

	private BruteForceOracle() {
	}

	/** 추천형 점수의 70% 올림(명세 3절, 정수 계산). */
	public static int scoreFloor(int recommendedScore) {
		return (recommendedScore * 7 + 9) / 10;
	}

	/** 한 달에 고른 상품들의 그 달 비용(명세 S1): FREE 0원, 0번째 달 SUBSCRIBED 0원, 나머지는 월 가격. */
	public static int monthCost(int month, Set<Long> productIds, List<OttProduct> products) {
		int cost = 0;
		for (long productId : productIds) {
			OttProduct product = find(productId, products);
			if (product.condition() == OttProductCondition.FREE) {
				continue;
			}
			if (month == 0 && product.condition() == OttProductCondition.SUBSCRIBED) {
				continue;
			}
			cost += product.monthlyPrice();
		}
		return cost;
	}

	/** 조합이 달마다 월 예산 이하인지 확인한다(명세 S1). */
	public static boolean withinBudget(PlanInput input, Selection selection) {
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			if (monthCost(month, selection.productIdsOf(month), input.products()) > input.monthlyBudget()) {
				return false;
			}
		}
		return true;
	}

	/** 명세 S6: 그 달의 후보 상품. FREE, 0번째 달 SUBSCRIBED, 어떤 단위도 못 보는 상품, 혼자서 그 달 예산을 넘는 상품은 뺀다. */
	public static List<OttProduct> candidates(PlanInput input, int month) {
		Set<Long> watchable = new HashSet<>();
		for (WatchUnit unit : input.units()) {
			watchable.addAll(unit.watchableProductIds());
		}
		List<OttProduct> result = new ArrayList<>();
		for (OttProduct product : input.products()) {
			if (product.condition() == OttProductCondition.FREE) {
				continue;
			}
			if (month == 0 && product.condition() == OttProductCondition.SUBSCRIBED) {
				continue;
			}
			if (!watchable.contains(product.id())) {
				continue;
			}
			if (monthCost(month, Set.of(product.id()), input.products()) > input.monthlyBudget()) {
				continue;
			}
			result.add(product);
		}
		return result;
	}

	/** 그 달에 고를 수 있는 후보 상품(S6)의 예산 이하 모든 부분집합(빈 집합 포함, 명세 S2). */
	public static List<Set<Long>> monthOptions(PlanInput input, int month) {
		List<OttProduct> candidates = candidates(input, month);
		List<Set<Long>> options = new ArrayList<>();
		for (int mask = 0; mask < (1 << candidates.size()); mask++) {
			Set<Long> subset = new TreeSet<>();
			for (int bit = 0; bit < candidates.size(); bit++) {
				if ((mask & (1 << bit)) != 0) {
					subset.add(candidates.get(bit).id());
				}
			}
			if (monthCost(month, subset, input.products()) <= input.monthlyBudget()) {
				options.add(subset);
			}
		}
		return options;
	}

	/** 조합의 모든 달 선택이 그 달의 후보 상품(S6)으로만 이루어졌는지 확인한다(성질 20). */
	public static boolean usesOnlyCandidates(PlanInput input, Selection selection) {
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			Set<Long> allowed = new HashSet<>();
			for (OttProduct product : candidates(input, month)) {
				allowed.add(product.id());
			}
			if (!allowed.containsAll(selection.productIdsOf(month))) {
				return false;
			}
		}
		return true;
	}

	/** 절약형·간편형 완전탐색이 훑는 조합 수(S5): 달별 예산 이하 부분집합 수의 곱(동치 제거 없음). */
	public static long fullCombinationCount(PlanInput input) {
		long count = 1;
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			count *= monthOptions(input, month).size();
		}
		return count;
	}

	/**
	 * 추천형 완전탐색이 훑는 조합 수(S5·S7): 달마다 "그 달에 볼 수 있게 되는 단위 집합"이 같은 부분집합을 한 그룹으로 묶고 그룹 수의 곱.
	 * 볼 수 있는 상품은 선택 상품에 FREE 상품과 0번째 달의 SUBSCRIBED 상품을 더한 것이다.
	 */
	public static long equivalenceCombinationCount(PlanInput input) {
		long count = 1;
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			Set<Set<Long>> groups = new HashSet<>();
			for (Set<Long> option : monthOptions(input, month)) {
				Set<Long> available = new HashSet<>(option);
				for (OttProduct product : input.products()) {
					if (product.condition() == OttProductCondition.FREE
							|| (month == 0 && product.condition() == OttProductCondition.SUBSCRIBED)) {
						available.add(product.id());
					}
				}
				Set<Long> watchableUnits = new HashSet<>();
				for (WatchUnit unit : input.units()) {
					if (!Collections.disjoint(unit.watchableProductIds(), available)) {
						watchableUnits.add(unit.id());
					}
				}
				groups.add(watchableUnits);
			}
			count *= groups.size();
		}
		return count;
	}

	/** 예산 이하 모든 후보 조합(S6)을 평가한 결과(명세 S3). */
	public static List<Evaluation> allEvaluations(PlanInput input) {
		return allEvaluations(input, new Evaluator(input)::evaluate);
	}

	/** 평가 함수를 주입하는 전수 탐색: 예산 이하 모든 후보 조합(S6)을 evaluate로 평가한다(예: 독립 구현 OracleAssigner). */
	public static List<Evaluation> allEvaluations(PlanInput input, java.util.function.Function<Selection, Evaluation> evaluate) {
		List<Set<Long>> month0 = monthOptions(input, 0);
		List<Set<Long>> month1 = monthOptions(input, 1);
		List<Set<Long>> month2 = monthOptions(input, 2);
		List<Evaluation> all = new ArrayList<>();
		for (Set<Long> first : month0) {
			for (Set<Long> second : month1) {
				for (Set<Long> third : month2) {
					all.add(evaluate.apply(Selection.of(first, second, third)));
				}
			}
		}
		return all;
	}

	/** 추천형 최선(무차별 대입). */
	public static Evaluation bestRecommended(PlanInput input) {
		return best(input, PlanType.RECOMMENDED, 0);
	}

	/** 유형별 최선(무차별 대입). recommendedScore는 절약형·간편형의 점수 하한 기준이며 추천형에서는 쓰지 않는다. */
	public static Evaluation best(PlanInput input, PlanType type, int recommendedScore) {
		return Collections.max(allEvaluations(input), comparator(type, input, recommendedScore));
	}

	/** S4를 독립적으로 구현한 그리디(추천형): 앞선 달은 고정, 뒤의 달은 빈 집합으로 두고 그 달의 후보 부분집합을 모두 평가해 최선을 확정한다. */
	public static Evaluation greedy(PlanInput input, Comparator<Evaluation> comparator) {
		Evaluator evaluator = new Evaluator(input);
		List<Set<Long>> fixed = new ArrayList<>(List.of(Set.of(), Set.of(), Set.of()));
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			Evaluation bestOfMonth = null;
			for (Set<Long> option : monthOptions(input, month)) {
				List<Set<Long>> trial = new ArrayList<>(fixed);
				trial.set(month, option);
				Evaluation evaluation = evaluator.evaluate(new Selection(trial));
				if (bestOfMonth == null || comparator.compare(evaluation, bestOfMonth) > 0) {
					bestOfMonth = evaluation;
				}
			}
			fixed.set(month, bestOfMonth.selection().productIdsOf(month));
		}
		return evaluator.evaluate(new Selection(fixed));
	}

	/**
	 * S8을 독립적으로 구현한 baseline 개선 그리디(절약형·간편형): baseline에서 출발해 달 0→1→2 순서로 그 달의 선택만 후보 부분집합 중
	 * 비교기 최선으로 바꿔 3개월 전체를 평가하고, 한 바퀴에서 바뀐 달이 없으면 멈춘다. 달마다 "최선 후보가 현재보다 엄격히 좋을 때만" 교체한다.
	 */
	public static Evaluation improve(PlanInput input, Comparator<Evaluation> comparator, Selection baseline) {
		Evaluator evaluator = new Evaluator(input);
		Evaluation current = evaluator.evaluate(baseline);
		boolean changed;
		do {
			changed = false;
			for (int month = 0; month < Selection.MONTH_COUNT; month++) {
				Evaluation bestOfMonth = current;
				for (Set<Long> option : monthOptions(input, month)) {
					List<Set<Long>> trial = new ArrayList<>(current.selection().productIdsByMonth());
					trial.set(month, option);
					Evaluation evaluation = evaluator.evaluate(new Selection(trial));
					if (comparator.compare(evaluation, bestOfMonth) > 0) {
						bestOfMonth = evaluation;
					}
				}
				if (bestOfMonth != current) {
					current = bestOfMonth;
					changed = true;
				}
			}
		} while (changed);
		return current;
	}

	/**
	 * 3개월 가입 횟수(명세 3절): m번째 달에 선택했고 (m-1)번째 달에는 구독 중이 아닌 상품마다 1회.
	 * 0번째 달에 구독 중인 상품은 "선택한 상품 + SUBSCRIBED 상품"이고, 0번째 달의 직전은 SUBSCRIBED 상품으로 본다(가정).
	 */
	public static int joinTotal(Selection selection, List<OttProduct> products) {
		Set<Long> subscribed = new HashSet<>();
		for (OttProduct product : products) {
			if (product.condition() == OttProductCondition.SUBSCRIBED) {
				subscribed.add(product.id());
			}
		}
		Set<Long> previous = subscribed;
		int total = 0;
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			Set<Long> selected = selection.productIdsOf(month);
			for (long productId : selected) {
				if (!previous.contains(productId)) {
					total++;
				}
			}
			Set<Long> owned = new HashSet<>(selected);
			if (month == 0) {
				owned.addAll(subscribed);
			}
			previous = owned;
		}
		return total;
	}

	/** 그 달에 새로 돈이 드는 상품 수(038 정의, 결제 미루기 ⑤에 쓴다). 가입 횟수와 다른 값이다. */
	public static int newPayments(Selection selection, int month, List<OttProduct> products) {
		int count = 0;
		for (long productId : selection.productIdsOf(month)) {
			OttProductCondition condition = find(productId, products).condition();
			if (condition == OttProductCondition.FREE || (month == 0 && condition == OttProductCondition.SUBSCRIBED)) {
				continue;
			}
			count++;
		}
		return count;
	}

	/**
	 * 조기 시청(④) 값: month번째 달까지(포함) 시청 완료한 MUST 수와 점수 합(WANT 2, MAYBE 1, MUST는 점수에 넣지 않음).
	 * 시청 단위 ID로 입력의 우선순위를 찾아 직접 센다(실제 비교기의 인덱스 매칭·long 묶음과 독립인 구현). 반환은 {MUST 수, 점수 합}.
	 */
	public static int[] completedUpTo(Evaluation evaluation, int month, PlanInput input) {
		int mustCount = 0;
		int scoreSum = 0;
		for (UnitResult result : evaluation.units()) {
			if (!result.scheduled() || result.completedMonthIndex() > month) {
				continue;
			}
			Priority priority = input.units().stream().filter(unit -> unit.id() == result.watchUnitId()).findFirst().orElseThrow().priority();
			if (priority == Priority.MUST) {
				mustCount++;
			} else if (priority == Priority.WANT) {
				scoreSum += 2;
			} else {
				scoreSum += 1;
			}
		}
		return new int[] {mustCount, scoreSum};
	}

	/** 명세 3절 표대로 직접 구현한 유형별 비교기(양수 = 첫 번째가 더 좋다). */
	public static Comparator<Evaluation> comparator(PlanType type, PlanInput input, int recommendedScore) {
		List<OttProduct> products = input.products();
		int floor = scoreFloor(recommendedScore);
		return (a, b) -> {
			int must = Integer.compare(a.mustCompleted(), b.mustCompleted());
			if (must != 0) {
				return must;
			}
			int order;
			switch (type) {
				case RECOMMENDED:
					if ((order = Integer.compare(a.score(), b.score())) != 0) {
						return order;
					}
					if ((order = Integer.compare(b.totalCost(), a.totalCost())) != 0) {
						return order;
					}
					break;
				case SAVER:
					if ((order = Boolean.compare(a.score() >= floor, b.score() >= floor)) != 0) {
						return order;
					}
					if ((order = Integer.compare(b.totalCost(), a.totalCost())) != 0) {
						return order;
					}
					if ((order = Integer.compare(a.score(), b.score())) != 0) {
						return order;
					}
					break;
				case SIMPLE:
					if ((order = Boolean.compare(a.score() >= floor, b.score() >= floor)) != 0) {
						return order;
					}
					if ((order = Integer.compare(joinTotal(b.selection(), products), joinTotal(a.selection(), products))) != 0) {
						return order;
					}
					if ((order = Integer.compare(b.totalCost(), a.totalCost())) != 0) {
						return order;
					}
					if ((order = Integer.compare(a.score(), b.score())) != 0) {
						return order;
					}
					break;
				default:
					throw new IllegalStateException("알 수 없는 유형: " + type);
			}
			// 조기 시청(④): 달 0→1→2 순서로 "그 달까지 시청 완료한 MUST 수, 같으면 점수 합"이 먼저 다른 달에서 큰 쪽이 좋다
			for (int month = 0; month < Selection.MONTH_COUNT; month++) {
				int[] x = completedUpTo(a, month, input);
				int[] y = completedUpTo(b, month, input);
				if ((order = Integer.compare(x[0], y[0])) != 0) {
					return order;
				}
				if ((order = Integer.compare(x[1], y[1])) != 0) {
					return order;
				}
			}
			// 결제 미루기(⑤): 달 0→1→2 순서로 새 결제 수가 먼저 다른 달에서 적은 쪽이 좋다
			for (int month = 0; month < Selection.MONTH_COUNT; month++) {
				order = Integer.compare(newPayments(b.selection(), month, products), newPayments(a.selection(), month, products));
				if (order != 0) {
					return order;
				}
			}
			// 달별 상품 ID 사전순으로 작은 쪽이 좋다(앞부분이 같으면 짧은 쪽이 작다)
			for (int month = 0; month < Selection.MONTH_COUNT; month++) {
				order = compareIds(a.selection().productIdsOf(month), b.selection().productIdsOf(month));
				if (order != 0) {
					return -order;
				}
			}
			return 0;
		};
	}

	private static int compareIds(Set<Long> left, Set<Long> right) {
		Iterator<Long> l = new TreeSet<>(left).iterator();
		Iterator<Long> r = new TreeSet<>(right).iterator();
		while (l.hasNext() && r.hasNext()) {
			int order = Long.compare(l.next(), r.next());
			if (order != 0) {
				return order;
			}
		}
		return Boolean.compare(l.hasNext(), r.hasNext());
	}

	private static OttProduct find(long productId, List<OttProduct> products) {
		return products.stream().filter(product -> product.id() == productId).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("상품이 없다: " + productId));
	}
}
