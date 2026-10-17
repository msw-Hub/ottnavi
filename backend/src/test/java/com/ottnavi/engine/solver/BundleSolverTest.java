package com.ottnavi.engine.solver;

import static com.ottnavi.engine.solver.SolverCases.ids;
import static com.ottnavi.engine.solver.SolverCases.sel;
import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 번들(Task 076)이 들어온 엔진 테스트: S1b 서비스 겹침 금지, S6 확장, 설계 문서 5절 예시, 오라클 대조, 그리디 전환.
 * 상품 ID는 1 = 넷플릭스 스탠다드, 2 = 티빙, 3 = 웨이브, 4 = 티빙+웨이브 번들로 쓴다(서비스 ID도 1·2·3).
 */
class BundleSolverTest {

	private static OttProduct single(long id, int price, long serviceId) {
		return new OttProduct(id, price, OttProductCondition.NONE, List.of(serviceId));
	}

	private static OttProduct subscribedSingle(long id, int price, long serviceId) {
		return new OttProduct(id, price, OttProductCondition.SUBSCRIBED, List.of(serviceId));
	}

	private static OttProduct bundle(long id, int price, Long... serviceIds) {
		return new OttProduct(id, price, OttProductCondition.NONE, List.of(serviceIds));
	}

	private static WatchUnit unit(long id, Priority priority, int minutes, Long... watchable) {
		return new WatchUnit(id, priority, minutes, Set.of(watchable), false);
	}

	/** 설계 문서 5절 입력: 예산 30,000, N 13,500 / T 13,500 / W 10,900 / 번들 B(티빙+웨이브) 16,000. 작품 셋을 한 달에 다 볼 수 있다. */
	private static PlanInput designExample() {
		List<OttProduct> products = List.of(single(1, 13_500, 1), single(2, 13_500, 2), single(3, 10_900, 3), bundle(4, 16_000, 2L, 3L));
		return new PlanInput(List.of(
				unit(1, Priority.MUST, 300, 2L, 4L),   // 티빙(또는 번들)에서만
				unit(2, Priority.MUST, 300, 3L, 4L),   // 웨이브(또는 번들)에서만
				unit(3, Priority.MUST, 300, 1L)),      // 넷플릭스에서만
				products, 30_000, 900);
	}

	@Test
	@DisplayName("S1b: 서비스가 겹치는 번들과 단품은 한 달에 함께 고르지 않고 겹치지 않으면 함께 고를 수 있다")
	void 서비스가_겹치면_한_달에_함께_고르지_않는다() {
		List<List<Set<Long>>> subsets = MonthCandidates.budgetSubsets(designExample());

		// 설계 문서 5절 표: {T,B}, {W,B}, {T,W,B}는 예산 안이어도 버리고, 서비스가 겹치지 않는 {N,B}는 남는다
		assertThat(subsets.get(0)).containsExactlyInAnyOrder(ids(), ids(1L), ids(2L), ids(3L), ids(4L),
				ids(1L, 2L), ids(1L, 3L), ids(2L, 3L), ids(1L, 4L));
		assertThat(subsets.get(1)).isEqualTo(subsets.get(0));
	}

	@Test
	@DisplayName("S6 확장: 0번째 달에는 구독 중인 서비스와 구성이 겹치는 번들을 후보에서 빼고 1·2번째 달에는 남긴다")
	void 구독_중인_서비스와_겹치는_번들은_이번_달_후보가_아니다() {
		// 티빙을 이미 구독 중이고 번들(티빙+웨이브)은 미구독 입력이다
		List<OttProduct> products = List.of(subscribedSingle(2, 13_500, 2), single(3, 10_900, 3), bundle(4, 16_000, 2L, 3L));
		PlanInput input = new PlanInput(List.of(unit(1, Priority.MUST, 100, 2L, 3L, 4L)), products, 30_000, 600);

		List<List<Set<Long>>> subsets = MonthCandidates.budgetSubsets(input);

		// 0번째 달: 구독 중 단품(2)은 후보가 아니고 번들(4)도 빠진다. 웨이브(3)만 남는다
		assertThat(subsets.get(0)).containsExactlyInAnyOrder(ids(), ids(3L));
		// 1·2번째 달: 구독 중 단품도 번들도 후보다. 둘은 서비스가 겹쳐 함께 고르지 않는다
		assertThat(subsets.get(1)).containsExactlyInAnyOrder(ids(), ids(2L), ids(3L), ids(4L), ids(2L, 3L));
	}

	@Test
	@DisplayName("S6 확장: 무료 이용 서비스는 번들 제외 근거가 아니다")
	void 무료_이용_서비스와_겹치는_번들은_후보로_남는다() {
		List<OttProduct> products = List.of(new OttProduct(2, 0, OttProductCondition.FREE, List.of(2L)), bundle(4, 16_000, 2L, 3L));
		PlanInput input = new PlanInput(List.of(unit(1, Priority.MUST, 100, 2L, 4L)), products, 30_000, 600);

		assertThat(MonthCandidates.budgetSubsets(input).get(0)).containsExactlyInAnyOrder(ids(), ids(4L));
	}

