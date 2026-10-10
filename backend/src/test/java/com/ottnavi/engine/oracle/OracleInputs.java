package com.ottnavi.engine.oracle;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/** 오라클 차분·정확성 테스트가 쓰는 시드 고정 무작위 입력과 조합 생성기. 같은 시드는 항상 같은 입력·조합이다. */
public final class OracleInputs {

	private static final int[] PRICES = {2_000, 3_000, 5_000, 8_000, 10_000, 15_000}; // 15,000원은 모든 예산을 넘는다
	private static final int[] BUDGETS = {5_000, 8_000, 10_000, 13_000};
	private static final int[] MONTHLY_MINUTES = {100, 150, 300, 400, 600}; // 100은 긴 시즌 최소 조각(120분) 미만이다
	private static final int[] SEASON_MINUTES = {40, 60, 90, 150, 250, 350, 500, 800, 1_200};
	private static final int[] MOVIE_MINUTES = {60, 100, 120, 200};

	private OracleInputs() {
	}

	/** 시리즈 1~3개(시즌 1~4개, 일부만 찜)와 영화 0~3편이 섞인 입력을 만든다. 상품은 minProducts~maxProducts개. */
	public static PlanInput randomInput(Random random, int minProducts, int maxProducts) {
		int productCount = minProducts + random.nextInt(maxProducts - minProducts + 1);
		List<OttProduct> products = new ArrayList<>();
		for (int i = 0; i < productCount; i++) {
			int roll = random.nextInt(100);
			OttProductCondition condition = roll < 12 ? OttProductCondition.FREE
					: roll < 27 ? OttProductCondition.SUBSCRIBED : OttProductCondition.NONE;
			products.add(new OttProduct(10L * (i + 1), PRICES[random.nextInt(PRICES.length)], condition));
		}
		Collections.shuffle(products, random);

		List<WatchUnit> units = new ArrayList<>();
		long nextId = 1;
		int seriesCount = 1 + random.nextInt(3);
		for (int title = 1; title <= seriesCount; title++) {
			int seasons = 1 + random.nextInt(4);
			for (int season = 1; season <= seasons; season++) {
				if (random.nextInt(10) < 3) {
					continue; // 일부 시즌은 찜하지 않았다(누락)
				}
				units.add(new WatchUnit(nextId++, randomPriority(random), SEASON_MINUTES[random.nextInt(SEASON_MINUTES.length)],
						randomWatchable(random, products), random.nextBoolean(), (long) title, season));
			}
		}
		int movieCount = random.nextInt(4);
		for (int i = 0; i < movieCount; i++) {
			units.add(new WatchUnit(nextId++, randomPriority(random), MOVIE_MINUTES[random.nextInt(MOVIE_MINUTES.length)],
					randomWatchable(random, products), random.nextBoolean()));
		}
		if (units.isEmpty()) {
			units.add(new WatchUnit(nextId, Priority.WANT, 100, randomWatchable(random, products), false));
		}
		Collections.shuffle(units, random);
		return new PlanInput(units, products, BUDGETS[random.nextInt(BUDGETS.length)], MONTHLY_MINUTES[random.nextInt(MONTHLY_MINUTES.length)]);
	}

	private static Priority randomPriority(Random random) {
		return Priority.values()[random.nextInt(Priority.values().length)];
	}

	private static Set<Long> randomWatchable(Random random, List<OttProduct> products) {
		Set<Long> watchable = new HashSet<>();
		if (random.nextInt(10) == 0) {
			return watchable; // 10%는 시청 가능 상품이 없는 단위(NO_PROVIDER·UNKNOWN)
		}
		for (OttProduct product : products) {
			if (random.nextBoolean()) {
				watchable.add(product.id());
			}
		}
		return watchable;
	}

	/** 달 하나에서 가능한 상품 부분집합. budgetOnly면 그 달 비용이 월 예산 이하인 것만 남긴다(상품 전체의 부분집합, 후보 제외 규칙 없음). */
	public static List<Set<Long>> monthSubsets(PlanInput input, int month, boolean budgetOnly) {
		List<OttProduct> products = input.products();
		List<Set<Long>> subsets = new ArrayList<>();
		for (int mask = 0; mask < (1 << products.size()); mask++) {
			Set<Long> subset = new TreeSet<>();
			for (int bit = 0; bit < products.size(); bit++) {
				if ((mask & (1 << bit)) != 0) {
					subset.add(products.get(bit).id());
				}
			}
			if (!budgetOnly || OracleAssigner.monthCost(input, month, subset) <= input.monthlyBudget()) {
				subsets.add(subset);
			}
		}
		return subsets;
	}

	/** 조합 수를 limit 이하로 맞춘 조합 목록. 전체가 limit 이하면 전부, 넘으면 빈 조합과 무작위 조합 limit-1개다. */
	public static List<Selection> selections(PlanInput input, boolean budgetOnly, int limit, Random random) {
		List<Set<Long>> month0 = monthSubsets(input, 0, budgetOnly);
		List<Set<Long>> month1 = monthSubsets(input, 1, budgetOnly);
		List<Set<Long>> month2 = monthSubsets(input, 2, budgetOnly);
		long total = (long) month0.size() * month1.size() * month2.size();
		List<Selection> result = new ArrayList<>();
		if (total <= limit) {
			for (Set<Long> first : month0) {
				for (Set<Long> second : month1) {
					for (Set<Long> third : month2) {
						result.add(Selection.of(first, second, third));
					}
				}
			}
			return result;
		}
		result.add(Selection.of(Set.of(), Set.of(), Set.of()));
		for (int i = 1; i < limit; i++) {
			result.add(Selection.of(month0.get(random.nextInt(month0.size())), month1.get(random.nextInt(month1.size())),
					month2.get(random.nextInt(month2.size()))));
		}
		return result;
	}

	/** 사람이 읽을 수 있는 입력 설명(반례 보고용). */
	public static String describe(PlanInput input) {
		StringBuilder text = new StringBuilder("budget=" + input.monthlyBudget() + ", M=" + input.monthlyWatchMinutes() + "\n products:");
		for (OttProduct product : input.products()) {
			text.append(" ").append(product.id()).append("(").append(product.monthlyPrice()).append(",").append(product.condition()).append(")");
		}
		List<WatchUnit> sorted = new ArrayList<>(input.units());
		sorted.sort(java.util.Comparator.comparingLong(WatchUnit::id));
		for (WatchUnit unit : sorted) {
			text.append("\n unit ").append(unit.id()).append(" ").append(unit.priority()).append(" ").append(unit.requiredMinutes())
					.append("min title=").append(unit.titleId()).append(" season=").append(unit.seasonNumber())
					.append(" watch=").append(new TreeSet<>(unit.watchableProductIds())).append(unit.isProviderUnknown() ? " unknown" : "");
		}
		return text.toString();
	}
}
