package com.ottnavi.plan.service;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.solver.ExactSolver;
import com.ottnavi.engine.solver.PlanTypeSolver;
import com.ottnavi.engine.solver.SolverSelector;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanCalcResults;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 계산 엔진 실행(Task 064): 입력 해시를 키로 세 플랜 유형의 결과를 계산하고 planCalc 캐시에 둔다.
 * 클래스에 @Transactional을 붙이지 않는다. 엔진은 CPU만 쓰고 수 초가 걸릴 수 있어, DB 커넥션을 잡은 채 돌리면 커넥션 풀이 마른다(TECH T-4).
 * 캐시가 맞으면 원래 계산의 알고리즘·후보 수·소요 시간이 그대로 돌아온다(DRAFT 저장은 맞아도 호출하는 쪽이 한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanSolveService {

	public static final String ENGINE_RESULT_VERSION = "e1"; // 엔진 규칙 버전. 규칙을 바꿔 결과가 달라지면 올려 옛 캐시를 쓰지 않게 한다

	private final SolverSelector solverSelector; // 유형마다 완전탐색·그리디를 고르는 선택기
	private final ExactSolver exactSolver;       // 후보 조합 수를 세는 데 쓴다

	/**
	 * 세 유형을 계산한다. 캐시 키는 "e1:{inputHash}"다. 이 메서드 본문이 실행됐다면 캐시에 없어서 계산한 것이다.
	 */
	@Cacheable(cacheNames = RedisConfig.PLAN_CALC,
			key = "T(com.ottnavi.plan.service.PlanSolveService).ENGINE_RESULT_VERSION + ':' + #inputHash")
	public PlanCalcResults solve(String inputHash, PlanInput input) {
		// 1. 요청마다 새 기록용 감싸개로 세 유형을 푼다
		RecordingPlanSolver recorder = new RecordingPlanSolver(solverSelector, exactSolver);
		Map<PlanType, SolveResult> solved = new PlanTypeSolver(recorder).solveAll(input);

		// 2. 유형 순서대로 캐시에 둘 수 있는 값으로 바꾼다
		List<PlanCalcResult> results = new ArrayList<>();
		for (PlanType planType : PlanType.values()) {
			RecordingPlanSolver.Measurement measurement = recorder.measurementOf(planType);
			results.add(PlanCalcResult.from(solved.get(planType), measurement.candidateCount(), measurement.elapsedMs()));
		}
		log.info("플랜 계산 실행(캐시 미스): inputHash={}, 유형 수={}", inputHash, results.size());
		return new PlanCalcResults(results);
	}
}
