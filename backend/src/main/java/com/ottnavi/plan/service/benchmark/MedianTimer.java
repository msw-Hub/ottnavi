package com.ottnavi.plan.service.benchmark;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.SolveResult;
import java.util.Arrays;
import java.util.function.Supplier;

/** 풀이를 워밍업 1회 뒤 3회 측정해 중앙값 시간을 내는 도우미. 반복 결과가 다르면 엔진 이상으로 본다. */
final class MedianTimer {

	static final int MEASURED_RUNS = 3; // 워밍업 뒤 측정 횟수

	/** 측정 결과: 중앙값 시간(나노초)과 마지막 실행의 결과. */
	record Timed(long medianNanos, SolveResult result) {
	}

	private MedianTimer() {
	}

	/** 워밍업 1회를 버리고 3회 재서 중앙값을 낸다. 반복한 결과(조합·점수·비용·must)가 서로 다르면 IllegalStateException. */
	static Timed measure(String label, Supplier<SolveResult> task) {
		// 1. 워밍업 1회: 시간은 버리고 결과는 비교 기준으로 쓴다
		SolveResult first = task.get();
		// 2. 3회 측정하면서 결과가 같은지 확인한다
		long[] times = new long[MEASURED_RUNS];
		SolveResult last = first;
		for (int run = 0; run < MEASURED_RUNS; run++) {
			long start = System.nanoTime();
			last = task.get();
			times[run] = System.nanoTime() - start;
			if (!sameResult(first.evaluation(), last.evaluation())) {
				throw new IllegalStateException("같은 입력의 반복 풀이 결과가 다르다(엔진 이상): " + label);
			}
		}
		// 3. 중앙값을 낸다
		return new Timed(median(times), last);
	}

	/** 값 배열의 중앙값(정렬한 가운데 값, 짝수 개면 위쪽 가운데). 입력 배열은 바꾸지 않는다. */
	static long median(long[] values) {
		long[] sorted = values.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}

	private static boolean sameResult(Evaluation a, Evaluation b) {
		return a.selection().equals(b.selection())
				&& a.mustCompleted() == b.mustCompleted()
				&& a.score() == b.score()
				&& a.totalCost() == b.totalCost();
	}
}
