package com.ottnavi.plan.service;

import com.ottnavi.catalog.service.WatchUnitQueryService;
import com.ottnavi.catalog.service.model.WatchUnitSummaries;
import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.entity.PlanAssignment;
import com.ottnavi.plan.domain.entity.PlanItem;
import com.ottnavi.plan.domain.entity.PlanMonth;
import com.ottnavi.plan.domain.entity.PlanMonthProduct;
import com.ottnavi.plan.domain.enums.PlanItemOutcome;
import com.ottnavi.plan.domain.repository.PlanAssignmentRepository;
import com.ottnavi.plan.domain.repository.PlanItemRepository;
import com.ottnavi.plan.domain.repository.PlanMonthProductRepository;
import com.ottnavi.plan.domain.repository.PlanMonthRepository;
import com.ottnavi.plan.domain.rule.PlanSnapshot;
import com.ottnavi.product.service.ProductSummaryQueryService;
import com.ottnavi.product.service.model.ProductSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 플랜(DB)을 변경 판정용 값(PlanSnapshot)으로 바꾼다(Task 069). PlanViewLoader의 출력은 시청 단위 ID와
 * 시청 완료 달의 서비스가 없어 쓸 수 없으므로 같은 테이블을 직접 읽는다. 플랜 수와 무관하게 쿼리 수는 일정하다.
 * 호출하는 쪽의 트랜잭션 안에서 불러야 한다(읽기 전용이 기본, 재계산 쓰기 트랜잭션 안에서도 쓴다).
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanSnapshotLoader {

	private final PlanMonthRepository planMonthRepository;
	private final PlanMonthProductRepository planMonthProductRepository;
	private final PlanItemRepository planItemRepository;
	private final PlanAssignmentRepository planAssignmentRepository;
	private final WatchUnitQueryService watchUnitQueryService;
	private final ProductSummaryQueryService productSummaryQueryService;

	/** 플랜들의 스냅샷을 플랜 ID로 찾을 수 있게 돌려준다 */
	public Map<Long, PlanSnapshot> load(List<Plan> plans) {
		Map<Long, PlanSnapshot> snapshots = new HashMap<>();
		if (plans.isEmpty()) {
			return snapshots;
		}

		// 1. 하위 행을 일괄 조회해 상위 ID별로 묶는다
		List<Long> planIds = new ArrayList<>();
		for (Plan plan : plans) {
			planIds.add(plan.getId());
		}
		Map<Long, List<PlanMonth>> monthsByPlan = new HashMap<>();
		for (PlanMonth month : planMonthRepository.findByPlanIds(planIds)) {
			addTo(monthsByPlan, month.getPlan().getId(), month);
		}
		Map<Long, List<PlanMonthProduct>> productsByMonth = new HashMap<>();
		Set<Long> productIds = new HashSet<>();
		for (PlanMonthProduct product : planMonthProductRepository.findByPlanIds(planIds)) {
			addTo(productsByMonth, product.getPlanMonth().getId(), product);
			productIds.add(product.getProductId());
		}
		Map<Long, List<PlanItem>> itemsByPlan = new HashMap<>();
		Set<Long> unitIds = new HashSet<>();
		for (PlanItem item : planItemRepository.findByPlanIds(planIds)) {
			addTo(itemsByPlan, item.getPlan().getId(), item);
			unitIds.add(item.getWatchUnitId());
		}
		Map<Long, List<PlanAssignment>> assignmentsByItem = new HashMap<>();
		for (PlanAssignment assignment : planAssignmentRepository.findByPlanIds(planIds)) {
			addTo(assignmentsByItem, assignment.getPlanItem().getId(), assignment);
		}

		// 2. 작품·상품 정보를 일괄 조회한다(다른 도메인은 서비스로만 부른다)
		WatchUnitSummaries summaries = watchUnitQueryService.loadSummaries(unitIds);
		Map<Long, ProductSummary> productSummaries = productSummaryQueryService.loadSummaries(productIds);

		// 3. 플랜마다 스냅샷을 만든다
		for (Plan plan : plans) {
			snapshots.put(plan.getId(), toSnapshot(plan, monthsByPlan.get(plan.getId()), productsByMonth,
					itemsByPlan.get(plan.getId()), assignmentsByItem, summaries.byUnitId(), productSummaries));
		}
		return snapshots;
	}

	private PlanSnapshot toSnapshot(Plan plan, List<PlanMonth> planMonths, Map<Long, List<PlanMonthProduct>> productsByMonth,
			List<PlanItem> items, Map<Long, List<PlanAssignment>> assignmentsByItem,
			Map<Long, WatchUnitSummary> summaries, Map<Long, ProductSummary> productSummaries) {
		if (planMonths == null || items == null) {
			throw new IllegalStateException("플랜의 달 또는 항목이 없다: planId=" + plan.getId());
		}

		// 1. 달과 달별 상품. 번들은 구성 서비스 전부를 ottServiceIds에 넣는다(번들은 Task 076에서 생긴다)
		Map<Integer, LocalDate> monthByIndex = new HashMap<>();
		List<PlanSnapshot.Month> months = new ArrayList<>();
		for (PlanMonth planMonth : planMonths) {
			monthByIndex.put(planMonth.getMonthIndex(), planMonth.getMonth());
			List<PlanSnapshot.Product> products = new ArrayList<>();
			List<PlanMonthProduct> stored = productsByMonth.get(planMonth.getId());
			if (stored != null) {
				for (PlanMonthProduct row : stored) {
					ProductSummary summary = productSummaries.get(row.getProductId());
					if (summary == null) {
						throw new IllegalStateException("플랜 상품을 찾지 못했다: productId=" + row.getProductId());
					}
					products.add(new PlanSnapshot.Product(row.getProductId(), summary.ottServiceIds(), summary.name(),
							row.getAppliedPrice()));
				}
			}
			months.add(new PlanSnapshot.Month(planMonth.getMonth(), planMonth.isConfirmed(), planMonth.getCost(), products));
		}

		// 2. 시청 단위마다 자리를 만든다: 시청을 끝내는 달과 그 달에 보는 서비스. 배정하지 못한 단위는 자리가 없다
		List<PlanSnapshot.Unit> units = new ArrayList<>();
		for (PlanItem item : items) {
			WatchUnitSummary summary = summaries.get(item.getWatchUnitId());
			if (summary == null) {
				throw new IllegalStateException("시청 단위 요약이 없다: watchUnitId=" + item.getWatchUnitId());
			}
			PlanSnapshot.Placement placement = null;
			if (item.getOutcome() == PlanItemOutcome.SCHEDULED) {
				placement = placementOf(item, monthByIndex, assignmentsByItem.get(item.getId()));
			}
			units.add(new PlanSnapshot.Unit(item.getWatchUnitId(), summary.title(), summary.seasonNumber(), placement));
		}
		return new PlanSnapshot(plan.getId(), plan.getDataAsOf(), plan.getActivatedAt(), months, units);
	}

	/** 시청 완료 달의 서비스를 정한다. 그 달의 배정이 없으면(이론상) 마지막 배정의 서비스를 쓴다 */
	private static PlanSnapshot.Placement placementOf(PlanItem item, Map<Integer, LocalDate> monthByIndex,
			List<PlanAssignment> assignments) {
		LocalDate month = monthByIndex.get(item.getCompletedMonthIndex());
		if (month == null || assignments == null || assignments.isEmpty()) {
			throw new IllegalStateException("배정된 항목의 달 또는 배정이 없다: planItemId=" + item.getId());
		}
		long serviceId = assignments.get(assignments.size() - 1).getOttServiceId();
		for (PlanAssignment assignment : assignments) {
			if (assignment.getPlanMonth().getMonthIndex() == item.getCompletedMonthIndex()) {
				serviceId = assignment.getOttServiceId();
			}
		}
		return new PlanSnapshot.Placement(month, serviceId);
	}

	private static <T> void addTo(Map<Long, List<T>> map, Long key, T value) {
		List<T> list = map.get(key);
		if (list == null) {
			list = new ArrayList<>();
			map.put(key, list);
		}
		list.add(value);
	}
}
