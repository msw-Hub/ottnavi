package com.ottnavi.engine.rule;

import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.selection;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.solver.BruteForceOracle;
import com.ottnavi.engine.solver.SolverCases;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 유형별 목적함수 테스트(명세 3절, 5절 11·18). 반환 규칙은 "양수 = 첫 번째가 더 좋다"이다.
 */
class PlanObjectiveTest {

	private static final List<OttProduct> PRODUCTS = List.of(product(1, 9_000), product(2, 1_000), product(3, 1_000), subscribed(4, 1_000));
	private static final Selection EMPTY = selection(Set.of(), Set.of(), Set.of());

	private static Evaluation evaluation(Selection selection, int mustCompleted, int score, int totalCost) {
		return new Evaluation(selection, mustCompleted, score, totalCost, List.of());
	}

	/** 추천형 결과 대역: 점수만 하한 계산에 쓰이고 조합은 baseline으로 담긴다. */
	private static Evaluation recommended(int score) {
		return evaluation(EMPTY, 0, score, 0);
	}

	private static Comparator<Evaluation> saver(int recommendedScore) {
		return PlanObjective.saver(PRODUCTS, recommended(recommendedScore)).comparator();
	}

	private static Comparator<Evaluation> simple(int recommendedScore) {
		return PlanObjective.simple(PRODUCTS, recommended(recommendedScore)).comparator();
	}

	@Test
	@DisplayName("scoreFloor: 0→0, 1→1, 3→3, 5→4, 10→7, 20→14 (올림 70%)")
	void 점수_하한() {
		int[][] cases = {{0, 0}, {1, 1}, {3, 3}, {5, 4}, {10, 7}, {20, 14}};
		for (int[] c : cases) {
			assertThat(PlanObjective.scoreFloor(c[0])).as("score=%d", c[0]).isEqualTo(c[1]);
		}
	}

	@Test
	@DisplayName("scoreFloor: 큰 점수에서도 올림 70%와 같고 점수보다 커지지 않는다")
	void 점수_하한_범위() {
		for (int score = 0; score <= 200; score++) {
			int expected = (int) Math.ceil(score * 7 / 10.0); // 7×score는 정수라 10.0 나눗셈이 정확히 올림된다
			assertThat(PlanObjective.scoreFloor(score)).as("score=%d", score).isEqualTo(expected);
			assertThat(PlanObjective.scoreFloor(score)).isLessThanOrEqualTo(score);
		}
	}

