package com.ottnavi.engine.benchmark;

import static com.ottnavi.engine.benchmark.BenchmarkSupport.heapCell;
import static com.ottnavi.engine.benchmark.BenchmarkSupport.measure;
import static com.ottnavi.engine.benchmark.BenchmarkSupport.ms;
import static com.ottnavi.engine.benchmark.BenchmarkSupport.summary;
import static com.ottnavi.engine.benchmark.BenchmarkSupport.synthetic;
import static com.ottnavi.engine.benchmark.BenchmarkSupport.timeCell;

import com.ottnavi.engine.benchmark.BenchmarkSupport.Stats;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.Evaluator;
import com.ottnavi.engine.rule.PlanObjective;
import com.ottnavi.engine.solver.ExactSolver;
import com.ottnavi.engine.solver.GreedySolver;
import com.ottnavi.engine.solver.PlanTypeSolver;
import com.ottnavi.engine.solver.SolverSelector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * 엔진 성능 측정(Task 040). {@code ./gradlew benchmark}로만 돌고 일반 test·build에는 포함되지 않는다.
 * 현재 코드를 그대로 재는 용도라 단언은 하지 않고, 결과 표만 콘솔과 build/benchmark-results/에 남긴다.
 * 측정 대상: ① 정답 세트 완전탐색·그리디 비교 ② 최악 입력 ③ 큰 찜 ④ 후보 상품 수별 경로·메모리 ⑤ 조합당 비용 분해.
 */
