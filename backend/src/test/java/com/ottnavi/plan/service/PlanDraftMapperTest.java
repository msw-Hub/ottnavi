package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.plan.service.PlanDraftMapper.MonthDraft;
import com.ottnavi.plan.service.PlanDraftMapper.Segment;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.ProductPriceInfo;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.user.domain.enums.WatchPreset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 계산 결과를 저장 값으로 바꾸는 규칙(Task 064)을 DB 없이 확인한다.
 * 막으려는 사고: 구독 중 상품의 비용이 모든 달에서 빠지거나 모든 달에 더해지는 일, 선택하지 않았지만 배정에 쓴 상품이 달 상품에서 빠지는 일,
 * 달 비용 합이 총비용과 어긋나는 일, 긴 시즌 조각 번호가 틀리는 일.
 */
class PlanDraftMapperTest {

	private static final long P_SUBSCRIBED = 1L; // 구독 중, 기본 출처 ADMIN, 10,000원
	private static final long P_FREE = 2L;       // 무료 이용, 기본 출처 USER_OVERRIDE, 5,000원
	private static final long P_NEW = 3L;        // 미구독, 8,000원

	private PlanInputBundle bundle() {
		Map<Long, ProductPriceInfo> prices = Map.of(
				P_SUBSCRIBED, new ProductPriceInfo(P_SUBSCRIBED, "구독 중", List.of(11L), 10_000, PriceSource.ALREADY_PAID, PriceSource.ADMIN),
				P_FREE, new ProductPriceInfo(P_FREE, "무료", List.of(12L), 5_000, PriceSource.FREE, PriceSource.USER_OVERRIDE),
				P_NEW, new ProductPriceInfo(P_NEW, "신규", List.of(13L), 8_000, PriceSource.ADMIN, PriceSource.ADMIN));
		List<OttProduct> products = List.of(
				new OttProduct(P_SUBSCRIBED, 10_000, OttProductCondition.SUBSCRIBED),
				new OttProduct(P_FREE, 5_000, OttProductCondition.FREE),
				new OttProduct(P_NEW, 8_000, OttProductCondition.NONE));
		List<WatchUnit> units = List.of(
				new WatchUnit(101L, Priority.MUST, 300, Set.of(P_SUBSCRIBED, P_FREE), false),
				new WatchUnit(102L, Priority.WANT, 100, Set.of(P_NEW), false));
		PlanInput input = new PlanInput(units, products, 30_000, 600);
		return new PlanInputBundle(input, 0, prices, Set.of(), Map.of(), Map.of(), Instant.parse("2026-10-15T00:00:00Z"),
				LocalDate.of(2026, 10, 1), List.of(0, 1, 2), 30_000, 600, WatchPreset.NORMAL);
	}

	/** 선택: 0달 [구독 중], 1달 [구독 중, 신규], 2달 [신규]. 단위 101은 0달 무료 상품에서 300분(선택 밖), 단위 102는 1달 신규에서 100분. */
	private PlanCalcResult result() {
		List<PlanCalcResult.Unit> units = List.of(
				new PlanCalcResult.Unit(101L, true, 0, null, List.of(new PlanCalcResult.Slot(0, P_FREE, 300))),
				new PlanCalcResult.Unit(102L, true, 1, null, List.of(new PlanCalcResult.Slot(1, P_NEW, 100))));
		return new PlanCalcResult(PlanType.RECOMMENDED, SolverType.EXACT, 1, 2, 26_000,
				List.of(List.of(P_SUBSCRIBED), List.of(P_SUBSCRIBED, P_NEW), List.of(P_NEW)), units, 8, 3L);
	}

