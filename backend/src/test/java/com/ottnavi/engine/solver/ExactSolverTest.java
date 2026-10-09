package com.ottnavi.engine.solver;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.solver.SolverCases.ids;
import static com.ottnavi.engine.solver.SolverCases.in;
import static com.ottnavi.engine.solver.SolverCases.sel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 완전탐색 Solver 테스트(명세 S3·S5·S6·S7·S9, 5절 1·5~10·12·13·16·18~20). 기대값은 손 계산 또는 무차별 대입 오라클이다.
 */
class ExactSolverTest {

	private final ExactSolver exact = new ExactSolver();

	private static List<PlanInput> oracleInputs() {
		List<PlanInput> inputs = new ArrayList<>(SolverCases.handCases());
		inputs.addAll(SolverCases.randomInputs(60, 20261009L));
		return inputs;
	}

	// ---------------------------------------------------------------- 손 계산 정답

	@Test
	@DisplayName("추천형 완전탐색: 그리디가 나쁜 케이스에서 점수 4·비용 28,000원, 조합은 ⑤ 사전순으로 ({1},{1},{2})")
	void 추천형_그리디_나쁜_케이스() {
		SolveResult result = exact.solve(SolverCases.greedyWorse());

		assertThat(result.evaluation().score()).isEqualTo(4);
		assertThat(result.evaluation().totalCost()).isEqualTo(28_000);
		assertThat(result.selection()).isEqualTo(SolverCases.greedyWorseExactSelection());
		assertThat(result.solverType()).isEqualTo(SolverType.EXACT);
		assertThat(result.planType()).isEqualTo(PlanType.RECOMMENDED);
	}

	@Test
	@DisplayName("동치 제거 동점(T20): 같은 가격·같은 단위를 보는 두 상품이면 결제를 미루고 ID가 작은 p1을 고른다")
	void 동치_제거_동점() {
		assertThat(exact.solve(SolverCases.tiePair()).selection()).isEqualTo(SolverCases.tiePairSelection());
	}

	@Test
	@DisplayName("동치 제거 회귀(성질 16): 최선은 [{1},{2},{2}], must 1·score 2·비용 3,000원")
	void 동치_제거_회귀() {
		SolveResult result = exact.solve(SolverCases.equivalenceRegression());

		assertThat(result.selection()).isEqualTo(SolverCases.equivalenceRegressionSelection());
		assertThat(result.evaluation().mustCompleted()).isEqualTo(1);
		assertThat(result.evaluation().score()).isEqualTo(2);
		assertThat(result.evaluation().totalCost()).isEqualTo(3_000);
	}

	@Test
	@DisplayName("SUBSCRIBED 시작: 예산보다 비싸도 0번째 달 비용이 0이라 시청할 수 있다")
	void 구독_중_시작() {
		SolveResult result = exact.solve(SolverCases.subscribedStart());

		assertThat(result.evaluation().score()).isEqualTo(2);
		assertThat(result.evaluation().totalCost()).isZero();
		assertThat(result.selection()).isEqualTo(sel(ids(), ids(), ids()));
	}

	@Test
	@DisplayName("SUBSCRIBED 유지 비용: 1번째 달에 예산을 넘으면 고를 수 없어 이어 보는 시즌을 못 끝낸다")
	void 구독_중_1번째_달_예산_초과() {
		SolveResult result = exact.solve(SolverCases.subscribedMonthOneOverBudget());

		assertThat(result.evaluation().score()).isZero();
		assertThat(result.evaluation().totalCost()).isZero();
		assertThat(result.selection()).isEqualTo(sel(ids(), ids(), ids()));
	}