	@Test
	@DisplayName("scoreFloor: 음수는 IllegalArgumentException")
	void 점수_하한_음수는_예외() {
		assertThatThrownBy(() -> PlanObjective.scoreFloor(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlanObjective.scoreFloor(Integer.MIN_VALUE)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("팩토리는 유형을 올바르게 담고, 추천형은 baseline이 없고 절약형·간편형은 추천형 조합을 baseline으로 담는다")
	void 팩토리_유형과_baseline() {
		Selection recommendedSelection = selection(Set.of(), Set.of(2L), Set.of(2L));
		Evaluation recommendedEvaluation = evaluation(recommendedSelection, 1, 5, 2_000);

		PlanObjective recommended = PlanObjective.recommended(PRODUCTS);
		PlanObjective saver = PlanObjective.saver(PRODUCTS, recommendedEvaluation);
		PlanObjective simple = PlanObjective.simple(PRODUCTS, recommendedEvaluation);

		assertThat(recommended.planType()).isEqualTo(PlanType.RECOMMENDED);
		assertThat(recommended.baseline()).isNull();
		assertThat(saver.planType()).isEqualTo(PlanType.SAVER);
		assertThat(saver.baseline()).isEqualTo(recommendedSelection);
		assertThat(simple.planType()).isEqualTo(PlanType.SIMPLE);
		assertThat(simple.baseline()).isEqualTo(recommendedSelection);
	}

	@Test
	@DisplayName("팩토리는 추천형 점수로 하한을 정한다: 점수 10이면 하한 7")
	void 팩토리의_하한() {
		Evaluation ok = evaluation(EMPTY, 0, 7, 5_000);
		Evaluation below = evaluation(EMPTY, 0, 6, 0);

		assertThat(saver(10).compare(ok, below)).isPositive();
		assertThat(simple(10).compare(ok, below)).isPositive();
		assertThat(saver(5).compare(below, ok)).isPositive(); // 점수 5 → 하한 4, 둘 다 통과해 비용이 싼 쪽
	}

	@Test
	@DisplayName("세 유형 모두 ① 꼭 완주 수가 많은 쪽이 점수와 비용보다 우선한다")
	void 꼭_완주_수_우선() {
		Evaluation more = evaluation(EMPTY, 1, 0, 50_000);
		Evaluation less = evaluation(EMPTY, 0, 10, 0);

		assertThat(saver(10).compare(more, less)).isPositive();
		assertThat(simple(10).compare(more, less)).isPositive();
		assertThat(saver(10).compare(less, more)).isNegative();
		assertThat(simple(10).compare(less, more)).isNegative();
	}

	@Test
	@DisplayName("절약형·간편형 ②: 하한(추천형 10 → 7) 이상이면 더 높은 점수가 비싸도 이긴다")
	void 하한_이상이면_비용으로_비교() {
		Evaluation enoughAndCheap = evaluation(EMPTY, 0, 7, 5_000);
		Evaluation higherScoreButExpensive = evaluation(EMPTY, 0, 10, 9_000);

		assertThat(saver(10).compare(enoughAndCheap, higherScoreButExpensive)).isPositive();
		assertThat(simple(10).compare(enoughAndCheap, higherScoreButExpensive)).isPositive();
	}

	@Test
	@DisplayName("절약형·간편형 ②: 하한 미만이면 아무리 싸도 하한 이상인 쪽에 진다")
	void 하한_미만이면_진다() {
		Evaluation belowFloor = evaluation(EMPTY, 0, 6, 0);
		Evaluation atFloor = evaluation(EMPTY, 0, 7, 9_000);

		assertThat(saver(10).compare(belowFloor, atFloor)).isNegative();
		assertThat(saver(10).compare(atFloor, belowFloor)).isPositive();
		assertThat(simple(10).compare(belowFloor, atFloor)).isNegative();
		assertThat(simple(10).compare(atFloor, belowFloor)).isPositive();
	}

	@Test
	@DisplayName("절약형 ③: 둘 다 하한 미만이면 ②는 같고 비용이 낮은 쪽이 이긴다")
	void 둘_다_하한_미만이면_비용() {
		Evaluation cheaper = evaluation(EMPTY, 0, 5, 1_000);
		Evaluation higherScore = evaluation(EMPTY, 0, 6, 2_000);

		assertThat(saver(10).compare(cheaper, higherScore)).isPositive();
		assertThat(saver(10).compare(higherScore, cheaper)).isNegative();
	}

	@Test
	@DisplayName("절약형 ④: 비용이 같으면 점수가 높은 쪽이 이긴다")
	void 비용이_같으면_점수() {
		Evaluation score8 = evaluation(EMPTY, 0, 8, 3_000);
		Evaluation score9 = evaluation(EMPTY, 0, 9, 3_000);

		assertThat(saver(10).compare(score9, score8)).isPositive();
		assertThat(saver(10).compare(score8, score9)).isNegative();
	}

	@Test
	@DisplayName("절약형은 비용을, 간편형은 가입 횟수를 먼저 본다")
	void 절약형과_간편형의_차이() {
		// A: 0번째 달 {1} 9,000원, 가입 1회 / B: 0번째 달 {2}, 2번째 달 {2} 1,000원씩, 갈아 끼워 가입 2회. 점수는 둘 다 하한 이상
		Evaluation oneJoin = evaluation(selection(Set.of(1L), Set.of(), Set.of()), 0, 10, 9_000);
		Evaluation twoJoins = evaluation(selection(Set.of(2L), Set.of(), Set.of(2L)), 0, 10, 2_000);

		assertThat(saver(10).compare(twoJoins, oneJoin)).isPositive();
		assertThat(simple(10).compare(oneJoin, twoJoins)).isPositive();
		assertThat(simple(10).compare(twoJoins, oneJoin)).isNegative();
	}

	@Test
	@DisplayName("가입 횟수 정의(성질 18): 연속 유지는 1회, 갈아타기는 3회라서 연속 유지가 비용이 비싸도 간편형에서 이긴다")
	void 연속_유지_대_갈아타기() {
		Evaluation keep = evaluation(selection(Set.of(1L), Set.of(1L), Set.of(1L)), 0, 10, 27_000); // 가입 1회
		Evaluation switching = evaluation(selection(Set.of(1L), Set.of(2L), Set.of(3L)), 0, 10, 11_000); // 가입 3회

		assertThat(simple(10).compare(keep, switching)).isPositive();
		assertThat(simple(10).compare(switching, keep)).isNegative();
		assertThat(saver(10).compare(switching, keep)).isPositive(); // 절약형은 비용만 본다
	}

	@Test
	@DisplayName("가입 횟수 정의(성질 18): 중간에 빠졌다 다시 선택하면 다시 센다. 결제 건수는 같아도 연속 유지가 이긴다")
	void 빠졌다_다시_선택() {
		// 둘 다 새로 돈이 드는 상품 수는 2건. 가입 횟수는 연속(1회) 대 중간에 빠짐(2회). 비용·점수가 같고 결제 미루기만 보면 [1,0,1]이 [1,1,0]보다 앞서므로
		// 가입 횟수 ③이 없으면 빠졌다 다시 선택한 쪽이 이긴다
		Evaluation consecutive = evaluation(selection(Set.of(2L), Set.of(2L), Set.of()), 0, 10, 2_000);
		Evaluation gap = evaluation(selection(Set.of(2L), Set.of(), Set.of(2L)), 0, 10, 2_000);

		assertThat(simple(10).compare(consecutive, gap)).isPositive();
		assertThat(simple(10).compare(gap, consecutive)).isNegative();
		assertThat(saver(10).compare(gap, consecutive)).isPositive(); // 절약형은 결제 미루기로 가른다
	}

	@Test
	@DisplayName("가입 횟수 정의(성질 18): 이미 구독 중인 상품을 1번째 달에 선택하면 가입 0회다")
	void 구독_중_상품_선택은_가입_0회() {
		// 상품 4는 SUBSCRIBED. 1번째 달에 4를 선택하면 가입 0회, 상품 2를 선택하면 가입 1회. 둘 다 새로 돈이 드는 상품 수는 1건이다
		Evaluation keepSubscribed = evaluation(selection(Set.of(), Set.of(4L), Set.of()), 0, 10, 1_000);
		Evaluation newJoin = evaluation(selection(Set.of(), Set.of(2L), Set.of()), 0, 10, 1_000);

		assertThat(simple(10).compare(keepSubscribed, newJoin)).isPositive();
		assertThat(simple(10).compare(newJoin, keepSubscribed)).isNegative();
	}

	@Test
	@DisplayName("간편형 ④·⑤: 가입 횟수가 같으면 비용이 낮은 쪽, 비용도 같으면 점수가 높은 쪽이 이긴다")
	void 간편형_가입_횟수_같으면_비용_점수() {
		Evaluation join1 = evaluation(selection(Set.of(1L), Set.of(), Set.of()), 0, 10, 9_000);
		Evaluation join2 = evaluation(selection(Set.of(2L), Set.of(), Set.of()), 0, 10, 1_000);
		assertThat(simple(10).compare(join2, join1)).isPositive();

		Evaluation score9 = evaluation(selection(Set.of(2L), Set.of(), Set.of()), 0, 9, 1_000);
		assertThat(simple(10).compare(join2, score9)).isPositive();
	}

	@Test
	@DisplayName("절약형·간편형 마지막 기준: 결제 미루기, 그다음 달별 상품 ID 사전순")
	void 결제_미루기와_상품_ID() {
		Evaluation late = evaluation(selection(Set.of(), Set.of(), Set.of(2L)), 0, 10, 1_000);
		Evaluation early = evaluation(selection(Set.of(2L), Set.of(), Set.of()), 0, 10, 1_000);
		assertThat(saver(10).compare(late, early)).isPositive();
		assertThat(simple(10).compare(late, early)).isPositive();

		Evaluation smallerId = evaluation(selection(Set.of(2L), Set.of(), Set.of()), 0, 10, 1_000);
		Evaluation largerId = evaluation(selection(Set.of(3L), Set.of(), Set.of()), 0, 10, 1_000);
		assertThat(saver(10).compare(smallerId, largerId)).isPositive();
		assertThat(simple(10).compare(smallerId, largerId)).isPositive();
		assertThat(saver(10).compare(smallerId, smallerId)).isZero();
		assertThat(simple(10).compare(smallerId, smallerId)).isZero();
	}

	@Test
	@DisplayName("추천형 점수가 0이면 하한이 0이라 절약형은 비용만 본다")
	void 하한_0() {
		Evaluation zeroScoreFree = evaluation(EMPTY, 0, 0, 0);
		Evaluation score2 = evaluation(EMPTY, 0, 2, 1_000);

		assertThat(saver(0).compare(zeroScoreFree, score2)).isPositive();
		assertThat(simple(0).compare(zeroScoreFree, score2)).isPositive();
	}

	@Test
	@DisplayName("비교기는 반대칭이다: compare(a,b)와 compare(b,a)의 부호가 반대")
	void 반대칭() {
		Selection[] selections = {EMPTY, selection(Set.of(1L), Set.of(), Set.of()), selection(Set.of(2L), Set.of(3L), Set.of()),
				selection(Set.of(), Set.of(), Set.of(1L, 2L)), selection(Set.of(), Set.of(4L), Set.of(4L))};
		List<Evaluation> evaluations = new ArrayList<>();
		for (Selection s : selections) {
			for (int must = 0; must <= 1; must++) {
				for (int score : new int[] {0, 6, 7, 10}) {
					for (int cost : new int[] {0, 2_000, 9_000}) {
						evaluations.add(evaluation(s, must, score, cost));
					}
				}
			}
		}
		for (Comparator<Evaluation> comparator : List.of(saver(10), simple(10))) {
			for (Evaluation a : evaluations) {
				for (Evaluation b : evaluations) {
					assertThat(Integer.signum(comparator.compare(a, b))).isEqualTo(-Integer.signum(comparator.compare(b, a)));
				}
			}
		}
	}

	@Test
	@DisplayName("실제 비교기는 명세 3절을 독립 구현한 오라클 비교기와 모든 표본 쌍에서 부호가 같다")
	void 독립_구현_비교기와_부호가_같다() {
		List<PlanInput> inputs = new ArrayList<>(SolverCases.randomInputs(12, 20261012L));
		inputs.add(SolverCases.saverVsSimple());
		inputs.add(SolverCases.decisionOne());
		Random random = new Random(7);

		for (PlanInput input : inputs) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			List<Evaluation> all = BruteForceOracle.allEvaluations(input);
			for (PlanType type : PlanType.values()) {
				Comparator<Evaluation> actual = switch (type) {
					case RECOMMENDED -> PlanObjective.recommended(input.products()).comparator();
					case SAVER -> PlanObjective.saver(input.products(), recommended).comparator();
					case SIMPLE -> PlanObjective.simple(input.products(), recommended).comparator();
				};
				Comparator<Evaluation> expected = BruteForceOracle.comparator(type, input.products(), recommended.score());
				for (int i = 0; i < 3_000; i++) {
					Evaluation a = all.get(random.nextInt(all.size()));
					Evaluation b = all.get(random.nextInt(all.size()));

					assertThat(Integer.signum(actual.compare(a, b))).as("%s %s vs %s", type, a.selection(), b.selection())
							.isEqualTo(Integer.signum(expected.compare(a, b)));
				}
			}
		}
	}

	@Test
	@DisplayName("생성자: 유형·비교기가 null이거나 절약형·간편형의 baseline이 null이면 IllegalArgumentException")
	void 생성자_검증() {
		Comparator<Evaluation> comparator = (a, b) -> 0;

		assertThatThrownBy(() -> new PlanObjective(null, comparator, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PlanObjective(PlanType.SAVER, null, EMPTY)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PlanObjective(PlanType.SAVER, comparator, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PlanObjective(PlanType.SIMPLE, comparator, null)).isInstanceOf(IllegalArgumentException.class);
		assertThat(new PlanObjective(PlanType.RECOMMENDED, comparator, null).baseline()).isNull();
		assertThat(new PlanObjective(PlanType.SAVER, comparator, EMPTY).baseline()).isEqualTo(EMPTY);
	}
}
