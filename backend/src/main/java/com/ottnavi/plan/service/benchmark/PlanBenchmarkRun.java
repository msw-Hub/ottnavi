package com.ottnavi.plan.service.benchmark;

import java.util.List;

/** 비교 측정 한 번의 전체 결과. */
public record PlanBenchmarkRun(
		int measuredRuns,                 // 알고리즘마다 측정한 횟수
		int warmupRuns,                   // 알고리즘마다 버린 워밍업 횟수
		List<SavedBenchmarkCase> cases,   // 저장된 케이스들(이름 순)
		List<String> skippedCaseNames     // 후보 수 상한 초과로 건너뛴 케이스 이름(저장하지 않는다)
) {
}
