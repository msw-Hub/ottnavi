package com.ottnavi.plan.service.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.solver.ExactSolver;
import com.ottnavi.engine.solver.GreedySolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 정답 세트 측정 서비스 테스트. Spring 컨텍스트 없이 직접 조립한다. */
class PlanBenchmarkServiceTest {

	private final BenchmarkCaseLoader loader = new BenchmarkCaseLoader();
	private final PlanBenchmarkService service = new PlanBenchmarkService(loader, new ExactSolver(), new GreedySolver());

	@Test
	@DisplayName("21건 전부 측정되고 두 알고리즘 모두 기대 결과와 같으며 입력 해시가 채워진다")
	void 전체_측정() {
		long start = System.nanoTime();
		List<BenchmarkCaseResult> results = service.measure();
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		assertThat(results).hasSizeGreaterThanOrEqualTo(21);
		for (BenchmarkCaseResult result : results) {
			assertThat(result.inputHash()).as(result.caseName()).isNotBlank();
			assertThat(result.candidateCount()).as(result.caseName()).isPositive();
			assertThat(result.exact()).as(result.caseName()).isNotNull();
			assertThat(result.exact().matchesExpected()).as("%s 완전탐색", result.caseName()).isTrue();
			assertThat(result.greedy().matchesExpected()).as("%s 그리디", result.caseName()).isTrue();
			System.out.printf("[bench] %s cand=%d exact=%dus greedy=%dus must%+d score%+d cost%+d%n", result.caseName(),
					result.candidateCount(), result.exact().elapsedMicrosMedian(), result.greedy().elapsedMicrosMedian(),
					result.mustDiff(), result.scoreDiff(), result.costDiff());
		}
		System.out.println("[bench] 총 소요 " + elapsedMs + "ms");
	}

	@Test
	@DisplayName("그리디가 완전탐색보다 나쁜 케이스는 차이 값에 드러난다")
	void 그리디_차이() {
		BenchmarkCaseResult worse = service.measure().stream()
				.filter(result -> result.caseName().contains("greedy-worse"))
				.findFirst().orElseThrow();

		assertThat(worse.mustDiff() < 0 || worse.scoreDiff() < 0 || worse.costDiff() != 0).isTrue();
	}

	@Test
	@DisplayName("중앙값은 3개 값을 정렬한 가운데 값이고 입력 배열은 바뀌지 않는다")
	void 중앙값() {
		long[] values = {30, 10, 20};

		assertThat(MedianTimer.median(values)).isEqualTo(20);
		assertThat(values).containsExactly(30, 10, 20);
	}

	@Test
	@DisplayName("인덱스 파일의 이름이 engine-benchmark 폴더의 JSON 파일과 정확히 일치한다")
	void 인덱스와_폴더_일치() throws IOException {
		List<String> files = new ArrayList<>();
		try (Stream<Path> paths = Files.list(Path.of("src", "main", "resources", "engine-benchmark"))) {
			for (Path path : paths.toList()) {
				String name = path.getFileName().toString();
				if (name.endsWith(".json")) {
					files.add(name.substring(0, name.length() - ".json".length()));
				}
			}
		}
		List<String> indexed = new ArrayList<>();
		for (BenchmarkCase benchmarkCase : loader.loadAll()) {
			indexed.add(benchmarkCase.name());
		}

		assertThat(indexed).containsExactlyInAnyOrderElementsOf(files);
	}
}
