package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.service.benchmark.BenchmarkCaseResult;
import com.ottnavi.plan.service.benchmark.PlanBenchmarkRun;
import com.ottnavi.plan.service.benchmark.SavedBenchmarkCase;
import com.ottnavi.plan.service.benchmark.SolverMeasurement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 완전탐색·그리디 비교 측정 결과(계약 PlanBenchmark). 차이 값은 모두 그리디 − 완전탐색이다.
 *
 * @param measuredRuns     알고리즘마다 측정한 횟수
 * @param warmupRuns       알고리즘마다 버린 워밍업 횟수
 * @param caseCount        저장된 케이스 수
 * @param cases            케이스별 결과(이름 순)
 * @param skippedCaseNames 후보 수 상한 초과로 건너뛴 케이스 이름(저장하지 않는다)
 */
public record PlanBenchmarkResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int measuredRuns,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int warmupRuns,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "21") int caseCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Case> cases,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> skippedCaseNames) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanBenchmarkResponse from(PlanBenchmarkRun run) {
		List<Case> cases = new ArrayList<>();
		for (SavedBenchmarkCase saved : run.cases()) {
			cases.add(Case.from(saved));
		}
		return new PlanBenchmarkResponse(run.measuredRuns(), run.warmupRuns(), cases.size(), cases,
				new ArrayList<>(run.skippedCaseNames()));
	}

	/**
	 * 케이스 하나의 결과.
	 *
	 * @param caseName       케이스 이름
	 * @param inputHash      입력 해시(calc_run 조회 키)
	 * @param candidateCount 완전탐색 기준 후보 조합 수
	 * @param exact          완전탐색 결과
	 * @param greedy         그리디 결과
	 * @param mustDiff       꼭 시청 완료 수 차이(그리디 − 완전탐색)
	 * @param scoreDiff      점수 차이(그리디 − 완전탐색)
	 * @param costDiff       총비용 차이(원, 그리디 − 완전탐색)
	 */
	public record Case(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "case-07-greedy-trap") String caseName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9f2c0000000000000000000000000000000000000000000000000000000000ab") String inputHash,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "27") int candidateCount,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Measurement exact,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Measurement greedy,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int mustDiff,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "-2") int scoreDiff,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "-20000") int costDiff) {

		static Case from(SavedBenchmarkCase saved) {
			BenchmarkCaseResult result = saved.result();
			return new Case(result.caseName(), result.inputHash(),
					(int) Math.min(result.candidateCount(), Integer.MAX_VALUE),
					Measurement.from(saved.exactCalcRunId(), result.exact()),
					Measurement.from(saved.greedyCalcRunId(), result.greedy()),
					result.mustDiff(), result.scoreDiff(), result.costDiff());
		}
	}

	/**
	 * 알고리즘 하나의 측정 결과(3회 중앙값).
	 *
	 * @param calcRunId       저장된 calc_run 행 id
	 * @param mustCompleted   시청 완료한 꼭 볼 작품 수
	 * @param score           점수
	 * @param totalCost       3개월 총비용(원)
	 * @param elapsedMicros   소요 시간 중앙값(마이크로초)
	 * @param elapsedMs       소요 시간 중앙값(ms 내림, calc_run.elapsed_ms와 같은 값). 0일 수 있다
	 * @param matchesExpected 정답 세트의 기대 결과와 같은지
	 */
	public record Measurement(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "101") long calcRunId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int mustCompleted,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4") int score,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "28000") int totalCost,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "21") long elapsedMicros,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") long elapsedMs,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean matchesExpected) {

		static Measurement from(long calcRunId, SolverMeasurement measurement) {
			return new Measurement(calcRunId, measurement.mustCompleted(), measurement.score(), measurement.totalCost(),
					measurement.elapsedMicrosMedian(), measurement.elapsedMsMedian(), measurement.matchesExpected());
		}
	}
}
