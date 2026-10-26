package com.ottnavi.plan.service.benchmark;

/** 알고리즘 하나의 측정 결과(3회 중앙값). */
public record SolverMeasurement(
		String algorithm,          // EXACT 또는 GREEDY
		int mustCompleted,         // 시청 완료한 MUST 수
		int score,                 // WANT 2점·MAYBE 1점 합
		int totalCost,             // 3개월 비용 합(원)
		long elapsedMicrosMedian,  // 소요 시간 중앙값(마이크로초)
		long elapsedMsMedian,      // 소요 시간 중앙값(ms, 내림). DB 저장용
		boolean matchesExpected    // 정답 세트의 기대 결과(must·score·비용)와 같은지
) {
}
