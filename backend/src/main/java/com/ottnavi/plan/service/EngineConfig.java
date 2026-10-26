package com.ottnavi.plan.service;

import com.ottnavi.engine.solver.ExactSolver;
import com.ottnavi.engine.solver.GreedySolver;
import com.ottnavi.engine.solver.SolverSelector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 계산 엔진 빈 설정. 엔진은 프레임워크에 의존하지 않는 순수 Java라 여기서 빈으로 등록한다.
 * PlanTypeSolver는 요청마다 기록용 감싸개를 넣어 새로 만들므로 빈으로 두지 않는다(PlanSolveService).
 */
@Configuration
public class EngineConfig {

	@Bean
	public ExactSolver exactSolver() {
		return new ExactSolver();
	}

	@Bean
	public GreedySolver greedySolver() {
		return new GreedySolver();
	}

	/** 후보 조합 수 상한은 엔진 기본값(Task 040 측정 근거, SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS)을 쓴다. */
	@Bean
	public SolverSelector solverSelector(ExactSolver exactSolver, GreedySolver greedySolver) {
		return new SolverSelector(exactSolver, greedySolver, SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS);
	}
}