	@Test
	@DisplayName("설계 문서 5절 예시: 세 유형 모두 첫 달에 넷플릭스 단품과 번들을 함께 고르고 총비용은 29,500원이다")
	void 설계_문서_예시의_세_유형_결과() {
		PlanInput input = designExample();
		PlanTypeSolver solver = new PlanTypeSolver(new SolverSelector(new ExactSolver(), new GreedySolver(), SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS));

		Map<PlanType, SolveResult> results = solver.solveAll(input);

		for (PlanType type : PlanType.values()) {
			Evaluation evaluation = results.get(type).evaluation();
			assertThat(results.get(type).solverType()).isEqualTo(SolverType.EXACT);
			assertThat(evaluation.mustCompleted()).as("%s 꼭 시청 완료 수", type).isEqualTo(3);
			assertThat(evaluation.totalCost()).as("%s 총비용", type).isEqualTo(29_500);
			assertThat(evaluation.selection()).as("%s 조합", type).isEqualTo(sel(ids(1L, 4L), ids(), ids()));
		}
		// 번들 없이 단품만 있으면(같은 입력에서 번들 제외) 세 작품을 한 달에 다 보는 것은 예산 밖이라 더 비싸다
		PlanInput withoutBundle = new PlanInput(List.of(unit(1, Priority.MUST, 300, 2L), unit(2, Priority.MUST, 300, 3L), unit(3, Priority.MUST, 300, 1L)),
				List.of(single(1, 13_500, 1), single(2, 13_500, 2), single(3, 10_900, 3)), 30_000, 900);
		assertThat(solver.solveAll(withoutBundle).get(PlanType.RECOMMENDED).evaluation().totalCost()).isEqualTo(37_900);
	}

	@Test
	@DisplayName("번들 포함 무작위 입력에서 완전탐색 세 유형 결과가 S1b·S6 확장을 반영한 전수 탐색 최선과 같다")
	void 번들_포함_완전탐색이_오라클과_같다() {
		Random random = new Random(20261017L);
		ExactSolver exact = new ExactSolver();
		long combinations = 0;
		List<String> counterexamples = new ArrayList<>();
		for (int i = 0; i < 120 && counterexamples.isEmpty(); i++) {
			PlanInput input = randomBundleInput(random);
			List<Evaluation> all = BruteForceOracle.allEvaluations(input);
			combinations += all.size();

			Evaluation oracleRecommended = Collections.max(all, BruteForceOracle.comparator(PlanType.RECOMMENDED, input, 0));
			SolveResult exactRecommended = exact.solve(input, PlanObjective.recommended(input));
			if (!exactRecommended.selection().equals(oracleRecommended.selection())) {
				counterexamples.add(PlanType.RECOMMENDED + " " + input + "\n 완전탐색=" + exactRecommended.evaluation() + "\n 전수=" + oracleRecommended);
			}
			for (PlanType type : List.of(PlanType.SAVER, PlanType.SIMPLE)) {
				Evaluation oracleBest = Collections.max(all, BruteForceOracle.comparator(type, input, oracleRecommended.score()));
				PlanObjective objective = SolverCases.objective(type, input, exactRecommended.evaluation());
				SolveResult actual = exact.solve(input, objective);
				if (!actual.selection().equals(oracleBest.selection())) {
					counterexamples.add(type + " " + input + "\n 완전탐색=" + actual.evaluation() + "\n 전수=" + oracleBest);
				}
			}
			// 고른 조합은 어느 달에도 서비스가 겹치는 상품을 함께 담지 않는다
			for (int month = 0; month < 3; month++) {
				assertThat(BruteForceOracle.hasServiceOverlap(exactRecommended.selection().productIdsOf(month), input.products())).isFalse();
			}
		}
		assertThat(counterexamples).as("반례").isEmpty();
		assertThat(combinations).as("전수 탐색한 조합 수").isGreaterThan(5_000);
	}

	@Test
	@DisplayName("번들 3개 + 단품 7개에서 후보 조합 수가 상한을 넘으면 절약형·간편형은 그리디로 바뀐다")
	void 번들이_들어오면_그리디로_전환된다() {
		PlanInput input = largeInput(3);
		ExactSolver exact = new ExactSolver();
		SolverSelector selector = new SolverSelector(exact, new GreedySolver(), SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS);
		Evaluation recommended = new GreedySolver().solve(input, PlanObjective.recommended(input)).evaluation();

		for (PlanType type : List.of(PlanType.SAVER, PlanType.SIMPLE)) {
			PlanObjective objective = SolverCases.objective(type, input, recommended);

			// 서비스 7개를 번들 3개(3+2+2)로 묶으면 달마다 9×5×5 = 225개, 3개월 조합은 225³ ≈ 1,139만으로 상한(2,097,152)을 넘는다
			assertThat(exact.countCandidateCombinations(input, objective)).as("%s 후보 조합 수", type)
					.isEqualTo(225L * 225 * 225).isGreaterThan(SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS);
			assertThat(selector.solve(input, objective).solverType()).as("%s 푼 방식", type).isEqualTo(SolverType.GREEDY);
		}
	}

