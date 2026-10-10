package com.ottnavi.engine.benchmark;

import com.ottnavi.engine.benchmark.BenchmarkCase.ExpectedPlan;
import com.ottnavi.engine.benchmark.BenchmarkCase.ProductSpec;
import com.ottnavi.engine.benchmark.BenchmarkCase.UnitSpec;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * engine-benchmark/*.json 정답 세트를 읽어 엔진 입력으로 바꾸는 테스트용 로더(Jackson 3).
 * 오타 난 필드가 조용히 무시되지 않도록 알 수 없는 필드는 오류로 처리한다.
 */
public final class BenchmarkLoader {

	public static final String RESOURCE_PATTERN = "classpath:engine-benchmark/*.json"; // 정답 세트 위치(main/resources)

	private static final ObjectMapper MAPPER = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private BenchmarkLoader() {
	}

	/** 정답 세트 전체를 파일명 순으로 읽는다. */
	public static List<BenchmarkCase> loadAll() {
		try {
			Resource[] resources = new PathMatchingResourcePatternResolver().getResources(RESOURCE_PATTERN);
			List<Resource> sorted = new ArrayList<>(Arrays.asList(resources));
			sorted.sort(Comparator.comparing(Resource::getFilename));
			List<BenchmarkCase> cases = new ArrayList<>();
			for (Resource resource : sorted) {
				try (InputStream in = resource.getInputStream()) {
					BenchmarkCase benchmarkCase = MAPPER.readValue(in, BenchmarkCase.class);
					validate(benchmarkCase);
					cases.add(benchmarkCase);
				}
			}
			return cases;
		} catch (IOException e) {
			throw new UncheckedIOException("정답 세트를 읽지 못했다", e);
		}
	}

	/** 스키마 검사: 시청 단위의 titleId와 seasonNumber는 둘 다 있거나 둘 다 없어야 한다(영화는 둘 다 없음). 어긋나면 케이스 이름과 단위 ID를 넣어 예외를 던진다. */
	public static void validate(BenchmarkCase benchmarkCase) {
		for (UnitSpec unit : benchmarkCase.units()) {
			if ((unit.titleId() == null) != (unit.seasonNumber() == null)) {
				throw new IllegalArgumentException("정답 세트 단위의 titleId와 seasonNumber는 둘 다 있거나 둘 다 없어야 한다: case="
						+ benchmarkCase.name() + ", unitId=" + unit.id());
			}
		}
	}

	/** 케이스의 입력 부분을 엔진 입력으로 바꾼다. */
	public static PlanInput toInput(BenchmarkCase benchmarkCase) {
		validate(benchmarkCase);
		List<OttProduct> products = benchmarkCase.products().stream()
				.map((ProductSpec spec) -> new OttProduct(spec.id(), spec.monthlyPrice(), spec.condition()))
				.toList();
		List<WatchUnit> units = benchmarkCase.units().stream()
				.map((UnitSpec spec) -> new WatchUnit(spec.id(), spec.priority(), spec.requiredMinutes(),
						new HashSet<>(spec.watchableProductIds()), spec.providerUnknown(), spec.titleId(), spec.seasonNumber()))
				.toList();
		return new PlanInput(units, products, benchmarkCase.monthlyBudget(), benchmarkCase.monthlyWatchMinutes());
	}

	/** 데이터 기준일을 날짜로 바꾼다. */
	public static LocalDate dataAsOf(BenchmarkCase benchmarkCase) {
		return LocalDate.parse(benchmarkCase.dataAsOf());
	}

	/** 평가 결과를 파일에 적는 기대 결과 모양으로 바꾼다. */
	public static ExpectedPlan toExpected(Evaluation evaluation) {
		List<List<Long>> months = new ArrayList<>();
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			months.add(new ArrayList<>(evaluation.selection().productIdsOf(month))); // Selection이 이미 오름차순
		}
		return new ExpectedPlan(months, evaluation.mustCompleted(), evaluation.score(), evaluation.totalCost());
	}

	/** 평가 결과에서 미배정 단위의 이유 코드를 단위 ID 오름차순으로 모은다(파일의 expectedUnitReasons 한 유형분과 같은 모양). */
	public static Map<Long, UnitReason> unscheduledReasons(Evaluation evaluation) {
		Map<Long, UnitReason> reasons = new TreeMap<>();
		for (UnitResult result : evaluation.units()) {
			if (!result.scheduled()) {
				reasons.put(result.watchUnitId(), result.reason());
			}
		}
		return reasons;
	}

	/** 기대 결과의 달별 상품 ID를 구독 조합으로 바꾼다. */
	public static Selection toSelection(ExpectedPlan expected) {
		List<Set<Long>> months = expected.months().stream().map(month -> (Set<Long>) new HashSet<>(month)).toList();
		return new Selection(months);
	}
}