	@Test
	void 달별_상품은_선택과_배정_상품의_합집합이고_구독_중_상품의_출처는_달마다_다르다() {
		// when
		List<MonthDraft> months = PlanDraftMapper.months(bundle(), result());

		// then: 0달은 구독 중 ALREADY_PAID, 무료 FREE(선택 밖이지만 배정에 쓰여 들어온다), 1·2달 구독 중은 기본 출처 ADMIN
		assertThat(months).hasSize(3);
		assertThat(months.get(0).month()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(months.get(0).products()).extracting(PlanDraftMapper.MonthProduct::productId)
				.containsExactly(P_SUBSCRIBED, P_FREE);
		assertThat(months.get(0).products()).extracting(PlanDraftMapper.MonthProduct::priceSource)
				.containsExactly(PriceSource.ALREADY_PAID, PriceSource.FREE);
		assertThat(months.get(1).month()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(months.get(1).products()).extracting(PlanDraftMapper.MonthProduct::productId)
				.containsExactly(P_SUBSCRIBED, P_NEW);
		assertThat(months.get(1).products()).extracting(PlanDraftMapper.MonthProduct::priceSource)
				.containsExactly(PriceSource.ADMIN, PriceSource.ADMIN);
		assertThat(months.get(2).products()).extracting(PlanDraftMapper.MonthProduct::productId).containsExactly(P_NEW);
		// 원래 월 요금은 출처와 상관없이 그대로다
		assertThat(months.get(0).products().get(0).appliedPrice()).isEqualTo(10_000);
		assertThat(months.get(0).products().get(1).appliedPrice()).isEqualTo(5_000);
	}

	@Test
	void 달_비용은_무료와_첫_달_구독_중을_빼며_세_달_합이_총비용과_같다() {
		// when
		List<MonthDraft> months = PlanDraftMapper.months(bundle(), result());

		// then: 0달 0원, 1달 구독 중 10,000 + 신규 8,000, 2달 신규 8,000
		assertThat(months.get(0).cost()).isEqualTo(0);
		assertThat(months.get(1).cost()).isEqualTo(18_000);
		assertThat(months.get(2).cost()).isEqualTo(8_000);
		assertThat(months.get(0).cost() + months.get(1).cost() + months.get(2).cost()).isEqualTo(result().totalCost());
	}

	@Test
	void 가격_정보가_없는_상품이_결과에_있으면_예외다() {
		// given: 결과가 번들에 없는 상품 99를 선택했다
		PlanCalcResult broken = new PlanCalcResult(PlanType.SAVER, SolverType.GREEDY, 0, 0, 0,
				List.of(List.of(99L), List.of(), List.of()), List.of(), 1, 1L);

		// when
		// then
		assertThatThrownBy(() -> PlanDraftMapper.months(bundle(), broken)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 긴_시즌의_조각_번호는_달_순서로_세고_첫_조각이_아니면_이어_보기다() {
		// given: 0·1·2달에 걸친 시즌과 한 달에 끝나는 영화
		PlanCalcResult.Unit split = new PlanCalcResult.Unit(201L, true, 2, null, List.of(
				new PlanCalcResult.Slot(0, P_NEW, 120), new PlanCalcResult.Slot(1, P_NEW, 120), new PlanCalcResult.Slot(2, P_NEW, 60)));
		PlanCalcResult.Unit single = new PlanCalcResult.Unit(202L, true, 1, null, List.of(new PlanCalcResult.Slot(1, P_NEW, 90)));

		// when
		// then
		assertThat(PlanDraftMapper.segmentOf(split, 0)).isEqualTo(new Segment(1, 3));
		assertThat(PlanDraftMapper.segmentOf(split, 1)).isEqualTo(new Segment(2, 3));
		assertThat(PlanDraftMapper.segmentOf(split, 2)).isEqualTo(new Segment(3, 3));
		assertThat(PlanDraftMapper.segmentOf(split, 0).isContinuation()).isFalse();
		assertThat(PlanDraftMapper.segmentOf(split, 1).isContinuation()).isTrue();
		assertThat(PlanDraftMapper.segmentOf(single, 1)).isEqualTo(new Segment(1, 1));
		assertThat(PlanDraftMapper.segmentOf(single, 1).isContinuation()).isFalse();
		assertThatThrownBy(() -> PlanDraftMapper.segmentOf(single, 0)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 번들의_배정_서비스는_작품이_제공되는_구성_서비스_중_ID가_가장_작은_것이다() {
		// given: 번들(서비스 5·7·9)
		ProductPriceInfo bundleInfo = new ProductPriceInfo(50L, "번들", List.of(5L, 7L, 9L), 16_000, PriceSource.ADMIN, PriceSource.ADMIN);

		// when
		// then: 작품이 7·9에서만 제공되면 7, 5에서도 제공되면 5, 구성 밖(11)에서만 제공되면(교집합이 비면) 구성 중 가장 작은 5
		assertThat(PlanDraftMapper.assignedServiceId(bundleInfo, Set.of(9L, 7L))).isEqualTo(7L);
		assertThat(PlanDraftMapper.assignedServiceId(bundleInfo, Set.of(5L, 7L, 9L, 11L))).isEqualTo(5L);
		assertThat(PlanDraftMapper.assignedServiceId(bundleInfo, Set.of(11L))).isEqualTo(5L);
	}

	@Test
	void 단품의_배정_서비스는_자기_서비스다() {
		ProductPriceInfo single = new ProductPriceInfo(1L, "단품", List.of(11L), 10_000, PriceSource.ADMIN, PriceSource.ADMIN);
		assertThat(PlanDraftMapper.assignedServiceId(single, Set.of(11L, 12L))).isEqualTo(11L);
	}

	@Test
	void 꼭_볼_작품_수는_입력_단위의_MUST_개수다() {
		assertThat(PlanDraftMapper.mustTotal(bundle().planInput())).isEqualTo(1);
	}
}
