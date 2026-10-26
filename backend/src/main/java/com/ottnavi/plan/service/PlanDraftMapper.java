package com.ottnavi.plan.service;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.ProductPriceInfo;
import com.ottnavi.product.domain.enums.PriceSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 계산 결과를 DB·응답에 담을 값(달별 상품·비용, 조각 번호)으로 바꾸는 규칙 모음. 프레임워크에 의존하지 않는 순수 계산이다.
 * 저장(PlanDraftWriter)과 응답 조립(PlanDraftViewAssembler)이 같은 규칙을 쓰도록 한곳에 둔다.
 */
public final class PlanDraftMapper {

	private PlanDraftMapper() {
	}

	/** 달별 상품 한 건. 가격 출처는 달마다 다를 수 있다. */
	public record MonthProduct(long productId, int appliedPrice, PriceSource priceSource) {
	}

	/** 저장·응답에 쓸 한 달의 값. */
	public record MonthDraft(int monthIndex, LocalDate month, int cost, List<MonthProduct> products) {
	}

	/** 긴 시즌 조각의 위치. 나뉘지 않으면 index 1, count 1이다. */
	public record Segment(int index, int count) {

		/** 앞 달에서 이어 보는 조각인지 */
		public boolean isContinuation() {
			return index > 1;
		}
	}

	/**
	 * 유형 하나의 달별 상품과 비용을 만든다.
	 * 그 달 상품 = 그 달 선택 집합 ∪ 그 달 배정에 쓰인 상품(중복 없음, ID 오름차순).
	 * 비용은 엔진의 달 비용 규칙과 같다(PRD 5.4 구독 상태 반영): FREE는 모든 달 0원, 구독 중은 0번째 달만 0원이다.
	 */
	public static List<MonthDraft> months(PlanInputBundle bundle, PlanCalcResult result) {
		List<MonthDraft> months = new ArrayList<>();
		for (int monthIndex : bundle.monthIndexes()) {
			// 1. 그 달 선택 집합과 그 달 배정에 쓰인 상품을 하나의 집합으로 모은다
			Set<Long> productIds = new TreeSet<>(result.productIdsByMonth().get(monthIndex));
			for (PlanCalcResult.Unit unit : result.units()) {
				for (PlanCalcResult.Slot slot : unit.assignments()) {
					if (slot.monthIndex() == monthIndex) {
						productIds.add(slot.productId());
					}
				}
			}

			// 2. 상품마다 그 달의 가격 출처를 정한다
			List<MonthProduct> products = new ArrayList<>();
			for (long productId : productIds) {
				ProductPriceInfo info = bundle.productPrices().get(productId);
				if (info == null) {
					throw new IllegalStateException("계산 결과의 상품 가격 정보가 없다: productId=" + productId);
				}
				products.add(new MonthProduct(productId, info.appliedPrice(), priceSourceOf(info, monthIndex)));
			}

			// 3. 그 달 비용을 더해 한 달 값으로 묶는다
			months.add(new MonthDraft(monthIndex, bundle.startMonth().plusMonths(monthIndex), monthCost(products), products));
		}
		return months;
	}

	/**
	 * 그 달의 가격 출처. 무료 이용은 모든 달 FREE, 구독 중은 0번째 달만 ALREADY_PAID이고 1·2번째 달은 기본 출처(ADMIN·USER_OVERRIDE)다.
	 */
	public static PriceSource priceSourceOf(ProductPriceInfo info, int monthIndex) {
		if (info.priceSource() == PriceSource.FREE) {
			return PriceSource.FREE;
		}
		if (info.priceSource() == PriceSource.ALREADY_PAID && monthIndex > 0) {
			return info.basePriceSource();
		}
		return info.priceSource();
	}

	/**
	 * 배정에 표시할 서비스 ID(PRD 11절 33번, Task 076). plan_assignment.ott_service_id가 단일값이라 번들이면 하나를 골라야 한다.
	 * 규칙: (작품이 AVAILABLE인 서비스) ∩ (상품의 구성 서비스) 중 ID가 가장 작은 것. 단품은 항상 자기 서비스다.
	 * 표시용이라 비용에는 영향이 없다. 교집합이 비면(제공 상태가 배정 뒤에 바뀐 경우 등) 구성 서비스 중 가장 작은 ID를 쓴다.
	 *
	 * @param availableServiceIds 작품(시청 단위)이 AVAILABLE인 서비스 ID들
	 */
	public static long assignedServiceId(ProductPriceInfo info, Set<Long> availableServiceIds) {
		for (Long serviceId : info.ottServiceIds()) { // 오름차순이라 처음 맞는 것이 가장 작은 ID다
			if (availableServiceIds.contains(serviceId)) {
				return serviceId;
			}
		}
		return info.ottServiceIds().get(0);
	}

	/** 시청 단위 요약에서 AVAILABLE인 서비스 ID를 모은다. */
	public static Set<Long> availableServiceIds(WatchUnitSummary summary) {
		Set<Long> ids = new HashSet<>();
		for (ServiceStatus status : summary.availabilities()) {
			if (status.status() == AvailabilityStatus.AVAILABLE) {
				ids.add(status.ottServiceId());
			}
		}
		return ids;
	}

	/** 그 달 비용(원). FREE와 ALREADY_PAID인 상품은 더하지 않는다. */
	public static int monthCost(List<MonthProduct> products) {
		long cost = 0;
		for (MonthProduct product : products) {
			if (product.priceSource() == PriceSource.FREE || product.priceSource() == PriceSource.ALREADY_PAID) {
				continue;
			}
			cost += product.appliedPrice();
		}
		return Math.toIntExact(cost);
	}

	/**
	 * 시청 단위의 배정 조각 중 그 달 조각의 위치를 구한다. 조각은 달 순서로 세며 한 단위는 달마다 최대 한 조각이다.
	 * 예: 0·1·2번째 달에 걸친 시즌은 1/3, 2/3, 3/3이고 두 번째·세 번째 조각이 이어 보기다.
	 */
	public static Segment segmentOf(PlanCalcResult.Unit unit, int monthIndex) {
		int before = 0;
		boolean found = false;
		for (PlanCalcResult.Slot slot : unit.assignments()) {
			if (slot.monthIndex() < monthIndex) {
				before++;
			}
			if (slot.monthIndex() == monthIndex) {
				found = true;
			}
		}
		if (!found) {
			throw new IllegalStateException("그 달에 배정이 없는 단위다: unitId=" + unit.unitId() + ", monthIndex=" + monthIndex);
		}
		return new Segment(before + 1, unit.assignments().size());
	}

	/** 꼭 볼 작품(MUST) 수. 입력 단위의 우선순위로 센다. */
	public static int mustTotal(PlanInput planInput) {
		int count = 0;
		for (WatchUnit unit : planInput.units()) {
			if (unit.priority() == Priority.MUST) {
				count++;
			}
		}
		return count;
	}

	/** 결과의 단위들을 시청 단위 ID로 찾을 수 있게 한다. */
	public static Map<Long, PlanCalcResult.Unit> unitsById(PlanCalcResult result) {
		Map<Long, PlanCalcResult.Unit> byId = new HashMap<>();
		for (PlanCalcResult.Unit unit : result.units()) {
			byId.put(unit.unitId(), unit);
		}
		return byId;
	}
}
