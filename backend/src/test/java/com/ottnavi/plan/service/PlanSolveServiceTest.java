package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.InputHasher;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanCalcResults;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;

/**
 * 플랜 계산 서비스(Task 064)를 실제 Redis로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 세 유형 중 하나가 빠지는 일, 캐시 값을 JSON으로 읽지 못해 매번 다시 계산하는 일, 캐시 키 형식이 어긋나는 일.
 */
class PlanSolveServiceTest extends IntegrationTestSupport {

	private static final LocalDate DATA_AS_OF = LocalDate.of(2026, 10, 15);

	@Autowired private PlanSolveService planSolveService;
	@Autowired private CacheManager cacheManager;

	@AfterEach
	void 정리() {
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
	}

	/** 상품 2개(1만원·2만원), 단위 3개로 예산 20,000원 안에서 풀 수 있는 작은 입력. */
	private PlanInput smallInput() {
		List<OttProduct> products = List.of(
				new OttProduct(1L, 10_000, OttProductCondition.NONE),
				new OttProduct(2L, 20_000, OttProductCondition.NONE));
		List<WatchUnit> units = List.of(
				new WatchUnit(101L, Priority.MUST, 120, Set.of(1L), false),
				new WatchUnit(102L, Priority.WANT, 300, Set.of(1L, 2L), false),
				new WatchUnit(103L, Priority.MAYBE, 90, Set.of(), false));
		return new PlanInput(units, products, 20_000, 600);
	}

	@Test
	void 세_유형_결과를_돌려주고_같은_해시의_두_번째_호출은_캐시에서_온다() {
		// given
		PlanInput input = smallInput();
		String hash = InputHasher.hash(input, DATA_AS_OF);

		// when: 첫 호출은 계산한다
		PlanCalcResults first = planSolveService.solve(hash, input);

		// then: 추천형·절약형·간편형 순서로 세 개이고 후보 수·알고리즘이 채워져 있다
		assertThat(first.results()).extracting(PlanCalcResult::planType)
				.containsExactly(PlanType.RECOMMENDED, PlanType.SAVER, PlanType.SIMPLE);
		for (PlanCalcResult result : first.results()) {
			assertThat(result.solverType()).isNotNull();
			assertThat(result.candidateCount()).isPositive();
			assertThat(result.productIdsByMonth()).hasSize(3);
			assertThat(result.units()).hasSize(3);
		}

		// then: 캐시에 "e1:{해시}" 키로 저장됐다
		Cache cache = cacheManager.getCache(RedisConfig.PLAN_CALC);
		String key = PlanSolveService.ENGINE_RESULT_VERSION + ":" + hash;
		assertThat(cache.get(key, PlanCalcResults.class)).isEqualTo(first);

		// when: 캐시 값을 표식 값으로 바꾼 뒤 같은 해시로 다시 호출한다(본문이 실행되면 표식이 사라진다)
		List<PlanCalcResult> marked = new ArrayList<>();
		for (PlanCalcResult result : first.results()) {
			marked.add(new PlanCalcResult(result.planType(), result.solverType(), result.mustCompleted(), result.score(),
					result.totalCost(), result.productIdsByMonth(), result.units(), 777, result.elapsedMs()));
		}
		cache.put(key, new PlanCalcResults(marked));
		PlanCalcResults second = planSolveService.solve(hash, input);

		// then: 캐시에서 온 값이다
		assertThat(second.results()).allSatisfy(result -> assertThat(result.candidateCount()).isEqualTo(777));
	}

	@Test
	void 계산_결과는_JSON으로_쓰고_읽어도_같다() {
		// given
		PlanInput input = smallInput();
		PlanCalcResults results = planSolveService.solve(InputHasher.hash(input, DATA_AS_OF), input);
		JacksonJsonRedisSerializer<PlanCalcResults> serializer = new JacksonJsonRedisSerializer<>(PlanCalcResults.class);

		// when
		PlanCalcResults restored = serializer.deserialize(serializer.serialize(results));

		// then: 중첩 record·List·null(미배정 단위의 다 본 달) 포함 같다
		assertThat(restored).isEqualTo(results);
	}
}