	@Test
	@DisplayName("FREE 상품(S6): 작품은 볼 수 있지만 결과의 selection에 나타나지 않는다")
	void 무료_상품() {
		SolveResult result = exact.solve(SolverCases.freeProduct());

		assertThat(result.evaluation().score()).isEqualTo(4);
		assertThat(result.evaluation().totalCost()).isEqualTo(5_000);
		assertThat(result.selection()).isEqualTo(SolverCases.freeProductSelection());
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			assertThat(result.selection().productIdsOf(month)).doesNotContain(3L);
		}
	}

	@Test
	@DisplayName("FREE 상품의 ID가 더 작아도 후보가 아니라서 결과에 나타나지 않는다")
	void 무료_상품_ID가_작은_경우() {
		SolveResult result = exact.solve(SolverCases.freeProductLowId());

		assertThat(result.evaluation().score()).isEqualTo(4);
		assertThat(result.evaluation().totalCost()).isEqualTo(5_000);
		assertThat(result.selection()).isEqualTo(SolverCases.freeProductLowIdSelection());
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			assertThat(result.selection().productIdsOf(month)).doesNotContain(1L);
		}
	}

	@Test
	@DisplayName("S6 후보 제외(성질 20): FREE, 0번째 달 SUBSCRIBED, 어떤 단위도 못 보는 상품, 예산 초과 상품은 결과에 나타나지 않는다")
	void 후보_제외() {
		// 상품 1 일반 3,000원, 2 FREE, 3 SUBSCRIBED 2,000원, 4 일반 3,000원(아무도 못 봄), 5 일반 20,000원(예산 10,000원 초과)
		// U1(WANT 100)은 1·5, U2는 FREE 2, U3는 SUBSCRIBED 3으로만 볼 수 있다. U2·U3는 선택 없이 0번째 달부터 보고 U1만 1이 필요하다 → ({},{},{1}), 점수 6, 3,000원
		PlanInput input = in(10_000, 600,
				List.of(product(1, 3_000), free(2), subscribed(3, 2_000), product(4, 3_000), product(5, 20_000)),
				unit(1, Priority.WANT, 100, 1L, 5L), unit(2, Priority.WANT, 100, 2L), unit(3, Priority.WANT, 100, 3L));

		for (PlanType type : PlanType.values()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			SolveResult result = exact.solve(input, SolverCases.objective(type, input, recommended));

			assertThat(result.selection()).as(type.name()).isEqualTo(sel(ids(), ids(), ids(1L)));
			assertThat(result.evaluation().score()).isEqualTo(6);
			assertThat(result.evaluation().totalCost()).isEqualTo(3_000);
		}
	}

	@Test
	@DisplayName("S6 후보 제외(성질 20): 손 계산·무작위 입력 전부에서 세 유형 결과가 후보 상품으로만 이루어져 있다")
	void 후보_제외_전체_입력() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				SolveResult result = exact.solve(input, SolverCases.objective(type, input, recommended));

				assertThat(BruteForceOracle.usesOnlyCandidates(input, result.selection())).as("%s %s", type, input).isTrue();
			}
		}
	}

	@Test
	@DisplayName("결정 4(한 달 최소 120분): 긴 시즌을 끝내려면 세 달 모두 고른다")
	void 결정_4_세_달_모두_선택() {
		SolveResult result = exact.solve(SolverCases.decisionFour());

		assertThat(result.evaluation().mustCompleted()).isEqualTo(1);
		assertThat(result.evaluation().score()).isEqualTo(2);
		assertThat(result.evaluation().totalCost()).isEqualTo(30_000);
		assertThat(result.selection()).isEqualTo(SolverCases.decisionFourSelection());
	}

	@Test
	@DisplayName("결정 1(실제 시청 달도 연속): 꼭 볼 작품 달을 피해 연속된 두 달에 이어 보는 배치를 고른다")
	void 결정_1_연속된_달() {
		SolveResult result = exact.solve(SolverCases.decisionOne());

		assertThat(result.evaluation().mustCompleted()).isEqualTo(1);
		assertThat(result.evaluation().score()).isEqualTo(2);
		assertThat(result.evaluation().totalCost()).isEqualTo(15_000);
		assertThat(result.selection()).isEqualTo(SolverCases.decisionOneSelection());
	}

	@Test
	@DisplayName("시청 가능 상품이 없는 단위(NO_PROVIDER·UNKNOWN)는 조합에 영향을 주지 않는다")
	void 시청_가능_상품_없는_단위() {
		SolveResult result = exact.solve(SolverCases.withUnwatchableUnits());

		assertThat(result.evaluation().score()).isEqualTo(2);
		assertThat(result.evaluation().totalCost()).isEqualTo(5_000);
		assertThat(result.selection()).isEqualTo(sel(ids(), ids(), ids(1L)));
	}

	@Test
	@DisplayName("예산 0원: 비용이 드는 상품은 고를 수 없고 FREE로 볼 수 있는 작품만 시청 완료한다")
	void 예산_0원() {
		SolveResult result = exact.solve(SolverCases.zeroBudget());

		assertThat(result.evaluation().score()).isEqualTo(2);
		assertThat(result.evaluation().totalCost()).isZero();
		assertThat(result.selection()).isEqualTo(sel(ids(), ids(), ids()));
	}

	@Test
	@DisplayName("빈 입력: 단위가 없으면 세 달 모두 빈 집합이고 비용 0이다")
	void 빈_입력() {
		PlanInput empty = in(10_000, 600, List.of(product(1, 5_000)));
		PlanInput emptyWithoutProducts = in(10_000, 600, List.of());

		for (PlanInput input : List.of(empty, emptyWithoutProducts)) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				SolveResult result = exact.solve(input, SolverCases.objective(type, input, recommended));

				assertThat(result.selection()).as(type.name()).isEqualTo(sel(ids(), ids(), ids()));
				assertThat(result.evaluation().totalCost()).isZero();
				assertThat(result.evaluation().score()).isZero();
				assertThat(result.planType()).isEqualTo(type);
				assertThat(result.solverType()).isEqualTo(SolverType.EXACT);
			}
		}
	}

	@Test
	@DisplayName("절약형·간편형 완전탐색: 추천형과 달라지는 손 계산 정답")
	void 절약형_간편형_손_계산() {
		PlanInput dropB = SolverCases.saverAndSimpleDropB();
		Evaluation dropBRecommended = BruteForceOracle.bestRecommended(dropB);
		assertThat(exact.solve(dropB, PlanObjective.recommended(dropB.products())).selection())
				.isEqualTo(SolverCases.saverAndSimpleDropBRecommended());
		SolveResult saver = exact.solve(dropB, PlanObjective.saver(dropB.products(), dropBRecommended));
		SolveResult simple = exact.solve(dropB, PlanObjective.simple(dropB.products(), dropBRecommended));
		assertThat(saver.selection()).isEqualTo(SolverCases.saverAndSimpleDropBSelection());
		assertThat(saver.evaluation().totalCost()).isEqualTo(6_000);
		assertThat(saver.planType()).isEqualTo(PlanType.SAVER);
		assertThat(simple.selection()).isEqualTo(SolverCases.saverAndSimpleDropBSelection());
		assertThat(simple.planType()).isEqualTo(PlanType.SIMPLE);

		PlanInput vs = SolverCases.saverVsSimple();
		Evaluation vsRecommended = BruteForceOracle.bestRecommended(vs);
		assertThat(exact.solve(vs).selection()).isEqualTo(SolverCases.saverVsSimpleRecommended());
		assertThat(exact.solve(vs, PlanObjective.saver(vs.products(), vsRecommended)).selection())
				.isEqualTo(SolverCases.saverVsSimpleRecommended());
		SolveResult vsSimple = exact.solve(vs, PlanObjective.simple(vs.products(), vsRecommended));
		assertThat(vsSimple.selection()).isEqualTo(SolverCases.saverVsSimpleSimple());
		assertThat(vsSimple.evaluation().totalCost()).isEqualTo(9_000);
	}

	// ---------------------------------------------------------------- 무차별 대입 비교(S3)

	@Test
	@DisplayName("완전탐색은 세 유형 모두 무차별 대입 최선과 정확히 같다(손 계산 케이스 + 무작위 입력 60개)")
	void 무차별_대입과_같다() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				Evaluation expected = BruteForceOracle.best(input, type, recommended.score());

				SolveResult actual = exact.solve(input, SolverCases.objective(type, input, recommended));

				assertThat(actual.selection()).as("%s %s", type, input).isEqualTo(expected.selection());
				assertThat(actual.evaluation().mustCompleted()).isEqualTo(expected.mustCompleted());
				assertThat(actual.evaluation().score()).isEqualTo(expected.score());
				assertThat(actual.evaluation().totalCost()).isEqualTo(expected.totalCost());
				assertThat(actual.planType()).isEqualTo(type);
				assertThat(actual.solverType()).isEqualTo(SolverType.EXACT);
				assertThat(BruteForceOracle.withinBudget(input, actual.selection())).isTrue();
			}
		}
	}

	@Test
	@DisplayName("완전탐색은 PlanObjective 구현과 무관하게 주어진 비교기를 따른다(오라클 비교기를 직접 넘겨도 같은 결과)")
	void 주어진_비교기를_따른다() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				Comparator<Evaluation> comparator = BruteForceOracle.comparator(type, input.products(), recommended.score());
				Evaluation expected = BruteForceOracle.best(input, type, recommended.score());
				Selection baseline = type == PlanType.RECOMMENDED ? null : recommended.selection();

				SolveResult actual = exact.solve(input, new PlanObjective(type, comparator, baseline));

				assertThat(actual.selection()).as("%s %s", type, input).isEqualTo(expected.selection());
			}
		}
	}

	@Test
	@DisplayName("간편형은 동치 제거 없이 전부 훑는다: 비용이 더 비싼 조합이 최선인 입력")
	void 간편형은_동치_제거_없이_전부_훑는다() {
		// saverVsSimple에서 P({1}, 9,000원)와 Q+R({2,3}, 4,000원)은 같은 작품 두 개를 본다(같은 동치 그룹). 추천형은 비용이 싼 Q+R만 남기지만
		// 간편형은 가입 횟수가 적은 비싼 P를 골라야 하므로, 간편형에서 동치 제거를 쓰면 이 정답이 사라진다.
		PlanInput vs = SolverCases.saverVsSimple();
		SolveResult simple = exact.solve(vs, PlanObjective.simple(vs.products(), BruteForceOracle.bestRecommended(vs)));

		assertThat(simple.evaluation().totalCost()).isEqualTo(9_000);
		assertThat(simple.selection().productIdsOf(2)).containsExactly(1L);
	}

	// ---------------------------------------------------------------- 절약형·간편형 성질(5절 12·13)

	@Test
	@DisplayName("절약형: 점수 하한 이상이고 비용이 추천형 이하이며 꼭 시청 완료 수가 추천형과 같다")
	void 절약형_성질() {
		for (PlanInput input : oracleInputs()) {
			SolveResult recommended = exact.solve(input);
			int floor = BruteForceOracle.scoreFloor(recommended.evaluation().score());

			SolveResult saver = exact.solve(input, PlanObjective.saver(input.products(), recommended.evaluation()));

			assertThat(saver.evaluation().score()).as("%s", input).isGreaterThanOrEqualTo(floor);
			assertThat(saver.evaluation().totalCost()).as("%s", input).isLessThanOrEqualTo(recommended.evaluation().totalCost());
			assertThat(saver.evaluation().mustCompleted()).isEqualTo(recommended.evaluation().mustCompleted());
		}
	}

	@Test
	@DisplayName("간편형: 점수 하한 이상이고 가입 횟수가 추천형 이하이며 꼭 시청 완료 수가 추천형과 같다")
	void 간편형_성질() {
		for (PlanInput input : oracleInputs()) {
			SolveResult recommended = exact.solve(input);
			int floor = BruteForceOracle.scoreFloor(recommended.evaluation().score());

			SolveResult simple = exact.solve(input, PlanObjective.simple(input.products(), recommended.evaluation()));

			assertThat(simple.evaluation().score()).as("%s", input).isGreaterThanOrEqualTo(floor);
			assertThat(BruteForceOracle.joinTotal(simple.selection(), input.products())).as("%s", input)
					.isLessThanOrEqualTo(BruteForceOracle.joinTotal(recommended.selection(), input.products()));
			assertThat(simple.evaluation().mustCompleted()).isEqualTo(recommended.evaluation().mustCompleted());
		}
	}

	// ---------------------------------------------------------------- 결정성·예외

	@Test
	@DisplayName("결정성: 같은 입력을 두 번 풀어도, 단위 순서를 섞어도 같은 결과다")
	void 결정성() {
		long seed = 1;
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				PlanObjective objective = SolverCases.objective(type, input, recommended);
				Selection first = exact.solve(input, objective).selection();

				assertThat(exact.solve(input, objective).selection()).isEqualTo(first);
				PlanInput shuffled = SolverCases.shuffledUnits(input, seed++);
				assertThat(exact.solve(shuffled, SolverCases.objective(type, shuffled, recommended)).selection())
						.as("%s %s", type, input).isEqualTo(first);
			}
		}
	}

	@Test
	@DisplayName("입력을 바꾸지 않는다: 풀이 전후로 입력이 같다")
	void 입력을_바꾸지_않는다() {
		PlanInput input = SolverCases.greedyWorse();
		PlanInput copy = new PlanInput(List.copyOf(input.units()), List.copyOf(input.products()), input.monthlyBudget(), input.monthlyWatchMinutes());

		exact.solve(input);

		assertThat(input).isEqualTo(copy);
	}

	@Test
	@DisplayName("null 입력이나 null 목적함수는 IllegalArgumentException이다")
	void null_입력() {
		PlanInput input = SolverCases.singleProduct();

		assertThatThrownBy(() -> exact.solve(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> exact.solve(null, PlanObjective.recommended(input.products()))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> exact.solve(input, null)).isInstanceOf(IllegalArgumentException.class);
	}

	// ---------------------------------------------------------------- S9 상품 수 한도(성질 19)

	@Test
	@DisplayName("후보 상품이 20개를 넘으면 solve와 countCandidateCombinations가 IllegalArgumentException이다")
	void 후보_상품_21개는_예외() {
		PlanInput input = SolverCases.manyProducts(21, 0);
		PlanObjective recommended = PlanObjective.recommended(input.products());

		assertThat(ExactSolver.MAX_PRODUCT_COUNT).isEqualTo(20);
		assertThatThrownBy(() -> exact.solve(input, recommended)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> exact.countCandidateCombinations(input, recommended)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> exact.solve(input)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("한도는 S6 적용 후 후보 상품 기준이다: 상품 21개라도 후보가 20개면 풀린다")
	void 후보_상품_20개는_풀린다() {
		// 상품 21개 중 1개는 아무 작품도 못 봐서 후보가 아니다. 예산 1,000원이라 한 달에 하나씩, 달마다 부분집합 21개(빈 집합 + 단일 20개)
		PlanInput input = SolverCases.manyProducts(20, 1);
		PlanObjective recommended = PlanObjective.recommended(input.products());

		assertThat(exact.countCandidateCombinations(input, recommended)).isEqualTo(21L * 21 * 21);
		SolveResult result = exact.solve(input, recommended);
		// 세 달에 상품 3개를 사서 WANT 3개(점수 6)를 본다. 비용 3,000원
		assertThat(result.evaluation().score()).isEqualTo(6);
		assertThat(result.evaluation().totalCost()).isEqualTo(3_000);
		assertThat(BruteForceOracle.usesOnlyCandidates(input, result.selection())).isTrue();
	}

	// ---------------------------------------------------------------- S5·S7 후보 조합 수

	@Test
	@DisplayName("후보 조합 수: 절약형·간편형은 동치 제거 없이 달별 부분집합 수의 곱이다")
	void 후보_조합_수_절약형_간편형() {
		// 상품 2개가 모두 3,000원이고 예산 10,000원이라 달마다 부분집합 4개 → 4^3 = 64
		PlanInput input = in(10_000, 600, List.of(product(1, 3_000), product(2, 3_000)), unit(1, Priority.WANT, 200, 1L, 2L));
		Evaluation recommended = BruteForceOracle.bestRecommended(input);

		assertThat(exact.countCandidateCombinations(input, PlanObjective.saver(input.products(), recommended))).isEqualTo(64L);
		assertThat(exact.countCandidateCombinations(input, PlanObjective.simple(input.products(), recommended))).isEqualTo(64L);
	}

	@Test
	@DisplayName("후보 조합 수: 예산을 넘는 부분집합은 세지 않는다")
	void 후보_조합_수_예산_제한() {
		// 달마다 {}, {1}, {2}만 가능(합 6,000 > 5,000) → 3^3 = 27
		PlanInput input = in(5_000, 600, List.of(product(1, 3_000), product(2, 3_000)), unit(1, Priority.WANT, 200, 1L, 2L));

		assertThat(exact.countCandidateCombinations(input, PlanObjective.saver(input.products(), BruteForceOracle.bestRecommended(input))))
				.isEqualTo(27L);
	}

	@Test
	@DisplayName("후보 조합 수: 추천형은 볼 수 있는 단위 집합이 같은 부분집합을 한 그룹으로 센다")
	void 후보_조합_수_추천형() {
		// 상품 둘이 같은 단위를 보므로 {1}, {2}, {1,2}가 한 그룹 → 달마다 {}, 그룹 = 2개, 2^3 = 8. 절약형은 4^3 = 64
		PlanInput input = in(10_000, 600, List.of(product(1, 3_000), product(2, 3_000)), unit(1, Priority.WANT, 200, 1L, 2L));

		assertThat(exact.countCandidateCombinations(input, PlanObjective.recommended(input.products()))).isEqualTo(8L);

		// P({1})와 Q+R({2,3})가 같은 그룹이 되는 입력: 달마다 그룹 4개 → 64, 전체 부분집합 5개 → 125
		PlanInput vs = SolverCases.saverVsSimple();
		assertThat(exact.countCandidateCombinations(vs, PlanObjective.recommended(vs.products()))).isEqualTo(64L);
		assertThat(exact.countCandidateCombinations(vs, PlanObjective.saver(vs.products(), BruteForceOracle.bestRecommended(vs))))
				.isEqualTo(125L);
	}

	@Test
	@DisplayName("후보 조합 수: 아무 작품도 못 보는 상품은 S6으로 빠져 추천형·절약형 모두 8이다(이전 가정 '8'도 새 정의에서 맞다)")
	void 후보_조합_수_못_보는_상품() {
		PlanInput input = in(10_000, 600, List.of(product(1, 3_000), product(2, 3_000)), unit(1, Priority.WANT, 200, 1L));
		Evaluation recommended = BruteForceOracle.bestRecommended(input);

		assertThat(exact.countCandidateCombinations(input, PlanObjective.recommended(input.products()))).isEqualTo(8L);
		assertThat(exact.countCandidateCombinations(input, PlanObjective.saver(input.products(), recommended))).isEqualTo(8L);
	}

	@Test
	@DisplayName("후보 조합 수: 손 계산 케이스와 무작위 입력에서 오라클이 센 값과 같다")
	void 후보_조합_수_오라클과_같다() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);

			assertThat(exact.countCandidateCombinations(input, PlanObjective.recommended(input.products()))).as("추천형 %s", input)
					.isEqualTo(BruteForceOracle.equivalenceCombinationCount(input));
			assertThat(exact.countCandidateCombinations(input, PlanObjective.saver(input.products(), recommended))).as("절약형 %s", input)
					.isEqualTo(BruteForceOracle.fullCombinationCount(input));
			assertThat(exact.countCandidateCombinations(input, PlanObjective.simple(input.products(), recommended))).as("간편형 %s", input)
					.isEqualTo(BruteForceOracle.fullCombinationCount(input));
		}
	}

	@Test
	@DisplayName("후보 조합 수: 빈 입력도 빈 집합 하나씩이라 1이다")
	void 후보_조합_수_빈_입력() {
		PlanInput input = in(10_000, 600, List.of());

		assertThat(exact.countCandidateCombinations(input, PlanObjective.recommended(input.products()))).isEqualTo(1L);
	}
}
