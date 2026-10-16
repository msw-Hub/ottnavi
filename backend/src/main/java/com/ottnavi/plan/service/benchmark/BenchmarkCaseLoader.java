package com.ottnavi.plan.service.benchmark;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.plan.service.benchmark.BenchmarkCase.ProductSpec;
import com.ottnavi.plan.service.benchmark.BenchmarkCase.UnitSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 운영용 정답 세트 로더. 인덱스 파일(engine-benchmark/index.txt)의 이름 순서대로 케이스 JSON을 읽는다.
 * 실행 jar 안에서 와일드카드 탐색이 동작하는지 확인하지 못해 목록 파일 방식을 쓴다(목록과 폴더의 일치는 테스트가 검사한다).
 */
@Component
public class BenchmarkCaseLoader {

	public static final String RESOURCE_DIR = "engine-benchmark/";   // 정답 세트 위치(main/resources)
	public static final String INDEX_FILE = RESOURCE_DIR + "index.txt"; // 케이스 이름(확장자 제외)을 한 줄에 하나씩 적은 목록

	private static final ObjectMapper MAPPER = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // 오타 난 필드가 조용히 무시되지 않게 한다
			.build();

	/** 정답 세트 전체를 인덱스 파일 순서(이름 순)로 읽는다. */
	public List<BenchmarkCase> loadAll() {
		// 1. 인덱스 파일에서 케이스 이름을 모은다
		List<String> names = new ArrayList<>();
		try (InputStream in = new ClassPathResource(INDEX_FILE).getInputStream()) {
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				if (!line.isBlank()) {
					names.add(line.strip());
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("정답 세트 인덱스를 읽지 못했다: " + INDEX_FILE, e);
		}
		// 2. 이름마다 JSON을 읽어 검증한다
		List<BenchmarkCase> cases = new ArrayList<>();
		for (String name : names) {
			try (InputStream in = new ClassPathResource(RESOURCE_DIR + name + ".json").getInputStream()) {
				BenchmarkCase benchmarkCase = MAPPER.readValue(in, BenchmarkCase.class);
				validate(benchmarkCase);
				cases.add(benchmarkCase);
			} catch (IOException e) {
				throw new UncheckedIOException("정답 세트를 읽지 못했다: " + name, e);
			}
		}
		return cases;
	}

	/** 시청 단위의 titleId와 seasonNumber는 둘 다 있거나 둘 다 없어야 한다(영화는 둘 다 없음). */
	public void validate(BenchmarkCase benchmarkCase) {
		for (UnitSpec unit : benchmarkCase.units()) {
			if ((unit.titleId() == null) != (unit.seasonNumber() == null)) {
				throw new IllegalArgumentException("정답 세트 단위의 titleId와 seasonNumber는 둘 다 있거나 둘 다 없어야 한다: case="
						+ benchmarkCase.name() + ", unitId=" + unit.id());
			}
		}
	}

	/** 케이스의 입력 부분을 엔진 입력으로 바꾼다. */
	public PlanInput toInput(BenchmarkCase benchmarkCase) {
		validate(benchmarkCase);
		List<OttProduct> products = new ArrayList<>();
		for (ProductSpec spec : benchmarkCase.products()) {
			products.add(new OttProduct(spec.id(), spec.monthlyPrice(), spec.condition()));
		}
		List<WatchUnit> units = new ArrayList<>();
		for (UnitSpec spec : benchmarkCase.units()) {
			units.add(new WatchUnit(spec.id(), spec.priority(), spec.requiredMinutes(),
					new HashSet<>(spec.watchableProductIds()), spec.providerUnknown(), spec.titleId(), spec.seasonNumber()));
		}
		return new PlanInput(units, products, benchmarkCase.monthlyBudget(), benchmarkCase.monthlyWatchMinutes());
	}

	/** 데이터 기준일을 날짜로 바꾼다. */
	public LocalDate dataAsOf(BenchmarkCase benchmarkCase) {
		return LocalDate.parse(benchmarkCase.dataAsOf());
	}
}
