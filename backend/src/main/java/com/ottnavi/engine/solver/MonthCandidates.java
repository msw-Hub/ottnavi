package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.SortedIdSet;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.engine.rule.Evaluator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 달마다 고를 수 있는 상품 묶음(부분집합) 후보를 만든다. 완전탐색·그리디가 같은 코드로 후보를 만들고 센다(Task 039 안내서 S5·S7).
 *
 * <p>비유: 상품마다 스위치가 하나씩 있고, 켜고 끄는 모양 하나가 "이번 달에 구독할 묶음" 하나다.
 * 후보 상품이 3개면 모양은 2³ = 8가지이고, 그중 그 달 비용 합이 예산 이하인 모양만 후보로 남긴다.
 */
final class MonthCandidates {

	private MonthCandidates() {
	}

	/**
	 * 달별 예산 이하 부분집합 목록을 만든다(S1 예산, S2 빈 달 허용, S6 후보 상품 제외, S9 한도).
	 * 결과는 길이 3이고 각 달 목록의 첫 번째는 항상 빈 집합이다. 후보 상품이 MAX_PRODUCT_COUNT를 넘는 달이 있으면 IllegalArgumentException.
	 */
	static List<List<Set<Long>>> budgetSubsets(PlanInput input) {
		// S6: 어떤 시청 단위의 시청 가능 상품에도 없는 상품은 골라도 볼 수 있는 작품이 늘지 않는다
		Set<Long> watchableProductIds = new HashSet<>();
		for (WatchUnit unit : input.units()) {
			watchableProductIds.addAll(unit.watchableProductIds());
		}
		List<OttProduct> sortedProducts = new ArrayList<>(input.products());
		sortedProducts.sort(Comparator.comparingLong(OttProduct::id));

		List<Set<Long>> month0 = subsetsOf(candidateProducts(sortedProducts, watchableProductIds, input.monthlyBudget(), 0), input.monthlyBudget());
		// 1·2번째 달은 후보 상품 규칙이 같아(SUBSCRIBED 제외는 0번째 달만) 같은 목록을 함께 쓴다(불변 목록이라 공유해도 안전)
		List<Set<Long>> month1 = subsetsOf(candidateProducts(sortedProducts, watchableProductIds, input.monthlyBudget(), 1), input.monthlyBudget());
		return List.of(month0, month1, month1);
	}

