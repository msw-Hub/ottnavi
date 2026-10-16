package com.ottnavi.plan.service;

import com.ottnavi.catalog.service.WatchUnitQueryService;
import com.ottnavi.catalog.service.model.WatchUnitSummaries;
import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.engine.model.Priority;
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
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.product.service.ProductSummaryQueryService;
import com.ottnavi.product.service.model.ProductSummary;
import java.time.Clock;
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
 * 저장된 플랜(DB)을 응답에 쓸 값(PlanDraftView)으로 다시 만든다(Task 066).
 * 계산 직후 응답(PlanDraftViewAssembler)과 같은 값·같은 순서가 되도록 맞춘다: 달 안 상품은 상품 ID 순,
 * 달 안 배정은 항목 ID 순(계산 입력인 찜 순서), 남은 작품은 우선순위(MUST → WANT → MAYBE) 안에서 항목 ID 순이다.
 * 플랜 수와 무관하게 쿼리는 9번 안팎이다(달·상품·항목·배정 각 1번, 작품 요약, 상품 요약).
 * 읽기 트랜잭션 안에서 불러야 한다. 하위 행의 연관은 앞에서 읽은 엔티티가 영속성 컨텍스트에 있어 추가 쿼리가 없다.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanViewLoader {

	private final PlanMonthRepository planMonthRepository;
	private final PlanMonthProductRepository planMonthProductRepository;
	private final PlanItemRepository planItemRepository;
	private final PlanAssignmentRepository planAssignmentRepository;
	private final WatchUnitQueryService watchUnitQueryService;
	private final ProductSummaryQueryService productSummaryQueryService;
	private final Clock clock;

	/** 플랜들을 주어진 순서 그대로 응답 값으로 바꾼다. 플랜이 없으면 쿼리 없이 빈 목록이다. */
	public List<PlanDraftView> load(List<Plan> plans) {
		// 1. 대상이 없으면 읽지 않는다
		List<PlanDraftView> views = new ArrayList<>();
		if (plans.isEmpty()) {
			return views;
		}
		List<Long> planIds = new ArrayList<>();
		for (Plan plan : plans) {
			planIds.add(plan.getId());
		}

		// 2. 하위 행을 일괄 조회해 상위 ID별로 묶는다
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

		// 3. 작품·상품 정보를 일괄 조회한다(다른 도메인은 서비스로만 부른다)
		WatchUnitSummaries summaries = watchUnitQueryService.loadSummaries(unitIds);
		Map<Long, ProductSummary> products = productSummaryQueryService.loadSummaries(productIds);

		// 4. 플랜마다 응답 값을 만든다
		for (Plan plan : plans) {
			views.add(toView(plan, monthsByPlan.get(plan.getId()), productsByMonth, itemsByPlan.get(plan.getId()),
					assignmentsByItem, summaries.byUnitId(), products));
		}
		return views;
	}

	private PlanDraftView toView(Plan plan, List<PlanMonth> planMonths, Map<Long, List<PlanMonthProduct>> productsByMonth,
			List<PlanItem> items, Map<Long, List<PlanAssignment>> assignmentsByItem,
			Map<Long, WatchUnitSummary> summaries, Map<Long, ProductSummary> products) {
		if (planMonths == null || items == null) {
			throw new IllegalStateException("플랜의 달 또는 항목이 없다: planId=" + plan.getId());
		}

		// 1. 달마다 상품과 그 달에 볼 조각을 만든다. 상품은 쿼리의 상품 ID 순, 조각은 항목 ID 순이다
		List<PlanDraftView.Month> months = new ArrayList<>();
		for (PlanMonth planMonth : planMonths) {
			List<PlanDraftView.Product> monthProducts = new ArrayList<>();
			List<PlanMonthProduct> storedProducts = productsByMonth.get(planMonth.getId());
			if (storedProducts != null) {
				for (PlanMonthProduct stored : storedProducts) {
					ProductSummary summary = products.get(stored.getProductId());
					if (summary == null) {
						throw new IllegalStateException("플랜 상품을 찾지 못했다: productId=" + stored.getProductId());
					}
					monthProducts.add(new PlanDraftView.Product(stored.getProductId(), summary.name(),
							summary.ottServiceIds(), stored.getAppliedPrice(), stored.getPriceSource()));
				}
			}
			List<PlanDraftView.Assignment> monthAssignments = new ArrayList<>();
			for (PlanItem item : items) {
				List<PlanAssignment> itemAssignments = assignmentsByItem.get(item.getId());
				if (itemAssignments == null) {
					continue;
				}
				// 조각 번호는 그 항목의 배정을 달 순서로 센 위치다(쿼리가 달 번호 순으로 정렬한다)
				for (int i = 0; i < itemAssignments.size(); i++) {
					PlanAssignment assignment = itemAssignments.get(i);
					if (assignment.getPlanMonth().getMonthIndex() != planMonth.getMonthIndex()) {
						continue;
					}
					WatchUnitSummary unit = summaryOf(summaries, item);
					monthAssignments.add(new PlanDraftView.Assignment(item.getId(), unit.mediaType(), unit.tmdbId(),
							unit.title(), unit.seasonNumber(), item.getPriority(), assignment.getOttServiceId(),
							assignment.getMinutes(), i + 1, itemAssignments.size(), i > 0));
				}
			}
			months.add(new PlanDraftView.Month(planMonth.getMonthIndex(), planMonth.getMonth(), planMonth.isConfirmed(),
					planMonth.getCost(), monthProducts, monthAssignments));
		}

		// 2. 배정하지 못한 항목을 우선순위 → 항목 ID 순으로 모으고, 배정된 항목 수를 센다
		int scheduledCount = 0;
		List<PlanDraftView.Unscheduled> unscheduled = new ArrayList<>();
		for (Priority priority : Priority.values()) { // 선언 순서가 MUST → WANT → MAYBE다
			for (PlanItem item : items) {
				if (item.getPriority() != priority) {
					continue;
				}
				if (item.getOutcome() == PlanItemOutcome.SCHEDULED) {
					scheduledCount++;
					continue;
				}
				WatchUnitSummary unit = summaryOf(summaries, item);
				unscheduled.add(new PlanDraftView.Unscheduled(item.getId(), unit.mediaType(), unit.tmdbId(),
						unit.title(), unit.seasonNumber(), item.getPriority(), item.getReason()));
			}
		}

		// 3. 플랜 한 건의 값으로 묶는다. 절감액은 저장하지 않고 계산한다(ERD)
		LocalDate dataAsOf = plan.getDataAsOf().atZone(clock.getZone()).toLocalDate();
		return new PlanDraftView(plan.getId(), plan.getPlanType(), plan.getStartMonth(), plan.getAlgorithm(),
				plan.getMonthlyBudget(), plan.getMonthlyWatchMinutes(), plan.getMustTotal(), plan.getMustCompleted(),
				scheduledCount, items.size(), plan.getScore(), plan.getTotalCost(), plan.getAllSubscribeCost(),
				plan.getAllSubscribeCost() - plan.getTotalCost(), months, unscheduled, dataAsOf);
	}

	private static WatchUnitSummary summaryOf(Map<Long, WatchUnitSummary> summaries, PlanItem item) {
		WatchUnitSummary summary = summaries.get(item.getWatchUnitId());
		if (summary == null) {
			throw new IllegalStateException("시청 단위 요약이 없다: watchUnitId=" + item.getWatchUnitId());
		}
		return summary;
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