@Tag("benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EngineBenchmarkMeasureTest {

	private static final BenchmarkReport REPORT = new BenchmarkReport();

	private static final int WATCH_MINUTES = 1_200;                  // 합성 입력의 월 시청 가능 시간(20시간)
	private static final long FORCE_EXACT_LIMIT = 30_000_000L;       // 강제 완전탐색을 시도할 후보 조합 수 상한(넘으면 시간이 폭발해 생략하고 추정만 한다)
	private static final long SELECTOR_LIMIT = SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS;

	private final ExactSolver exact = new ExactSolver();
	private final GreedySolver greedy = new GreedySolver();
	private final PlanTypeSolver exactTypes = new PlanTypeSolver(exact);   // 유형 3종 모두 완전탐색(상한 무시)
	private final PlanTypeSolver greedyTypes = new PlanTypeSolver(greedy); // 유형 3종 모두 그리디
	private final PlanTypeSolver selectorTypes = new PlanTypeSolver(new SolverSelector(exact, greedy, SELECTOR_LIMIT)); // 운영 구성

	@AfterAll
	static void writeReport() {
		REPORT.write();
	}

	@Test
	@Order(1)
	@DisplayName("정답 세트 13건: 완전탐색 대 그리디의 점수·비용 차이와 시간")
	void 정답_세트_비교() {
		List<List<String>> recommendedRows = new ArrayList<>();
		List<List<String>> otherRows = new ArrayList<>();
		for (BenchmarkCase benchmarkCase : BenchmarkLoader.loadAll()) {
			PlanInput input = BenchmarkLoader.toInput(benchmarkCase);
			Stats<SolveResult> exactRec = measure(() -> exact.solve(input));
			Stats<SolveResult> greedyRec = measure(() -> greedy.solve(input));
			Stats<Map<PlanType, SolveResult>> exactAll = measure(() -> exactTypes.solveAll(input));
			Stats<Map<PlanType, SolveResult>> greedyAll = measure(() -> greedyTypes.solveAll(input));

			Evaluation exactEval = exactRec.result().evaluation();
			Evaluation greedyEval = greedyRec.result().evaluation();
			recommendedRows.add(List.of(benchmarkCase.name(), summary(exactEval), summary(greedyEval),
					String.valueOf(exactEval.score() - greedyEval.score()),
					String.format("%,d", greedyEval.totalCost() - exactEval.totalCost()),
					String.valueOf(exactEval.mustCompleted() - greedyEval.mustCompleted()),
					ms(exactRec.millis()), ms(greedyRec.millis()), ms(exactAll.millis()), ms(greedyAll.millis())));
			otherRows.add(List.of(benchmarkCase.name(),
					summary(exactAll.result().get(PlanType.SAVER).evaluation()), summary(greedyAll.result().get(PlanType.SAVER).evaluation()),
					summary(exactAll.result().get(PlanType.SIMPLE).evaluation()), summary(greedyAll.result().get(PlanType.SIMPLE).evaluation())));
		}
		REPORT.table("1-1. 정답 세트 추천형: 완전탐색 대 그리디",
				"칸 형식은 \"꼭 시청 완료 수/점수/3개월 비용(원)\". 점수 차 = 완전탐색 - 그리디(클수록 그리디가 나쁨), 비용 차 = 그리디 - 완전탐색. 시간은 ms 중앙값이며 \"3종\"은 유형 3종 전체를 푼 시간.",
				List.of("케이스", "완전탐색 추천형", "그리디 추천형", "점수 차", "비용 차(원)", "꼭 차", "완전탐색 추천형 ms", "그리디 추천형 ms", "완전탐색 3종 ms", "그리디 3종 ms"),
				recommendedRows);
		REPORT.table("1-2. 정답 세트 절약형·간편형: 완전탐색 대 그리디",
				"칸 형식은 1-1과 같다. 그리디의 절약형·간편형은 그리디 추천형 결과를 시작 조합·점수 하한 기준으로 쓰고, 완전탐색 쪽은 완전탐색 추천형 결과를 쓴다.",
				List.of("케이스", "절약형 완전탐색", "절약형 그리디", "간편형 완전탐색", "간편형 그리디"),
				otherRows);
	}

	@Test
	@Order(2)
	@DisplayName("최악 입력(상품 7개 모두 후보, 단위 30개, 예산 넉넉)의 유형별·3종 시간")
	void 최악_입력() {
		PlanInput input = synthetic(7, 30, WATCH_MINUTES);
		List<List<String>> rows = new ArrayList<>();
		TypeTimings timings = measureTypes(input, rows, "단위 30개");
		REPORT.table("2. 최악 입력: 상품 7개 모두 후보 · 단위 30개 · 예산 넉넉",
				"조합 수는 완전탐색이 실제로 훑는 수. 조합당 ns = 중앙값 시간 / 조합 수. 3종 합계는 SolverSelector(운영 구성)로 풀었다. 힙 칸은 \"최대 used / 기준선\" MB.",
				List.of("항목", "조합 수", "중앙값 ms", "조합당 ns", "힙 MB", "결과(꼭/점수/비용)"), rows);
		REPORT.paragraph("2-참고. 유형별 시간 비율", List.of(
				"절약형 / 추천형 시간 = " + String.format("%.2f", timings.saverNanos / (double) timings.recommendedNanos) + "배",
				"간편형 / 추천형 시간 = " + String.format("%.2f", timings.simpleNanos / (double) timings.recommendedNanos) + "배",
				"간편형 / 절약형 시간 = " + String.format("%.2f", timings.simpleNanos / (double) timings.saverNanos) + "배 (조합 수가 같으므로 비교기 비용 차이로 본다)"));
	}

	@Test
	@Order(3)
	@DisplayName("큰 찜 변형(단위 60개, 100개)")
	void 큰_찜() {
		List<List<String>> rows = new ArrayList<>();
		for (int unitCount : new int[] {60, 100}) {
			PlanInput input = synthetic(7, unitCount, WATCH_MINUTES);
			measureTypes(input, rows, "단위 " + unitCount + "개");
		}
		REPORT.table("3. 큰 찜 변형: 상품 7개 · 단위 60개/100개",
				"워밍업이 15초를 넘으면 그 1회 값만 쓴다(\"(1회)\"). 표 형식은 2와 같다.",
				List.of("항목", "조합 수", "중앙값 ms", "조합당 ns", "힙 MB", "결과(꼭/점수/비용)"), rows);
	}

	@Test
	@Order(4)
	@DisplayName("후보 상품 7·10·14·17·20개: 선택 경로·후보 조합 수·시간·힙")
	void 후보_상한_근처() {
		List<List<String>> pathRows = new ArrayList<>();
		List<List<String>> forcedRows = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		double nsPerCombination = 0; // P=7 추천형 완전탐색에서 얻은 조합당 시간으로 큰 입력의 완전탐색 시간을 추정한다
		for (int productCount : new int[] {7, 10, 14, 17, 20}) {
			PlanInput input = synthetic(productCount, 30, WATCH_MINUTES);
			PlanObjective recommended = PlanObjective.recommended(input);

			Stats<Long> recommendedCount = tryMeasure(() -> exact.countCandidateCombinations(input, recommended), notes, "P=" + productCount + " 추천형 조합 수 계산");
			Stats<Map<PlanType, SolveResult>> selectorAll = tryMeasure(() -> selectorTypes.solveAll(input), notes, "P=" + productCount + " Selector 3종");

			String saverCountCell = "-";
			Stats<Long> saverCount = null;
			PlanObjective saver = null;
			Evaluation recommendedEval = null;
			if (selectorAll != null) {
				recommendedEval = selectorAll.result().get(PlanType.RECOMMENDED).evaluation();
				PlanObjective saverObjective = PlanObjective.saver(input, recommendedEval);
				saver = saverObjective;
				saverCount = tryMeasure(() -> exact.countCandidateCombinations(input, saverObjective), notes, "P=" + productCount + " 절약형 조합 수 계산");
				saverCountCell = saverCount == null ? "OOM" : String.format("%,d", saverCount.result());
			}
			String recommendedCountCell = recommendedCount == null ? "OOM" : String.format("%,d", recommendedCount.result());
			pathRows.add(List.of(String.valueOf(productCount), recommendedCountCell, saverCountCell,
					selectorAll == null ? "OOM" : pathOf(selectorAll.result()),
					recommendedCount == null ? "OOM" : timeCell(recommendedCount), recommendedCount == null ? "OOM" : heapCell(recommendedCount),
					selectorAll == null ? "OOM" : timeCell(selectorAll), selectorAll == null ? "OOM" : heapCell(selectorAll),
					selectorAll == null ? "OOM" : summary(recommendedEval)));

			// 강제 완전탐색: 조합 수가 상한 이하일 때만 시도한다. 넘으면 시간이 폭발하므로 시도하지 않고 추정만 남긴다
			String forcedRecommended = forcedCell(recommendedCount == null ? null : recommendedCount.result(), nsPerCombination,
					() -> exact.solve(input, recommended), notes, "P=" + productCount + " 강제 완전탐색 추천형");
			String forcedSaver = "-";
			if (saverCount != null && saver != null) {
				PlanObjective forcedSaverObjective = saver;
				forcedSaver = forcedCell(saverCount.result(), nsPerCombination, () -> exact.solve(input, forcedSaverObjective), notes,
						"P=" + productCount + " 강제 완전탐색 절약형");
			}
			forcedRows.add(List.of(String.valueOf(productCount), forcedRecommended, forcedSaver));

			if (productCount == 7 && recommendedCount != null) {
				// 조합당 시간은 P=7 추천형 완전탐색 중앙값으로 잡는다(바로 위 강제 완전탐색과 같은 입력, 한 번 더 잰다)
				Stats<SolveResult> calibration = tryMeasure(() -> exact.solve(input, recommended), notes, "P=7 조합당 시간 보정");
				if (calibration != null) {
					nsPerCombination = calibration.medianNanos() / (double) recommendedCount.result();
				}
			}
			System.gc();
		}
		REPORT.table("4-1. 후보 상품 수별 SolverSelector 경로(상한 " + String.format("%,d", SELECTOR_LIMIT) + ")",
				"합성 입력: 단위 30개, 예산 넉넉, 후보 상품 P개. \"조합 수 계산\"은 Selector가 경로를 정하려고 하는 countCandidateCombinations(추천형)의 시간·힙이고, "
						+ "\"Selector 3종\"은 PlanTypeSolver(SolverSelector).solveAll 전체다. 경로 칸은 추천형/절약형/간편형 순서. 힙 칸은 \"최대 used / 기준선\" MB.",
				List.of("후보 상품 수", "추천형 조합 수", "절약형 조합 수", "경로(추천/절약/간편)", "조합 수 계산 ms", "계산 힙 MB", "Selector 3종 ms", "3종 힙 MB", "추천형 결과(꼭/점수/비용)"),
				pathRows);
		REPORT.table("4-2. 강제 완전탐색(상한 무시)",
				"조합 수가 " + String.format("%,d", FORCE_EXACT_LIMIT) + " 이하일 때만 ExactSolver를 직접 호출했다. 넘으면 시도하지 않고 \"조합 수 x 조합당 시간(P=7 보정 "
						+ String.format("%.0f", nsPerCombination) + "ns)\"으로 추정 시간만 적었다. 시도한 것은 try-catch(OutOfMemoryError)로 감쌌다.",
				List.of("후보 상품 수", "추천형 강제 완전탐색", "절약형 강제 완전탐색"), forcedRows);
		if (!notes.isEmpty()) {
			REPORT.paragraph("4-3. 측정 중 기록한 사건", notes);
		}
	}

	@Test
	@Order(5)
	@DisplayName("조합 하나당 비용 분해: Selection 생성·평가·비교기")
	void 조합당_비용_분해() {
		PlanInput input = synthetic(7, 30, WATCH_MINUTES);
		Random random = new Random(BenchmarkSupport.SEED);
		int sampleCount = 20_000;
		List<List<Set<Long>>> raw = new ArrayList<>(); // Selection으로 만들기 전의 달별 집합
		for (int i = 0; i < sampleCount; i++) {
			List<Set<Long>> months = new ArrayList<>();
			for (int month = 0; month < Selection.MONTH_COUNT; month++) {
				Set<Long> ids = new HashSet<>();
				for (long id = 1; id <= 7; id++) {
					if (random.nextBoolean()) {
						ids.add(id);
					}
				}
				months.add(ids);
			}
			raw.add(months);
		}

		Evaluator evaluator = new Evaluator(input);
		List<Selection> selections = new ArrayList<>();
		long[] sink = new long[1]; // 최적화로 측정 코드가 지워지지 않게 결과를 흘려 둔다

		// Selection 생성(생성 때 달마다 TreeSet으로 정렬 복사): 3회 돌려 마지막 값을 쓴다(앞 2회는 워밍업)
		double selectionNs = 0;
		for (int pass = 0; pass < 3; pass++) {
			selections.clear();
			long start = System.nanoTime();
			for (List<Set<Long>> months : raw) {
				selections.add(new Selection(months));
			}
			selectionNs = (System.nanoTime() - start) / (double) sampleCount;
		}
		double evaluateNs = 0;
		List<Evaluation> evaluations = new ArrayList<>();
		for (int pass = 0; pass < 3; pass++) {
			evaluations.clear();
			long start = System.nanoTime();
			for (Selection selection : selections) {
				evaluations.add(evaluator.evaluate(selection));
			}
			evaluateNs = (System.nanoTime() - start) / (double) sampleCount;
		}

		Evaluation baseline = exact.solve(input).evaluation();
		Comparator<Evaluation> recommendedComparator = PlanObjective.recommended(input).comparator();
		Comparator<Evaluation> saverComparator = PlanObjective.saver(input, baseline).comparator();
		Comparator<Evaluation> simpleComparator = PlanObjective.simple(input, baseline).comparator();
		double recommendedCompareNs = compareNs(recommendedComparator, evaluations, sink);
		double saverCompareNs = compareNs(saverComparator, evaluations, sink);
		double simpleCompareNs = compareNs(simpleComparator, evaluations, sink);

		double total = selectionNs + evaluateNs + recommendedCompareNs;
		REPORT.table("5. 조합 하나당 비용 분해(최악 입력, 무작위 조합 " + String.format("%,d", sampleCount) + "개)",
				"완전탐색 한 조합 = Selection 생성 + 평가 + 비교기 1회. 단위: 1회당 ns. 이월 최적화 ②(Selection 정렬 복사)·①(간편형 signUpCount 재계산)의 몫을 가늠하는 표다. (참고 출력: " + sink[0] + ")",
				List.of("구성 요소", "1회당 ns", "한 조합 합계 대비(추천형 기준)"),
				List.of(
						List.of("Selection 생성(정렬 복사)", String.format("%,.0f", selectionNs), percent(selectionNs, total)),
						List.of("Evaluator.evaluate", String.format("%,.0f", evaluateNs), percent(evaluateNs, total)),
						List.of("추천형 비교기 compare", String.format("%,.0f", recommendedCompareNs), percent(recommendedCompareNs, total)),
						List.of("절약형 비교기 compare", String.format("%,.0f", saverCompareNs), "-"),
						List.of("간편형 비교기 compare", String.format("%,.0f", simpleCompareNs), "-")));
	}

	/** 유형별(추천·절약·간편) 완전탐색, 3종 합계(Selector), 그리디 3종을 재서 rows에 추가한다. */
	private TypeTimings measureTypes(PlanInput input, List<List<String>> rows, String label) {
		PlanObjective recommended = PlanObjective.recommended(input);
		long recommendedCount = exact.countCandidateCombinations(input, recommended);
		Stats<SolveResult> recommendedStats = measure(() -> exact.solve(input, recommended));
		Evaluation recommendedEval = recommendedStats.result().evaluation();
		PlanObjective saver = PlanObjective.saver(input, recommendedEval);
		PlanObjective simple = PlanObjective.simple(input, recommendedEval);
		long saverCount = exact.countCandidateCombinations(input, saver);
		long simpleCount = exact.countCandidateCombinations(input, simple);
		Stats<SolveResult> saverStats = measure(() -> exact.solve(input, saver));
		Stats<SolveResult> simpleStats = measure(() -> exact.solve(input, simple));
		Stats<Map<PlanType, SolveResult>> selectorAll = measure(() -> selectorTypes.solveAll(input));
		Stats<SolveResult> greedyRecommended = measure(() -> greedy.solve(input, recommended));
		Stats<Map<PlanType, SolveResult>> greedyAll = measure(() -> greedyTypes.solveAll(input));

		rows.add(combinationRow(label + " 추천형 완전탐색", recommendedCount, recommendedStats, summary(recommendedEval)));
		rows.add(combinationRow(label + " 절약형 완전탐색", saverCount, saverStats, summary(saverStats.result().evaluation())));
		rows.add(combinationRow(label + " 간편형 완전탐색", simpleCount, simpleStats, summary(simpleStats.result().evaluation())));
		rows.add(combinationRow(label + " 3종 합계(Selector, 경로 " + pathOf(selectorAll.result()) + ")", recommendedCount + saverCount + simpleCount, selectorAll, "-"));
		rows.add(List.of(label + " 추천형 그리디", "-", timeCell(greedyRecommended), "-", heapCell(greedyRecommended), summary(greedyRecommended.result().evaluation())));
		rows.add(List.of(label + " 3종 그리디", "-", timeCell(greedyAll), "-", heapCell(greedyAll), "-"));
		return new TypeTimings(recommendedStats.medianNanos(), saverStats.medianNanos(), simpleStats.medianNanos());
	}

	private static List<String> combinationRow(String name, long combinations, Stats<?> stats, String result) {
		return List.of(name, String.format("%,d", combinations), timeCell(stats),
				String.format("%,.0f", stats.medianNanos() / (double) combinations), heapCell(stats), result);
	}

	/** 측정 한 번을 OOM에서 보호한다. OOM이면 사건을 기록하고 null을 돌려준다. */
	private static <T> Stats<T> tryMeasure(Supplier<T> task, List<String> notes, String label) {
		try {
			return measure(task);
		} catch (OutOfMemoryError e) {
			notes.add(label + ": OutOfMemoryError 발생(" + e.getMessage() + "), 최대 힙 " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
			System.gc();
			return null;
		}
	}

	/** 강제 완전탐색 칸. 조합 수가 상한 이하면 직접 풀어 시간·힙을, 넘으면 추정 시간과 생략 사유를 돌려준다. */
	private static String forcedCell(Long combinations, double nsPerCombination, Supplier<SolveResult> task, List<String> notes, String label) {
		if (combinations == null) {
			return "생략(조합 수 계산 실패)";
		}
		if (combinations > FORCE_EXACT_LIMIT) {
			double seconds = combinations * nsPerCombination / 1e9;
			return String.format("생략(조합 수 %,d > %,d, 추정 약 %s)", combinations, FORCE_EXACT_LIMIT, humanDuration(seconds));
		}
		Stats<SolveResult> stats = tryMeasure(task, notes, label);
		if (stats == null) {
			return "OOM";
		}
		return String.format("%s ms, 힙 %s MB, 조합 수 %,d", timeCell(stats), heapCell(stats), combinations);
	}

	/** 선택 경로를 "EXACT/GREEDY/GREEDY" 꼴로 돌려준다(추천·절약·간편 순). */
	private static String pathOf(Map<PlanType, SolveResult> results) {
		return results.get(PlanType.RECOMMENDED).solverType() + "/" + results.get(PlanType.SAVER).solverType() + "/"
				+ results.get(PlanType.SIMPLE).solverType();
	}

	/** 비교기를 이웃한 평가 결과 쌍에 반복 호출한 1회당 ns. */
	private static double compareNs(Comparator<Evaluation> comparator, List<Evaluation> evaluations, long[] sink) {
		double nsPerCompare = 0;
		int size = evaluations.size();
		for (int pass = 0; pass < 3; pass++) {
			long start = System.nanoTime();
			long accumulator = 0;
			for (int i = 0; i < size; i++) {
				accumulator += comparator.compare(evaluations.get(i), evaluations.get((i + 1) % size));
			}
			nsPerCompare = (System.nanoTime() - start) / (double) size;
			sink[0] += accumulator;
		}
		return nsPerCompare;
	}

	private static String percent(double part, double whole) {
		return String.format("%.0f%%", part * 100 / whole);
	}

	private static String humanDuration(double seconds) {
		if (seconds < 120) {
			return String.format("%.0f초", seconds);
		}
		if (seconds < 7_200) {
			return String.format("%.0f분", seconds / 60);
		}
		if (seconds < 172_800) {
			return String.format("%.1f시간", seconds / 3_600);
		}
		return String.format("%.0f일", seconds / 86_400);
	}

	/** 유형별 완전탐색 중앙값 시간(나노초). */
	private record TypeTimings(long recommendedNanos, long saverNanos, long simpleNanos) {
	}
}
