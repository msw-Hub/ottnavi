package com.ottnavi.engine.solver;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.solver.SolverCases.ids;
import static com.ottnavi.engine.solver.SolverCases.sel;
import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오라클 자체 검증: 손으로 푼 정답과 오라클이 일치해야 한다. Solver 구현과 무관하게 항상 통과해야 하는 테스트다.
 */
class BruteForceOracleTest {

	private static Evaluation best(PlanInput input, PlanType type, int recommendedScore) {
		return BruteForceOracle.best(input, type, recommendedScore);
	}

	@Test
	@DisplayName("오라클: 그리디가 나쁜 케이스의 완전탐색 결과는 점수 4·비용 28,000원, 조합은 ({1},{1},{2})다")
	void 그리디_나쁜_케이스_완전탐색() {
		Evaluation exact = BruteForceOracle.bestRecommended(SolverCases.greedyWorse());

		assertThat(exact.score()).isEqualTo(4);
		assertThat(exact.totalCost()).isEqualTo(28_000);
		assertThat(exact.selection()).isEqualTo(SolverCases.greedyWorseExactSelection());
	}

	@Test
	@DisplayName("오라클: 그리디가 나쁜 케이스의 그리디는 점수 2·비용 8,000원이다")
	void 그리디_나쁜_케이스_그리디() {
		PlanInput input = SolverCases.greedyWorse();

		Evaluation greedy = BruteForceOracle.greedy(input, BruteForceOracle.comparator(PlanType.RECOMMENDED, input.products(), 0));

		assertThat(greedy.score()).isEqualTo(2);
		assertThat(greedy.totalCost()).isEqualTo(8_000);
		assertThat(greedy.selection()).isEqualTo(SolverCases.greedyWorseGreedySelection());
	}

