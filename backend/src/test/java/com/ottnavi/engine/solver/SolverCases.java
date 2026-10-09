package com.ottnavi.engine.solver;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static com.ottnavi.engine.EngineFixtures.unit;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Solver 테스트가 함께 쓰는 입력 모음: 손으로 정답을 계산한 케이스와 시드 고정 무작위 입력.
 * 손 계산 근거는 각 메서드 주석에 적고, 기대 조합은 {@link #sel}로 만든다.
 */
public final class SolverCases {

	private SolverCases() {
	}

	/** 예산을 지정해 입력을 만든다(EngineFixtures.input은 예산이 고정이라 쓰지 않는다). */
	public static PlanInput in(int budget, int minutes, List<OttProduct> products, WatchUnit... units) {
		return new PlanInput(Arrays.asList(units), products, budget, minutes);
	}

	/** 달별 상품 ID 집합 3개로 조합을 만든다. 예: sel(ids(), ids(1), ids(2, 3)). */
	public static Selection sel(Set<Long> month0, Set<Long> month1, Set<Long> month2) {
		return Selection.of(month0, month1, month2);
	}

	/** 실제 PlanObjective(구현 대상)를 유형별로 만든다. recommendedScore는 절약형·간편형에서만 쓴다. */
	public static com.ottnavi.engine.rule.PlanObjective objective(
			com.ottnavi.engine.model.PlanType type, PlanInput input, com.ottnavi.engine.model.Evaluation recommended) {
		return switch (type) {
			case RECOMMENDED -> com.ottnavi.engine.rule.PlanObjective.recommended(input.products());
			case SAVER -> com.ottnavi.engine.rule.PlanObjective.saver(input.products(), recommended);
			case SIMPLE -> com.ottnavi.engine.rule.PlanObjective.simple(input.products(), recommended);
		};
	}

	/**
	 * 후보 상품 수 한도(S9) 입력: 가격 1,000원 상품 watchable개는 각각 WANT 100분 단위 하나씩을 볼 수 있고, extraUnwatchable개는 아무도 못 본다.
	 * 예산 1,000원이라 한 달에 상품 하나만 고를 수 있고, 후보 상품은 watchable개다.
	 */
	public static PlanInput manyProducts(int watchable, int extraUnwatchable) {
		List<OttProduct> products = new ArrayList<>();
		List<WatchUnit> units = new ArrayList<>();
		for (int i = 1; i <= watchable + extraUnwatchable; i++) {
			products.add(product(i, 1_000));
			if (i <= watchable) {
				units.add(unit(i, Priority.WANT, 100, (long) i));
			}
		}
		return new PlanInput(units, products, 1_000, 600);
	}

	/** 상품 ID 집합. */
	public static Set<Long> ids(Long... ids) {
		return Set.of(ids);
	}

	// ---------------------------------------------------------------- 손 계산 케이스

	/**
	 * 그리디가 나쁜 케이스(명세 5절 3번). 월 600분, 예산 10,000원. p1(id 1, 10,000원), p2(id 2, 8,000원).
	 * X(id 1, WANT, 1000분)는 p1만, Y(id 2, WANT, 300분)는 p2만.
	 */
	public static PlanInput greedyWorse() {
		return in(10_000, 600, List.of(product(1, 10_000), product(2, 8_000)),
				unit(1, Priority.WANT, 1000, 1L), unit(2, Priority.WANT, 300, 2L));
	}

	/**
	 * 완전탐색(추천형): 점수 4(X 2 + Y 2), 비용 28,000원.
	 * 한 달 예산이 10,000원이라 p1+p2(18,000원)를 한 달에 못 담는다. X(1000분)는 p1이 연속 두 달 있어야 600+400분으로 끝나고,
	 * Y는 p2가 남은 한 달에 있어야 한다. 두 배치 (p2,p1,p1)와 (p1,p1,p2)가 점수 4·비용 28,000으로 같고 결제 미루기 [1,1,1]도 같아
	 * ⑤ 상품 ID 사전순으로 0번째 달 {1} < {2}인 (p1,p1,p2)가 남는다. (명세 예시는 0번째 달 p2라고 적었으나 ⑤를 따르면 ID 1이 앞선다.)
	 */
	public static Selection greedyWorseExactSelection() {
		return sel(ids(1L), ids(1L), ids(2L));
	}

	/**
	 * 그리디(추천형): m=0 후보 {}, {p1}, {p2}(p1+p2는 예산 초과). {p1}은 X가 한 달뿐이라 TIME으로 점수 0, {p2}는 Y 시청 완료로 점수 2·8,000원 → {p2} 확정.
	 * m=1: {p2},{},{} (점수 2·8,000원)이 {p2},{p1},{}(X 600분뿐이라 TIME, 16,000원)보다 좋다 → {}. m=2도 같은 이유로 {}.
	 * 결과 ({2},{},{}) 점수 2·비용 8,000원.
	 */
	public static Selection greedyWorseGreedySelection() {
		return sel(ids(2L), ids(), ids());
	}

	/**
	 * T20 지배 제거 동점 + 결제 미루기: 같은 가격 9,000원 단품 p2·p1(목록 순서는 일부러 p2 먼저)이 같은 WANT 200분 하나를 둘 다 볼 수 있다. 예산 10,000원.
	 * 점수 2는 어느 달에 어느 하나를 골라도 같고 비용 9,000원도 같다. ④ 결제를 미루는 쪽이 이기므로 2번째 달, ⑤로 ID가 작은 p1이 남는다.
	 */
	public static PlanInput tiePair() {
		return in(10_000, 600, List.of(product(2, 9_000), product(1, 9_000)), unit(1, Priority.WANT, 200, 1L, 2L));
	}

	public static Selection tiePairSelection() {
		return sel(ids(), ids(), ids(1L));
	}

	/**
	 * SUBSCRIBED 시작: 구독 중인 s(id 1, 50,000원)가 예산(10,000원)보다 비싸지만 0번째 달 비용은 0원이다.
	 * WANT 200분 하나를 s로 본다. 선택하지 않아도 0번째 달에 볼 수 있어 점수 2·비용 0이고, 같은 조건에서 ⑤로 빈 집합이 {1}보다 앞서 전부 빈 조합이다.
	 */
	public static PlanInput subscribedStart() {
		return in(10_000, 600, List.of(subscribed(1, 50_000)), unit(1, Priority.WANT, 200, 1L));
	}

	/**
	 * SUBSCRIBED인데 1번째 달이 필요한 긴 시즌: s로만 볼 수 있는 1000분(월 600분) 시즌은 0·1번째 달이 모두 필요하다.
	 * 1번째 달에 s를 고르면 50,000원이라 예산(10,000원)을 넘어 못 고른다 → 시청 완료 불가, 점수 0, 전부 빈 조합.
	 */
	public static PlanInput subscribedMonthOneOverBudget() {
		return in(10_000, 600, List.of(subscribed(1, 50_000)), unit(1, Priority.WANT, 1000, 1L));
	}

	/**
	 * FREE 상품: f(id 3, 무료)는 선택하지 않아도 모든 달에 본다. p(id 2, 5,000원). A(WANT 200)는 f·p 둘 다, B(WANT 200)는 p만. 예산 10,000원.
	 * B 때문에 p가 한 번 필요하다. 가장 늦은 2번째 달에 p만 고르면 점수 4·비용 5,000원.
	 * FREE는 S6에 따라 후보가 아니므로 결과에 나타나지 않는다. 기대 ({},{},{2}).
	 */
	public static PlanInput freeProduct() {
		return in(10_000, 600, List.of(free(3), product(2, 5_000)),
				unit(1, Priority.WANT, 200, 3L, 2L), unit(2, Priority.WANT, 200, 2L));
	}

	public static Selection freeProductSelection() {
		return sel(ids(), ids(), ids(2L));
	}

	/**
	 * FREE 상품의 ID가 더 작은 경우: f(id 1, 무료), p(id 2, 5,000원). 입력은 freeProduct와 같다.
	 * 후보에 남겼다면 ⑤ 사전순에서 [1,2]가 [2]보다 앞서 f를 함께 고른 조합이 이겼을 것이다. S6은 FREE를 후보에서 빼므로 기대는 ({},{},{2})다.
	 */
	public static PlanInput freeProductLowId() {
		return in(10_000, 600, List.of(free(1), product(2, 5_000)),
				unit(1, Priority.WANT, 200, 1L, 2L), unit(2, Priority.WANT, 200, 2L));
	}

	public static Selection freeProductLowIdSelection() {
		return sel(ids(), ids(), ids(2L));
	}

	/**
	 * 동치 제거 회귀(성질 16): 월 300분, 예산 1,000원, pA(id 1)·pB(id 2) 각 1,000원.
	 * A(MUST 200, pB만), B(MAYBE 300, pB만), C(MAYBE 400, pA·pB). 배정 순서 A, B, C.
	 * [{1},{2},{2}]: A는 1번째 달(200, 남은 100), B는 2번째 달 300, C는 0번째 달(pA) 300 + 1번째 달 남은 100 = 400으로 1번째 달에 시청 완료.
	 * must 1, score 2(B 1 + C 1), 비용 3,000원. 점수 최대가 2이고 pB를 두 달(A와 B가 같은 달에 못 들어감) 써야 하며,
	 * pB만 두 달 쓰면 C의 이어 보기가 결정 4(남은 100분 < 120분)로 막히므로 pA 한 달이 더 필요하다. 옛 제거식은 {1}을 지워 score 1이 된다.
	 */
	public static PlanInput equivalenceRegression() {
		return in(1_000, 300, List.of(product(1, 1_000), product(2, 1_000)),
				unit(1, Priority.MUST, 200, 2L), unit(2, Priority.MAYBE, 300, 2L), unit(3, Priority.MAYBE, 400, 1L, 2L));
	}

	public static Selection equivalenceRegressionSelection() {
		return sel(ids(1L), ids(2L), ids(2L));
	}

	/**
	 * 결정 4(한 달 최소 120분): 월 600분, p1(id 1, 10,000원)으로만 Y(MUST 500분)·S(WANT 1000분)를 본다. 예산 10,000원.
	 * p1을 0·1번째 달에만 고르면 Y가 0번째 달 500분을 쓰고 남은 100분(120분 미만)이라 S를 못 이어 본다.
	 * 0·1·2번째 달 모두 고르면 S가 1번째 달 600분 + 2번째 달 400분으로 끝난다. MUST 1, 점수 2, 비용 30,000원, 조합 ({1},{1},{1}).
	 */
	public static PlanInput decisionFour() {
		return in(10_000, 600, List.of(product(1, 10_000)),
				unit(1, Priority.MUST, 500, 1L), unit(2, Priority.WANT, 1000, 1L));
	}

	public static Selection decisionFourSelection() {
		return sel(ids(1L), ids(1L), ids(1L));
	}

	/**
	 * 결정 1(실제 시청 달도 연속): 월 600분, q(id 1, 5,000원), p(id 2, 5,000원). X(MUST 600)는 q만, S(WANT 1200)는 p만. 예산 10,000원.
	 * X가 한 달 600분을 다 쓰고 S는 남은 두 달 600+600분으로 끝나야 하므로 q 한 번, p 두 번이 필요하고 15,000원이다.
	 * 배치는 (q,p,p)와 (p,p,q)가 점수 2·MUST 1·15,000원·결제 [1,1,1]로 같아 ⑤로 0번째 달 {1} < {2}인 (q,p,p)가 남는다. 기대 ({1},{2},{2}).
	 * (q를 p와 같은 달에 두면 그 달 600분을 X가 써서 S가 이어지지 않는다.)
	 */
	public static PlanInput decisionOne() {
		return in(10_000, 600, List.of(product(1, 5_000), product(2, 5_000)),
				unit(1, Priority.MUST, 600, 1L), unit(2, Priority.WANT, 1200, 2L));
	}

	public static Selection decisionOneSelection() {
		return sel(ids(1L), ids(2L), ids(2L));
	}

	/**
	 * 절약형·간편형이 추천형·서로와 다른 케이스 1(SV): a(id 1, 6,000원), b(id 2, 3,000원), 예산 10,000원, 월 600분.
	 * U1·U2·U3(WANT 100분)는 a만, U4(MAYBE 100분)는 b만. 한 달에 a+b(9,000원)를 담을 수 있다.
	 * 추천형: 점수 7(2+2+2+1), 최소 비용 a+b = 9,000원, 결제를 가장 늦게(둘 다 2번째 달) → ({},{},{1,2}).
	 * 하한 = ceil(7×0.7) = 5. 절약형: a만으로 점수 6 ≥ 5, 6,000원 → ({},{},{1}). 간편형: a만 결제 1건 → ({},{},{1}).
	 */
	public static PlanInput saverAndSimpleDropB() {
		return in(10_000, 600, List.of(product(1, 6_000), product(2, 3_000)),
				unit(1, Priority.WANT, 100, 1L), unit(2, Priority.WANT, 100, 1L), unit(3, Priority.WANT, 100, 1L),
				unit(4, Priority.MAYBE, 100, 2L));
	}

	public static Selection saverAndSimpleDropBRecommended() {
		return sel(ids(), ids(), ids(1L, 2L));
	}

	public static Selection saverAndSimpleDropBSelection() {
		return sel(ids(), ids(), ids(1L));
	}

	/**
	 * 절약형과 간편형이 서로 다른 케이스(SS): P(id 1, 9,000원), Q(id 2, 2,000원), R(id 3, 2,000원). 예산 10,000원, 월 600분.
	 * U1(WANT 100)은 P 또는 Q로, U2(WANT 100)는 P 또는 R로 볼 수 있다.
	 * 점수 4는 P 하나(9,000원, 결제 1건) 또는 Q+R(4,000원, 결제 2건)로 얻는다.
	 * 추천형: 비용 4,000원인 Q+R을 가장 늦게 → ({},{},{2,3}), 점수 4. 하한 = ceil(2.8) = 3이라 점수 4만 통과한다.
	 * 절약형: 비용 최소 → 추천형과 같은 ({},{},{2,3}). 간편형: 결제 1건인 P를 가장 늦게 → ({},{},{1}) (비용 9,000원).
	 */
	public static PlanInput saverVsSimple() {
		return in(10_000, 600, List.of(product(1, 9_000), product(2, 2_000), product(3, 2_000)),
				unit(1, Priority.WANT, 100, 1L, 2L), unit(2, Priority.WANT, 100, 1L, 3L));
	}

	public static Selection saverVsSimpleRecommended() {
		return sel(ids(), ids(), ids(2L, 3L));
	}

	public static Selection saverVsSimpleSimple() {
		return sel(ids(), ids(), ids(1L));
	}

	/**
	 * saverVsSimple의 추천형 그리디(S4): 결제를 미루지 못한다(0번째 달부터 확정).
	 * m=0 후보 중 점수 4가 {1}(9,000원)과 {2,3}(4,000원)이라 비용이 낮은 {2,3} → ({2,3},{},{}).
	 */
	public static Selection saverVsSimpleGreedyRecommended() {
		return sel(ids(2L, 3L), ids(), ids());
	}

	/**
	 * saverVsSimple의 절약형 그리디(S8): baseline ({},{},{2,3})(점수 4, 4,000원)에서 출발한다. 어느 달을 바꿔도 비용이 늘거나 점수 하한(3) 미만이라
	 * 바뀌지 않는다 → baseline 그대로.
	 */
	public static Selection saverVsSimpleGreedySaver() {
		return sel(ids(), ids(), ids(2L, 3L));
	}

	/**
	 * saverVsSimple의 간편형 그리디(S8): baseline ({},{},{2,3})(가입 2회)에서 출발한다. 0·1번째 달을 바꾸면 가입 횟수가 늘어 그대로이고,
	 * 2번째 달을 {1}로 바꾸면 점수 4 ≥ 하한 3에 가입 1회로 좋아져 교체한다. 두 번째 바퀴에서는 바뀐 달이 없다 → ({},{},{1}).
	 */
	public static Selection saverVsSimpleGreedySimple() {
		return sel(ids(), ids(), ids(1L));
	}

	/** 시청 가능 상품이 없는 단위(NO_PROVIDER·UNKNOWN)는 조합에 영향이 없다: 상품 p(id 1, 5,000원)와 A(WANT 200, p), B(WANT 200, 없음), C(MAYBE 200, 없음, 모름). */
	public static PlanInput withUnwatchableUnits() {
		return in(10_000, 600, List.of(product(1, 5_000)), unit(1, Priority.WANT, 200, 1L),
				unit(2, Priority.WANT, 200), com.ottnavi.engine.EngineFixtures.unknownUnit(3, Priority.MAYBE, 200));
	}

	/** 상품이 하나뿐이고 예산이 넉넉하며 작품 하나: 세 유형의 조합이 모두 ({},{},{1}). 점수 2·5,000원이 하한 2를 넘고 결제 1건이라 같다. */
	public static PlanInput singleProduct() {
		return in(10_000, 600, List.of(product(1, 5_000)), unit(1, Priority.WANT, 200, 1L));
	}

	public static Selection singleProductSelection() {
		return sel(ids(), ids(), ids(1L));
	}

	/** 예산 0원: 유료 상품 p(id 1)는 어느 달에도 못 고르고 FREE f(id 2)만 가능하다. A(WANT 200)는 p만, B(WANT 200)는 f로 볼 수 있다. 점수 2, 비용 0, 전부 빈 조합. */
	public static PlanInput zeroBudget() {
		return in(0, 600, List.of(product(1, 5_000), free(2)),
				unit(1, Priority.WANT, 200, 1L), unit(2, Priority.WANT, 200, 2L));
	}

	/** 손 계산 케이스 전체(오라클·성질 테스트에도 쓴다). */
	public static List<PlanInput> handCases() {
		return List.of(greedyWorse(), tiePair(), subscribedStart(), subscribedMonthOneOverBudget(), freeProduct(), freeProductLowId(),
				decisionFour(), decisionOne(), saverAndSimpleDropB(), saverVsSimple(), withUnwatchableUnits(),
				singleProduct(), zeroBudget(), equivalenceRegression());
	}

	// ---------------------------------------------------------------- 시드 고정 무작위 입력

	private static final int[] PRICES = {2_000, 3_000, 5_000, 8_000, 10_000};
	private static final int[] BUDGETS = {5_000, 8_000, 10_000, 13_000, 20_000};
	private static final int[] MINUTES = {100, 200, 300, 500, 700, 1000, 1300};
	private static final int[] MONTHLY_MINUTES = {300, 600};

	/** 시드로 재현되는 작은 무작위 입력(상품 3~4개, 단위 1~6개). 같은 시드는 항상 같은 입력이다. */
	public static List<PlanInput> randomInputs(int count, long seed) {
		Random random = new Random(seed);
		List<PlanInput> inputs = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			inputs.add(randomInput(random));
		}
		return inputs;
	}

	private static PlanInput randomInput(Random random) {
		int productCount = 3 + random.nextInt(2);
		List<OttProduct> products = new ArrayList<>();
		for (int i = 0; i < productCount; i++) {
			int roll = random.nextInt(10);
			OttProductCondition condition = roll == 0 ? OttProductCondition.FREE
					: roll <= 1 ? OttProductCondition.SUBSCRIBED : OttProductCondition.NONE;
			products.add(new OttProduct(10L * (i + 1), PRICES[random.nextInt(PRICES.length)], condition));
		}
		Collections.shuffle(products, random);
		int unitCount = 1 + random.nextInt(6);
		List<WatchUnit> units = new ArrayList<>();
		for (int i = 0; i < unitCount; i++) {
			Set<Long> watchable = new java.util.HashSet<>();
			if (random.nextInt(10) != 0) { // 10%는 시청 가능 상품이 없는 단위
				for (OttProduct product : products) {
					if (random.nextInt(3) == 0) {
						watchable.add(product.id());
					}
				}
			}
			Priority priority = Priority.values()[random.nextInt(Priority.values().length)];
			units.add(new WatchUnit(i + 1, priority, MINUTES[random.nextInt(MINUTES.length)], watchable, random.nextBoolean()));
		}
		Collections.shuffle(units, random);
		return new PlanInput(units, products, BUDGETS[random.nextInt(BUDGETS.length)], MONTHLY_MINUTES[random.nextInt(MONTHLY_MINUTES.length)]);
	}

	/** 입력의 단위 순서만 섞은 같은 입력(결정성 테스트). */
	public static PlanInput shuffledUnits(PlanInput input, long seed) {
		List<WatchUnit> units = new ArrayList<>(input.units());
		Collections.shuffle(units, new Random(seed));
		return new PlanInput(units, input.products(), input.monthlyBudget(), input.monthlyWatchMinutes());
	}
}
