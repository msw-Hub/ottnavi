package com.ottnavi.plan.service.benchmark;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.plan.domain.entity.CalcRun;
import com.ottnavi.plan.domain.repository.CalcRunRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 측정 결과를 calc_run에 한 트랜잭션으로 저장한다(Task 065).
 * 측정(수 초의 CPU 작업)은 이 클래스 밖에서 끝난 상태로 들어오고, PlanBenchmarkRunService와 다른 빈이어야 @Transactional이 적용된다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PlanBenchmarkWriter {

	private final CalcRunRepository calcRunRepository;

	/**
	 * 케이스마다 EXACT·GREEDY 두 행을 저장하고 id를 돌려준다. plan_id는 NULL, plan_type은 RECOMMENDED다.
	 * 완전탐색이 건너뛰어진 케이스(exact가 null)는 두 행 모두 저장하지 않는다. 같은 입력으로 다시 실행하면 행이 더 쌓인다.
	 */
	public List<SavedBenchmarkCase> save(List<BenchmarkCaseResult> results) {
		List<SavedBenchmarkCase> saved = new ArrayList<>();
		for (BenchmarkCaseResult result : results) {
			if (result.exact() == null) {
				continue;
			}
			// 1. 후보 수는 int 컬럼이라 상한을 넘지 않게 자른다. 그리디 행에도 완전탐색 기준 값을 쓴다
			int candidateCount = (int) Math.min(result.candidateCount(), Integer.MAX_VALUE);
			// 2. 두 행을 저장한다
			CalcRun exactRun = calcRunRepository
					.save(toRun(result.inputHash(), SolverType.EXACT, result.exact(), candidateCount));
			CalcRun greedyRun = calcRunRepository
					.save(toRun(result.inputHash(), SolverType.GREEDY, result.greedy(), candidateCount));
			saved.add(new SavedBenchmarkCase(result, exactRun.getId(), greedyRun.getId()));
		}
		return saved;
	}

	private CalcRun toRun(String inputHash, SolverType algorithm, SolverMeasurement measurement, int candidateCount) {
		return CalcRun.create(null, inputHash, PlanType.RECOMMENDED, algorithm, measurement.mustCompleted(),
				measurement.score(), measurement.totalCost(), candidateCount, measurement.elapsedMsMedian());
	}
}
