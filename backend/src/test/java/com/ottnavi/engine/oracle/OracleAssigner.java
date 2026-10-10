package com.ottnavi.engine.oracle;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 테스트 전용 배정 오라클: 규칙 문서(PRD 5.4, TECH 1절, TASK038 안내서 3절)의 정의만 보고 {@code Evaluator}와 독립적으로 구현한 평가기다.
 * 비트마스크·사전 계산 없이 List·Set·Map만 쓰고, 속도보다 문서 정의와의 1:1 대응을 우선한다.
 *
 * <p>정의 범위: 달마다 선택 상품 가격 합이 월 예산 이하인 조합(예산 안 조합)에서만 규칙 문서가 결과를 정한다.
 * 예산 밖 조합은 평가기 D7(예산 미검사) 때문에 문서에 정의가 없어서, 끌어올림이 막힌 사슬의 뒤 시즌은 "정적 불가능이면 미배정"으로 둔다.
 * 입력은 {@link PlanInput#units()}의 순서를 쓰지 않고(집합으로만 본다) 정렬과 끌어올림을 여기서 다시 한다.
 */
public final class OracleAssigner {

	/** 긴 시즌의 마지막이 아닌 조각의 최소 분(PRD 5.4 긴 시즌, 결정 4). 모델 상수를 쓰지 않고 문서 값을 다시 적었다. */
	private static final int MIN_PIECE_MINUTES = 120;
	private static final int MONTHS = 3;

	private OracleAssigner() {
	}

	/** 한 조합을 평가한다. 결과 단위 목록은 입력 단위 목록({@code input.units()})과 같은 순서다. */
	public static Evaluation evaluate(PlanInput input, Selection selection) {
		Map<Long, OttProduct> productsById = new HashMap<>();
		for (OttProduct product : input.products()) {
			productsById.put(product.id(), product);
		}
		for (int month = 0; month < MONTHS; month++) {
			for (long productId : selection.productIdsOf(month)) {
				if (!productsById.containsKey(productId)) {
					throw new IllegalArgumentException("조합에 입력에 없는 상품이 있다: " + productId);
				}
			}
		}

		List<Set<Long>> visible = visibleProducts(input, selection);
		List<WatchUnit> order = assignmentOrder(input, productsById);

		int[] remaining = new int[MONTHS]; // 달마다 남은 시청 가능 분
		for (int month = 0; month < MONTHS; month++) {
			remaining[month] = input.monthlyWatchMinutes();
		}
		Map<Long, UnitResult> done = new HashMap<>();
		for (WatchUnit unit : order) {
			done.put(unit.id(), assign(input, unit, visible, remaining, done));
		}

		int mustCompleted = 0;
		int score = 0;
		List<UnitResult> results = new ArrayList<>();
		for (WatchUnit unit : input.units()) {
			UnitResult result = done.get(unit.id());
			results.add(result);
			if (result.scheduled()) {
				if (unit.priority() == Priority.MUST) {
					mustCompleted++;
				} else if (unit.priority() == Priority.WANT) {
					score += 2;
				} else {
					score += 1;
				}
			}
		}
		return new Evaluation(selection, mustCompleted, score, totalCost(input, selection), results);
	}

	/** 3개월 비용 합: FREE 0원, 0번째 달의 SUBSCRIBED 0원, 나머지는 선택한 상품의 월 가격(PRD 5.4 구독 상태 반영). */
	public static int totalCost(PlanInput input, Selection selection) {
		int total = 0;
		for (int month = 0; month < MONTHS; month++) {
			total += monthCost(input, month, selection.productIdsOf(month));
		}
		return total;
	}

	/** 한 달에 고른 상품들의 그 달 비용. */
	public static int monthCost(PlanInput input, int month, Set<Long> productIds) {
		int cost = 0;
		for (long productId : productIds) {
			OttProduct product = input.products().stream().filter(p -> p.id() == productId).findFirst().orElseThrow();
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

	/** 달마다 시청에 쓸 수 있는 상품: 선택 상품 + FREE 상품(모든 달) + SUBSCRIBED 상품(0번째 달만, D2). */
	public static List<Set<Long>> visibleProducts(PlanInput input, Selection selection) {
		List<Set<Long>> visible = new ArrayList<>();
		for (int month = 0; month < MONTHS; month++) {
			Set<Long> products = new HashSet<>(selection.productIdsOf(month));
			for (OttProduct product : input.products()) {
				if (product.condition() == OttProductCondition.FREE
						|| (month == 0 && product.condition() == OttProductCondition.SUBSCRIBED)) {
					products.add(product.id());
				}
			}
			visible.add(products);
		}
		return visible;
	}

	/** 단위를 볼 수 있는 달(오름차순). 그 달의 사용 가능 상품과 단위의 시청 가능 상품이 하나라도 겹치면 볼 수 있다. */
	public static List<Integer> visibleMonths(WatchUnit unit, List<Set<Long>> visible) {
		List<Integer> months = new ArrayList<>();
		for (int month = 0; month < MONTHS; month++) {
			if (smallestCommon(unit, visible.get(month)) != null) {
				months.add(month);
			}
		}
		return months;
	}

	/** 단위의 시청 가능 상품과 사용 가능 상품의 교집합 중 ID가 가장 작은 것(D9). 없으면 null. */
	private static Long smallestCommon(WatchUnit unit, Set<Long> usable) {
		Long smallest = null;
		for (long productId : unit.watchableProductIds()) {
			if (usable.contains(productId) && (smallest == null || productId < smallest)) {
				smallest = productId;
			}
		}
		return smallest;
	}

	// ------------------------------------------------------------------ 배정 순서

	/** 배정 순서: 기본 정렬 뒤 한 번 훑으며 앞 시즌을 끌어올린다(PRD 5.4 시즌 순서). */
	static List<WatchUnit> assignmentOrder(PlanInput input, Map<Long, OttProduct> productsById) {
		List<WatchUnit> base = new ArrayList<>(input.units());
		base.sort(Comparator
				.comparingInt((WatchUnit unit) -> unit.watchableProductIds().isEmpty() ? 1 : 0) // 시청 가능 상품 없는 단위는 맨 뒤
				.thenComparingInt(unit -> priorityRank(unit.priority()))
				.thenComparingInt(WatchUnit::requiredMinutes)
				.thenComparingLong(WatchUnit::id));

		List<WatchUnit> ordered = new ArrayList<>();
		Set<Long> emitted = new HashSet<>();
		for (WatchUnit unit : base) {
			if (emitted.contains(unit.id())) {
				continue;
			}
			if (unit.titleId() != null) {
				List<WatchUnit> notEmittedEarlier = new ArrayList<>();
				for (WatchUnit earlier : seasonsBefore(input.units(), unit)) {
					if (!emitted.contains(earlier.id())) {
						notEmittedEarlier.add(earlier);
					}
				}
				boolean pull = !notEmittedEarlier.isEmpty() && !staticallyImpossible(input, unit, productsById);
				for (WatchUnit earlier : notEmittedEarlier) {
					if (staticallyImpossible(input, earlier, productsById)) {
						pull = false;
					}
				}
				if (pull) {
					for (WatchUnit earlier : notEmittedEarlier) {
						ordered.add(earlier);
						emitted.add(earlier.id());
					}
				}
			}
			ordered.add(unit);
			emitted.add(unit.id());
		}
		return ordered;
	}

	private static int priorityRank(Priority priority) {
		return switch (priority) {
			case MUST -> 0;
			case WANT -> 1;
			case MAYBE -> 2;
		};
	}

	/** 같은 시리즈에서 시즌 번호가 더 작은 단위들을 시즌 번호 오름차순으로 돌려준다. */
	private static List<WatchUnit> seasonsBefore(List<WatchUnit> units, WatchUnit unit) {
		List<WatchUnit> earlier = new ArrayList<>();
		for (WatchUnit other : units) {
			if (other.titleId() != null && other.titleId().equals(unit.titleId()) && other.seasonNumber() < unit.seasonNumber()) {
				earlier.add(other);
			}
		}
		earlier.sort(Comparator.comparingInt(WatchUnit::seasonNumber));
		return earlier;
	}

	/** 직전 시즌: 같은 시리즈에서 시즌 번호가 작은 것 중 가장 큰 번호의 단위(없으면 null). 찜 목록에 있는 직전 시즌만 기준이다. */
	private static WatchUnit predecessor(List<WatchUnit> units, WatchUnit unit) {
		List<WatchUnit> earlier = seasonsBefore(units, unit);
		return earlier.isEmpty() ? null : earlier.get(earlier.size() - 1);
	}

	/** 정적 불가능(PRD 5.4 끌어올림 제외 ①②③). */
	public static boolean staticallyImpossible(PlanInput input, WatchUnit unit, Map<Long, OttProduct> productsById) {
		boolean candidateExists = false; // ① FREE·SUBSCRIBED 이거나 월 가격이 예산 이하인 시청 가능 상품이 있다
		for (long productId : unit.watchableProductIds()) {
			OttProduct product = productsById.get(productId);
			if (product.condition() == OttProductCondition.FREE || product.condition() == OttProductCondition.SUBSCRIBED
					|| product.monthlyPrice() <= input.monthlyBudget()) {
				candidateExists = true;
			}
		}
		if (!candidateExists) {
			return true;
		}
		if (unit.requiredMinutes() > input.monthlyWatchMinutes() * 3) { // ②
			return true;
		}
		return unit.requiredMinutes() > input.monthlyWatchMinutes() && input.monthlyWatchMinutes() < MIN_PIECE_MINUTES; // ③
	}

	// ------------------------------------------------------------------ 배정

	private static UnitResult assign(PlanInput input, WatchUnit unit, List<Set<Long>> visible, int[] remaining, Map<Long, UnitResult> done) {
		if (unit.watchableProductIds().isEmpty()) {
			return UnitResult.unscheduled(unit.id(), unit.isProviderUnknown() ? UnitReason.UNKNOWN : UnitReason.NO_PROVIDER);
		}
		List<Integer> months = visibleMonths(unit, visible);
		UnitReason failReason = months.isEmpty() ? UnitReason.BUDGET : UnitReason.TIME;

		// 시즌 순서: 직전 시즌이 미배정이면 이 시즌도 미배정, 배정됐으면 그 시청 완료 달 이상에서만 끝낼 수 있다
		int earliestCompletion = 0;
		WatchUnit before = predecessor(input.units(), unit);
		if (before != null) {
			UnitResult beforeResult = done.get(before.id());
			if (beforeResult == null) {
				// 직전 시즌이 아직 배정 전이다. 끌어올림이 막힌 경우(사슬에 정적 불가능이 있음)에만 가능하고, 그때 이 시즌은 어차피 미배정이다
				if (!chainHasImpossible(input, unit)) {
					throw new IllegalStateException("끌어올림 불변식 위반: 직전 시즌이 뒤 시즌보다 늦게 배정된다 unit=" + unit.id() + ", before=" + before.id());
				}
				return UnitResult.unscheduled(unit.id(), failReason);
			}
			if (!beforeResult.scheduled()) {
				return UnitResult.unscheduled(unit.id(), failReason);
			}
			earliestCompletion = beforeResult.completedMonthIndex();
		}

		List<Assignment> pieces = unit.requiredMinutes() <= input.monthlyWatchMinutes()
				? placeShort(unit, months, visible, remaining, earliestCompletion)
				: placeLong(unit, months, visible, remaining, earliestCompletion);
		if (pieces == null) {
			return UnitResult.unscheduled(unit.id(), failReason);
		}
		for (Assignment piece : pieces) {
			remaining[piece.monthIndex()] -= piece.minutes();
		}
		return UnitResult.scheduled(unit.id(), pieces.get(pieces.size() - 1).monthIndex(), pieces);
	}

	/** 이 단위 또는 그 앞 시즌 중에 정적 불가능이 있는지. */
	private static boolean chainHasImpossible(PlanInput input, WatchUnit unit) {
		Map<Long, OttProduct> productsById = new HashMap<>();
		for (OttProduct product : input.products()) {
			productsById.put(product.id(), product);
		}
		if (staticallyImpossible(input, unit, productsById)) {
			return true;
		}
		for (WatchUnit earlier : seasonsBefore(input.units(), unit)) {
			if (staticallyImpossible(input, earlier, productsById)) {
				return true;
			}
		}
		return false;
	}

	/** 한 달 이내 단위: 볼 수 있는 달 중 시청 완료 달 제약을 만족하고 남은 시간이 충분한 가장 이른 달 하나에 통째로 배정한다. */
	private static List<Assignment> placeShort(WatchUnit unit, List<Integer> months, List<Set<Long>> visible, int[] remaining,
			int earliestCompletion) {
		for (int month : months) {
			if (month >= earliestCompletion && remaining[month] >= unit.requiredMinutes()) {
				return List.of(new Assignment(month, smallestCommon(unit, visible.get(month)), unit.requiredMinutes()));
			}
		}
		return null;
	}

	/**
	 * 긴 시즌: 시작 달을 이른 달부터 시도한다. 시작 달부터 볼 수 있는 달이 연속으로 이어져야 하고, 달마다 가능한 만큼 채우며,
	 * 마지막 조각이 아닌 조각은 120분 이상이어야 한다(0분이면 끊김). 시청 완료 달이 직전 시즌의 완료 달보다 이르면 그 시작 달은 건너뛴다.
	 */
	private static List<Assignment> placeLong(WatchUnit unit, List<Integer> months, List<Set<Long>> visible, int[] remaining,
			int earliestCompletion) {
		for (int start : months) {
			List<Assignment> pieces = new ArrayList<>();
			int needed = unit.requiredMinutes();
			int month = start;
			boolean broken = false;
			while (needed > 0) {
				if (month >= MONTHS || !months.contains(month)) {
					broken = true;
					break;
				}
				int take = Math.min(remaining[month], needed);
				if (take < needed && take < MIN_PIECE_MINUTES) {
					broken = true;
					break;
				}
				pieces.add(new Assignment(month, smallestCommon(unit, visible.get(month)), take));
				needed -= take;
				month++;
			}
			if (!broken && pieces.get(pieces.size() - 1).monthIndex() >= earliestCompletion) {
				return pieces;
			}
		}
		return null;
	}
}
