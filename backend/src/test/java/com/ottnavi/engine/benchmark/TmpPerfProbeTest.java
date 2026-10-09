package com.ottnavi.engine.benchmark;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.engine.rule.PlanObjective;
import com.ottnavi.engine.solver.ExactSolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
class TmpPerfProbeTest {

	@Test
	void probe() {
		PlanInput plain = BenchmarkSupport.synthetic(7, 30, 1_200);
		report("시즌 없음", plain);
		List<WatchUnit> seasoned = new ArrayList<>();
		List<WatchUnit> base = new ArrayList<>(plain.units());
		base.sort(java.util.Comparator.comparingLong(WatchUnit::id));
		for (int i = 0; i < base.size(); i++) {
			WatchUnit u = base.get(i);
			if (i < 18) {
				seasoned.add(new WatchUnit(u.id(), u.priority(), u.requiredMinutes(), u.watchableProductIds(), false, (long) (i / 3 + 1), i % 3 + 1));
			} else {
				seasoned.add(u);
			}
		}
		PlanInput withSeasons = new PlanInput(seasoned, plain.products(), plain.monthlyBudget(), plain.monthlyWatchMinutes());
		report("시즌 18개(6x3)+영화 12", withSeasons);
	}

	private static void report(String label, PlanInput input) {
		ExactSolver exact = new ExactSolver();
		PlanObjective rec = PlanObjective.recommended(input);
		exact.solve(input, rec); // 워밍업
		exact.solve(input, rec);
		long[] t = new long[7];
		Object last = null;
		for (int i = 0; i < t.length; i++) {
			long s = System.nanoTime();
			last = exact.solve(input, rec);
			t[i] = System.nanoTime() - s;
		}
		Arrays.sort(t);
		System.out.println("PROBE " + label + " 추천형 완전탐색 중앙값 ms=" + t[3] / 1e6 + " 최소=" + t[0] / 1e6 + " 최대=" + t[6] / 1e6
				+ " 결과=" + ((com.ottnavi.engine.model.SolveResult) last).evaluation().mustCompleted() + "/" + ((com.ottnavi.engine.model.SolveResult) last).evaluation().score());
	}
}