	@Test
	@DisplayName("오라클: 손 계산 추천형 정답과 일치한다")
	void 손_계산_추천형() {
		assertThat(BruteForceOracle.bestRecommended(SolverCases.tiePair()).selection()).isEqualTo(SolverCases.tiePairSelection());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.freeProduct()).selection()).isEqualTo(SolverCases.freeProductSelection());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.freeProductLowId()).selection()).isEqualTo(SolverCases.freeProductLowIdSelection());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.decisionFour()).selection()).isEqualTo(SolverCases.decisionFourSelection());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.decisionOne()).selection()).isEqualTo(SolverCases.decisionOneSelection());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.singleProduct()).selection()).isEqualTo(SolverCases.singleProductSelection());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.saverAndSimpleDropB()).selection())
				.isEqualTo(SolverCases.saverAndSimpleDropBRecommended());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.saverVsSimple()).selection())
				.isEqualTo(SolverCases.saverVsSimpleRecommended());

		Selection empty = sel(ids(), ids(), ids());
		assertThat(BruteForceOracle.bestRecommended(SolverCases.subscribedStart()).selection()).isEqualTo(empty);
		assertThat(BruteForceOracle.bestRecommended(SolverCases.subscribedStart()).totalCost()).isZero();
		assertThat(BruteForceOracle.bestRecommended(SolverCases.subscribedMonthOneOverBudget()).selection()).isEqualTo(empty);
		assertThat(BruteForceOracle.bestRecommended(SolverCases.subscribedMonthOneOverBudget()).score()).isZero();
		assertThat(BruteForceOracle.bestRecommended(SolverCases.zeroBudget()).selection()).isEqualTo(empty);
		assertThat(BruteForceOracle.bestRecommended(SolverCases.zeroBudget()).score()).isEqualTo(2);
	}

	@Test
	@DisplayName("오라클: 동치 제거 회귀 입력(성질 16)의 최선은 [{1},{2},{2}], must 1·score 2·비용 3,000원이다")
	void 동치_제거_회귀_검산() {
		Evaluation best = BruteForceOracle.bestRecommended(SolverCases.equivalenceRegression());

		assertThat(best.selection()).isEqualTo(SolverCases.equivalenceRegressionSelection());
		assertThat(best.mustCompleted()).isEqualTo(1);
		assertThat(best.score()).isEqualTo(2);
		assertThat(best.totalCost()).isEqualTo(3_000);
	}

	@Test
	@DisplayName("오라클: 손 계산 절약형·간편형 정답과 일치한다")
	void 손_계산_절약형_간편형() {
		PlanInput dropB = SolverCases.saverAndSimpleDropB();
		assertThat(best(dropB, PlanType.SAVER, 7).selection()).isEqualTo(SolverCases.saverAndSimpleDropBSelection());
		assertThat(best(dropB, PlanType.SIMPLE, 7).selection()).isEqualTo(SolverCases.saverAndSimpleDropBSelection());

		PlanInput vs = SolverCases.saverVsSimple();
		assertThat(best(vs, PlanType.SAVER, 4).selection()).isEqualTo(SolverCases.saverVsSimpleRecommended());
		Evaluation simple = best(vs, PlanType.SIMPLE, 4);
		assertThat(simple.selection()).isEqualTo(SolverCases.saverVsSimpleSimple());
		assertThat(simple.totalCost()).isEqualTo(9_000);
	}

	@Test
	@DisplayName("오라클: 손 계산 그리디 정답(추천형 S4, 절약형·간편형 S8)과 일치한다")
	void 손_계산_그리디() {
		PlanInput vs = SolverCases.saverVsSimple();
		Selection baseline = SolverCases.saverVsSimpleRecommended();

		assertThat(BruteForceOracle.greedy(vs, BruteForceOracle.comparator(PlanType.RECOMMENDED, vs.products(), 0)).selection())
				.isEqualTo(SolverCases.saverVsSimpleGreedyRecommended());
		assertThat(BruteForceOracle.improve(vs, BruteForceOracle.comparator(PlanType.SIMPLE, vs.products(), 4), baseline).selection())
				.isEqualTo(SolverCases.saverVsSimpleGreedySimple());
		assertThat(BruteForceOracle.improve(vs, BruteForceOracle.comparator(PlanType.SAVER, vs.products(), 4), baseline).selection())
				.isEqualTo(SolverCases.saverVsSimpleGreedySaver());
	}

	@Test
	@DisplayName("오라클: S6 후보 제외(FREE, 0번째 달 SUBSCRIBED, 못 보는 상품, 예산 초과 상품)")
	void 후보_제외() {
		// 상품: 1 일반 3,000원(보임), 2 FREE, 3 SUBSCRIBED 2,000원, 4 일반 3,000원(아무 단위도 못 봄), 5 일반 20,000원(예산 초과)
		PlanInput input = SolverCases.in(10_000, 600,
				List.of(product(1, 3_000), free(2), subscribed(3, 2_000), product(4, 3_000), product(5, 20_000)),
				unit(1, Priority.WANT, 100, 1L, 2L, 3L, 5L));

		assertThat(BruteForceOracle.candidates(input, 0)).extracting(OttProduct::id).containsExactly(1L);
		assertThat(BruteForceOracle.candidates(input, 1)).extracting(OttProduct::id).containsExactly(1L, 3L);
		assertThat(BruteForceOracle.candidates(input, 2)).extracting(OttProduct::id).containsExactly(1L, 3L);
	}

	@Test
	@DisplayName("오라클: 후보 수 세기 손 계산 (동치 제거 4^3=64, 전체 5^3=125)")
	void 후보_수_손_계산() {
		PlanInput vs = SolverCases.saverVsSimple();

		// 예산 이하 부분집합: {}, {P}, {Q}, {R}, {Q,R}(5개). 볼 수 있는 단위: {}, {U1,U2}, {U1}, {U2}, {U1,U2} → {P}와 {Q,R}가 같은 그룹이라 4개
		assertThat(BruteForceOracle.fullCombinationCount(vs)).isEqualTo(125L);
		assertThat(BruteForceOracle.equivalenceCombinationCount(vs)).isEqualTo(64L);
	}

	@Test
	@DisplayName("오라클: 가입 횟수 정의(연속 유지 1, 갈아타기 3, 구독 중 상품 1월 선택 0, 빠졌다 다시 선택 2)")
	void 가입_횟수() {
		List<OttProduct> products = List.of(product(1, 1_000), product(2, 1_000), product(3, 1_000), subscribed(4, 1_000));

		assertThat(BruteForceOracle.joinTotal(sel(ids(1L), ids(1L), ids(1L)), products)).isEqualTo(1);
		assertThat(BruteForceOracle.joinTotal(sel(ids(1L), ids(2L), ids(3L)), products)).isEqualTo(3);
		assertThat(BruteForceOracle.joinTotal(sel(ids(), ids(4L), ids()), products)).isZero();
		assertThat(BruteForceOracle.joinTotal(sel(ids(1L), ids(), ids(1L)), products)).isEqualTo(2);
		assertThat(BruteForceOracle.joinTotal(sel(ids(), ids(1L), ids(1L)), products)).isEqualTo(1);
	}

	@Test
	@DisplayName("오라클: scoreFloor 정수 계산이 올림 70%와 같다")
	void 점수_하한_정수_계산() {
		int[][] cases = {{0, 0}, {1, 1}, {3, 3}, {5, 4}, {10, 7}, {20, 14}};
		for (int[] c : cases) {
			assertThat(BruteForceOracle.scoreFloor(c[0])).as("score=%d", c[0]).isEqualTo(c[1]);
		}
	}
}