	/**
	 * 추천형 동치 제거(S7): 그 달에 볼 수 있게 되는 시청 단위 집합이 완전히 같은 부분집합끼리 한 그룹으로 묶고,
	 * "그 달만 그 부분집합이고 나머지 두 달은 빈 집합"인 조합을 비교기로 비교해 그룹마다 1등 하나만 남긴다.
	 *
	 * <p>결과를 바꾸지 않는 이유: 평가기의 must·점수는 "단위별로 어느 달에 볼 수 있는지"에만 달려 있다.
	 * 그래서 같은 그룹의 두 부분집합은 다른 달을 무엇으로 고정하든 must·점수가 같고, 비용·결제 미루기·ID 순은 그 달에서만 달라지고 조기 시청은 시청 완료 달이 같아 늘 같다.
	 * 즉 그룹 안의 우열은 다른 달 선택과 상관없이 늘 같고, 1등이 아닌 부분집합은 최선 조합에 들어갈 수 없다.
	 * 옛 정의("더 많이 보이는 묶음이 같은 값이면 덜 보이는 묶음을 지운다")는 쓰지 않는다. 볼 수 있는 작품이 늘면 앞 순서 작품이
	 * 그 달 시간을 먼저 차지해 뒤 순서 긴 시즌의 이어 보기를 막을 수 있어서 결과가 틀려진다(안내서 성질 16).
	 */
	static List<List<Set<Long>>> representatives(PlanInput input, Evaluator evaluator, Comparator<Evaluation> comparator,
			List<List<Set<Long>>> subsetsByMonth) {
		Set<Long> freeIds = new HashSet<>();
		Set<Long> subscribedIds = new HashSet<>();
		for (OttProduct product : input.products()) {
			if (product.condition() == OttProductCondition.FREE) {
				freeIds.add(product.id());
			} else if (product.condition() == OttProductCondition.SUBSCRIBED) {
				subscribedIds.add(product.id());
			}
		}

		List<List<Set<Long>>> result = new ArrayList<>(Selection.MONTH_COUNT);
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			// 처음 나온 순서를 지키는 맵이라 결과 순서가 항상 같다(결정성)
			Map<BitSet, Evaluation> bestByGroup = new LinkedHashMap<>();
			for (Set<Long> subset : subsetsByMonth.get(month)) {
				BitSet key = visibleUnits(input.units(), subset, freeIds, month == 0 ? subscribedIds : Set.of());
				Evaluation candidate = evaluator.evaluate(onlyMonth(month, subset));
				Evaluation best = bestByGroup.get(key);
				if (best == null || comparator.compare(candidate, best) > 0) {
					bestByGroup.put(key, candidate);
				}
			}
			List<Set<Long>> kept = new ArrayList<>(bestByGroup.size());
			for (Evaluation best : bestByGroup.values()) {
				kept.add(best.selection().productIdsOf(month));
			}
			result.add(List.copyOf(kept));
		}
		return List.copyOf(result);
	}

	/** 3개월 조합 수(달별 후보 수의 곱)를 센다. 후보 상품이 20개 이하라 곱이 2^60 이하로 long을 넘지 않는다(S9). */
	static long combinationCount(List<List<Set<Long>>> candidatesByMonth) {
		long count = 1;
		for (List<Set<Long>> candidates : candidatesByMonth) {
			count *= candidates.size();
		}
		return count;
	}

	/** month번째 달만 subset이고 나머지 두 달은 빈 집합인 조합을 만든다. */
	static Selection onlyMonth(int month, Set<Long> subset) {
		List<Set<Long>> months = new ArrayList<>(List.of(Set.<Long>of(), Set.<Long>of(), Set.<Long>of()));
		months.set(month, subset);
		return new Selection(months);
	}

	/** month번째 달 하나를 replacement로 바꾼 조합을 만든다. */
	static Selection replaceMonth(Selection selection, int month, Set<Long> replacement) {
		List<Set<Long>> months = new ArrayList<>(selection.productIdsByMonth());
		months.set(month, replacement);
		return new Selection(months);
	}

	/**
	 * month번째 달의 후보 상품(ID 오름차순)을 고른다(S6).
	 * 제외: FREE(선택 없이도 항상 볼 수 있음), 0번째 달의 SUBSCRIBED(선택 없이도 0원으로 볼 수 있음),
	 * 어떤 시청 단위도 볼 수 없는 상품, 혼자서도 예산을 넘는 상품.
	 */
	private static List<OttProduct> candidateProducts(List<OttProduct> sortedProducts, Set<Long> watchableProductIds, int monthlyBudget, int month) {
		List<OttProduct> candidates = new ArrayList<>();
		for (OttProduct product : sortedProducts) {
			if (product.condition() == OttProductCondition.FREE) {
				continue;
			}
			if (month == 0 && product.condition() == OttProductCondition.SUBSCRIBED) {
				continue;
			}
			if (!watchableProductIds.contains(product.id())) {
				continue;
			}
			// 남은 후보는 그 달 비용이 곧 monthlyPrice다(평가기 기준에서 0원이 되는 FREE·0번째 달 SUBSCRIBED는 위에서 뺐다)
			if (product.monthlyPrice() > monthlyBudget) {
				continue;
			}
			candidates.add(product);
		}
		// S9: 2^P 열거가 불가능해지고 후보 수의 곱이 long을 넘지 않도록 막는다
		if (candidates.size() > ExactSolver.MAX_PRODUCT_COUNT) {
			throw new IllegalArgumentException("후보 상품은 최대 " + ExactSolver.MAX_PRODUCT_COUNT + "개까지 풀 수 있다: monthIndex=" + month + ", 후보 상품 수=" + candidates.size());
		}
		return candidates;
	}

	/**
	 * 후보 상품의 부분집합 중 비용 합이 예산 이하인 것을 비트마스크 오름차순으로 만든다(S1·S2).
	 * 마스크 0(빈 집합)은 비용이 0이라 항상 첫 번째로 들어간다.
	 * 집합은 여기서 한 번만 만들고 Solver가 수백만 번 재사용한다(조합마다 새로 만들지 않기 위함).
	 * 정렬된 불변 집합(SortedIdSet)으로 만들어, Selection이 조합마다 달별 집합을 다시 정렬 복사하지 않게 한다(Task 040 이월 최적화 ②).
	 */
	private static List<Set<Long>> subsetsOf(List<OttProduct> candidates, int monthlyBudget) {
		int productCount = candidates.size();
		Long[] boxedIds = new Long[productCount]; // 상품 ID를 한 번만 박싱해 부분집합이 같은 Long 객체를 공유하게 한다
		for (int bit = 0; bit < productCount; bit++) {
			boxedIds[bit] = candidates.get(bit).id();
		}
		List<Set<Long>> subsets = new ArrayList<>();
		for (int mask = 0; mask < (1 << productCount); mask++) {
			int cost = 0;
			List<Long> ids = new ArrayList<>(Integer.bitCount(mask));
			for (int bit = 0; bit < productCount; bit++) {
				if ((mask & (1 << bit)) != 0) {
					cost += candidates.get(bit).monthlyPrice();
					ids.add(boxedIds[bit]);
				}
			}
			// S1: 달별 예산은 달마다 따로 적용한다(3개월 합산 한도 없음)
			if (cost <= monthlyBudget) {
				subsets.add(SortedIdSet.of(ids));
			}
		}
		return List.copyOf(subsets);
	}

	/**
	 * 그 달에 볼 수 있게 되는 시청 단위를 비트로 표시한다(비트 i = 배정 순서 i번째 단위). 동치 제거의 그룹 기준이다.
	 * 평가기와 같은 시청 가능 규칙: 선택 상품 + FREE 상품(D1, 모든 달) + SUBSCRIBED 상품(D2, 0번째 달만).
	 */
	private static BitSet visibleUnits(List<WatchUnit> units, Set<Long> subset, Set<Long> freeIds, Set<Long> subscribedIdsThisMonth) {
		BitSet visible = new BitSet(units.size());
		for (int i = 0; i < units.size(); i++) {
			for (long productId : units.get(i).watchableProductIds()) {
				if (subset.contains(productId) || freeIds.contains(productId) || subscribedIdsThisMonth.contains(productId)) {
					visible.set(i);
					break;
				}
			}
		}
		return visible;
	}
}
