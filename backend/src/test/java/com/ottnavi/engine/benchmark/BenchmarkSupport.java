package com.ottnavi.engine.benchmark;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/** 성능 측정 테스트가 함께 쓰는 도구: 중앙값 시간 측정, 힙 샘플링, 합성 입력 생성, 숫자 서식. */
final class BenchmarkSupport {

	static final int MEASURED_RUNS = 3;                              // 워밍업 1회 뒤 측정 횟수(중앙값을 쓴다)
	static final long SINGLE_RUN_LIMIT_NANOS = 15_000_000_000L;      // 워밍업이 이 시간을 넘으면 반복하지 않고 그 1회 값만 쓴다(너무 오래 걸리는 입력 보호)
	static final long SEED = 20261010L;                              // 합성 입력의 난수 씨앗(항상 같은 입력)
	static final int AMPLE_BUDGET = 1_000_000;                       // 예산 넉넉(어떤 부분집합도 예산 이하)

	private BenchmarkSupport() {
	}

	/** 측정 결과: 중앙값 시간, 측정 횟수, 풀이 중 힙 최대 사용량과 풀이 전 기준선, 마지막 실행의 결과. */
	record Stats<T>(long medianNanos, int measuredRuns, long peakHeapBytes, long baselineHeapBytes, T result) {

		double millis() {
			return medianNanos / 1_000_000.0;
		}
	}

	/** 워밍업 1회 후 3회 실행해 중앙값을 낸다. 워밍업이 15초를 넘으면 그 1회 값만 쓴다(measuredRuns=1, 워밍업 포함 값). */
	static <T> Stats<T> measure(Supplier<T> task) {
		try (HeapSampler sampler = new HeapSampler()) {
			long start = System.nanoTime();
			T result = task.get();
			long warmupNanos = System.nanoTime() - start;
			if (warmupNanos > SINGLE_RUN_LIMIT_NANOS) {
				return new Stats<>(warmupNanos, 1, sampler.peakBytes(), sampler.baselineBytes(), result);
			}
			long[] times = new long[MEASURED_RUNS];
			for (int run = 0; run < MEASURED_RUNS; run++) {
				start = System.nanoTime();
				result = task.get();
				times[run] = System.nanoTime() - start;
			}
			Arrays.sort(times);
			return new Stats<>(times[MEASURED_RUNS / 2], MEASURED_RUNS, sampler.peakBytes(), sampler.baselineBytes(), result);
		}
	}

	/** 실행 중 사용 중인 힙(used)을 2ms마다 읽어 최대값을 남기는 샘플러. 만들 때 GC로 기준선을 잡는다. 최대값에는 아직 수거되지 않은 쓰레기도 들어간다. */
	static final class HeapSampler implements AutoCloseable {

		private volatile boolean isRunning = true;
		private volatile long peakBytes;   // 샘플링 중 본 used 최대값
		private final long baselineBytes;  // 시작 직전 GC 후 used
		private final Thread thread;

		HeapSampler() {
			System.gc();
			baselineBytes = usedBytes();
			peakBytes = baselineBytes;
			thread = new Thread(() -> {
				while (isRunning) {
					peakBytes = Math.max(peakBytes, usedBytes());
					try {
						Thread.sleep(2);
					} catch (InterruptedException e) {
						return;
					}
				}
			}, "heap-sampler");
			thread.setDaemon(true);
			thread.start();
		}

		long peakBytes() {
			return peakBytes;
		}

		long baselineBytes() {
			return baselineBytes;
		}

		@Override
		public void close() {
			isRunning = false;
			try {
				thread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		private static long usedBytes() {
			Runtime runtime = Runtime.getRuntime();
			return runtime.totalMemory() - runtime.freeMemory();
		}
	}

	/**
	 * 합성 입력: 상품 productCount개(전부 구독 없음, 가격 5,000~16,000원), 시청 단위 unitCount개, 예산 넉넉.
	 * 단위 i는 상품 (i % productCount)를 반드시 볼 수 있어 모든 상품이 후보가 되고, 1~2개 상품을 더 볼 수 있다.
	 * 우선순위는 MUST 20%·WANT 40%·MAYBE 40%, 7번째마다 긴 시즌(360~600분)이고 나머지는 45~165분.
	 */
	static PlanInput synthetic(int productCount, int unitCount, int monthlyWatchMinutes) {
		Random random = new Random(SEED + productCount * 1000L + unitCount);
		List<OttProduct> products = new ArrayList<>();
		for (int i = 0; i < productCount; i++) {
			products.add(new OttProduct(i + 1L, 5_000 + (i * 1_700) % 12_000, OttProductCondition.NONE));
		}
		List<WatchUnit> units = new ArrayList<>();
		for (int i = 0; i < unitCount; i++) {
			Priority priority = i % 5 == 0 ? Priority.MUST : (i % 5 <= 2 ? Priority.WANT : Priority.MAYBE);
			int minutes = i % 7 == 6 ? 360 + random.nextInt(241) : 45 + random.nextInt(121);
			Set<Long> watchable = new HashSet<>();
			watchable.add((long) (i % productCount) + 1);
			int extra = 1 + random.nextInt(2);
			for (int k = 0; k < extra; k++) {
				watchable.add((long) random.nextInt(productCount) + 1);
			}
			units.add(new WatchUnit(i + 1L, priority, minutes, watchable, false));
		}
		return new PlanInput(units, products, AMPLE_BUDGET, monthlyWatchMinutes);
	}

	/** "꼭 시청 완료/점수/비용" 한 칸 문자열. */
	static String summary(Evaluation evaluation) {
		return evaluation.mustCompleted() + "/" + evaluation.score() + "/" + String.format("%,d", evaluation.totalCost());
	}

	/** 밀리초 서식(천 단위 구분, 소수 1자리). */
	static String ms(double millis) {
		return String.format("%,.1f", millis);
	}

	/** 바이트를 MB(소수 0자리)로. */
	static String mb(long bytes) {
		return String.format("%,d", bytes / (1024 * 1024));
	}

	/** 시간 칸: 중앙값 ms, 1회만 쟀으면 "(1회)"를 붙인다. */
	static String timeCell(Stats<?> stats) {
		return ms(stats.millis()) + (stats.measuredRuns() == 1 ? " (1회)" : "");
	}

	/** 힙 칸: 풀이 중 최대 used MB / 풀이 전 기준선 MB. */
	static String heapCell(Stats<?> stats) {
		return mb(stats.peakHeapBytes()) + " / " + mb(stats.baselineHeapBytes());
	}

}
