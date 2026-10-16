package com.ottnavi.plan.service.benchmark;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 비교 측정을 실행하고 결과를 저장하는 서비스(Task 065, FR-13).
 * 측정은 트랜잭션 밖에서, 저장은 PlanBenchmarkWriter의 짧은 트랜잭션에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanBenchmarkRunService {

	private static final int WARMUP_RUNS = 1; // MedianTimer가 버리는 워밍업 횟수

	private final PlanBenchmarkService planBenchmarkService;
	private final PlanBenchmarkWriter planBenchmarkWriter;

	/** 정답 세트를 모두 측정해 calc_run에 저장하고 결과를 돌려준다. */
	public PlanBenchmarkRun run() {
		// 1. 측정한다(트랜잭션 밖)
		List<BenchmarkCaseResult> results = planBenchmarkService.measure();
		// 2. 건너뛴 케이스 이름을 모은다
		List<String> skipped = new ArrayList<>();
		for (BenchmarkCaseResult result : results) {
			if (result.exact() == null) {
				skipped.add(result.caseName());
			}
		}
		// 3. 저장한다(짧은 쓰기 트랜잭션)
		List<SavedBenchmarkCase> saved = planBenchmarkWriter.save(results);
		log.info("비교 측정을 저장했다: 케이스={}, 저장 행={}, 건너뜀={}", results.size(), saved.size() * 2, skipped.size());
		return new PlanBenchmarkRun(MedianTimer.MEASURED_RUNS, WARMUP_RUNS, saved, skipped);
	}
}
