package com.ottnavi.plan.service.benchmark;

/** 정답 세트 케이스 하나의 측정 결과. 후보 수가 상한을 넘어 완전탐색을 건너뛰면 exact는 null이다. */
public record BenchmarkCaseResult(
		String caseName,          // 케이스 이름
		String inputHash,         // 입력 해시(InputHasher, 데이터 기준일 포함)
		long candidateCount,      // 완전탐색 기준 후보 조합 수(추천형, 그리디 행에도 같은 값을 쓴다)
		SolverMeasurement exact,  // 완전탐색 결과(건너뛰면 null)
		SolverMeasurement greedy  // 그리디 결과
) {

	/** 그리디 − 완전탐색의 꼭 시청 완료 수 차이. 완전탐색을 건너뛰었으면 null. */
	public Integer mustDiff() {
		if (exact == null) {
			return null;
		}
		return greedy.mustCompleted() - exact.mustCompleted();
	}

	/** 그리디 − 완전탐색의 점수 차이. 완전탐색을 건너뛰었으면 null. */
	public Integer scoreDiff() {
		if (exact == null) {
			return null;
		}
		return greedy.score() - exact.score();
	}

	/** 그리디 − 완전탐색의 비용 차이(원). 완전탐색을 건너뛰었으면 null. */
	public Integer costDiff() {
		if (exact == null) {
			return null;
		}
		return greedy.totalCost() - exact.totalCost();
	}
}