	@Test
	@DisplayName("번들 13개 + 단품 7개(후보 20개)도 한도(20개)를 넘지 않아 예외 없이 후보를 만든다")
	void 번들_열세_개와_단품_일곱_개도_후보를_만든다() {
		PlanInput input = largeInput(13);

		List<List<Set<Long>>> subsets = MonthCandidates.budgetSubsets(input);

		assertThat(subsets).hasSize(3);
		assertThat(subsets.get(0)).isNotEmpty();
	}

	/**
	 * 서비스 7개의 단품(ID 1~7)과 번들 bundleCount개(ID 11~)를 가진 입력. 번들은 처음 3개가 (1,2,3)·(4,5)·(6,7)이고 이후는 서비스 두 개씩 겹치게 돌려 가며 만든다.
	 * 예산은 모든 조합이 들어가게 크게 잡고, 작품 8개는 상품 일부를 볼 수 있다.
	 */
	private static PlanInput largeInput(int bundleCount) {
		List<OttProduct> products = new ArrayList<>();
		for (long service = 1; service <= 7; service++) {
			products.add(single(service, 5_000 + 500 * (int) service, service));
		}
		List<List<Long>> bundleServices = new ArrayList<>(List.of(List.of(1L, 2L, 3L), List.of(4L, 5L), List.of(6L, 7L)));
		for (long k = 0; bundleServices.size() < bundleCount; k++) {
			bundleServices.add(List.of(1 + k % 7, 1 + (k + 3) % 7));
		}
		List<WatchUnit> units = new ArrayList<>();
		for (int i = 0; i < bundleCount; i++) {
			List<Long> services = bundleServices.get(i);
			products.add(new OttProduct(11 + i, 8_000 + 300 * i, OttProductCondition.NONE, services));
		}
		for (long u = 1; u <= 8; u++) {
			Set<Long> watchable = new HashSet<>();
			watchable.add(1 + (u % 7)); // 단품 하나
			for (OttProduct product : products) {
				if (product.isBundle() && product.ottServiceIds().contains(1 + (u % 7))) {
					watchable.add(product.id()); // 그 서비스를 포함한 번들도 볼 수 있다
				}
			}
			watchable.add(1 + ((u + 2) % 7));
			units.add(new WatchUnit(u, Priority.WANT, 200, watchable, false));
		}
		return new PlanInput(units, products, 200_000, 900);
	}

	/** 서비스 3개(단품 ID 10·20·30)와 번들 0~3개(서비스 쌍 2개짜리와 3개짜리)가 섞인 작은 무작위 입력. 일부 단품은 구독 중·무료다. */
	private static PlanInput randomBundleInput(Random random) {
		int[] prices = {2_000, 3_000, 5_000, 8_000, 10_000};
		List<OttProduct> products = new ArrayList<>();
		for (int service = 1; service <= 3; service++) {
			int roll = random.nextInt(100);
			OttProductCondition condition = roll < 10 ? OttProductCondition.FREE
					: roll < 30 ? OttProductCondition.SUBSCRIBED : OttProductCondition.NONE;
			products.add(new OttProduct(10L * service, prices[random.nextInt(prices.length)], condition, List.of((long) service)));
		}
		List<List<Long>> bundleChoices = List.of(List.of(1L, 2L), List.of(2L, 3L), List.of(1L, 3L), List.of(1L, 2L, 3L));
		int bundleCount = 1 + random.nextInt(3);
		List<List<Long>> shuffled = new ArrayList<>(bundleChoices);
		Collections.shuffle(shuffled, random);
		for (int i = 0; i < bundleCount; i++) {
			products.add(new OttProduct(100L + i, prices[random.nextInt(prices.length)] + 3_000, OttProductCondition.NONE, shuffled.get(i)));
		}
		Collections.shuffle(products, random);

		List<WatchUnit> units = new ArrayList<>();
		int unitCount = 2 + random.nextInt(3);
		Priority[] priorities = Priority.values();
		int[] minutes = {60, 120, 200, 300};
		for (long id = 1; id <= unitCount; id++) {
			Set<Long> watchable = new HashSet<>();
			for (OttProduct product : products) {
				if (random.nextBoolean()) {
					watchable.add(product.id());
				}
			}
			units.add(new WatchUnit(id, priorities[random.nextInt(priorities.length)], minutes[random.nextInt(minutes.length)], watchable, false));
		}
		int[] budgets = {8_000, 13_000, 18_000, 25_000};
		int[] watchMinutes = {150, 300, 600};
		return new PlanInput(units, products, budgets[random.nextInt(budgets.length)], watchMinutes[random.nextInt(watchMinutes.length)]);
	}
}
