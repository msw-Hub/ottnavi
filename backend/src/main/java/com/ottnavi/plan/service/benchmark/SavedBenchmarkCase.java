package com.ottnavi.plan.service.benchmark;

/** 저장까지 끝난 케이스 하나의 결과. 측정값과 calc_run에 저장된 두 행의 id를 묶는다. */
public record SavedBenchmarkCase(
		BenchmarkCaseResult result, // 측정 결과(완전탐색은 null이 아니다)
		long exactCalcRunId,        // 완전탐색 행의 calc_run id
		long greedyCalcRunId        // 그리디 행의 calc_run id
) {
}
