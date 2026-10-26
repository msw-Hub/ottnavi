package com.ottnavi.plan.service;

import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.plan.domain.entity.CalcRun;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.entity.PlanAssignment;
import com.ottnavi.plan.domain.entity.PlanItem;
import com.ottnavi.plan.domain.entity.PlanMonth;
import com.ottnavi.plan.domain.entity.PlanMonthProduct;
import com.ottnavi.plan.domain.repository.CalcRunRepository;
import com.ottnavi.plan.domain.repository.PlanAssignmentRepository;
import com.ottnavi.plan.domain.repository.PlanItemRepository;
import com.ottnavi.plan.domain.repository.PlanMonthProductRepository;
import com.ottnavi.plan.domain.repository.PlanMonthRepository;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.PlanDraftMapper.MonthDraft;
import com.ottnavi.plan.service.PlanDraftMapper.MonthProduct;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.ProductPriceInfo;
import com.ottnavi.plan.service.model.SavedDraft;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계산 결과 한 건을 플랜 행과 하위 행(달·항목·달별 상품·배정·calc_run)으로 저장한다.
 * DRAFT 저장(PlanDraftWriter)과 매월 재계산(Task 069)이 같은 저장 로직을 쓰도록 PlanDraftWriter에서 떼어 냈다.
 * 호출하는 쪽의 트랜잭션 안에서 실행된다(잠금·기존 행 정리·상태 전환은 호출하는 쪽이 맡는다).
 */
@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class PlanResultWriter {

	private final PlanRepository planRepository;
	private final PlanMonthRepository planMonthRepository;
	private final PlanItemRepository planItemRepository;
	private final PlanMonthProductRepository planMonthProductRepository;
	private final PlanAssignmentRepository planAssignmentRepository;
	private final CalcRunRepository calcRunRepository;

	/**
	 * 만들어 둔 플랜(아직 저장 전)과 계산 결과를 plan → 달 → 항목 → 달별 상품 → 배정 → calc_run 순서로 저장한다.
	 *
	 * @param plan      Plan.createDraft 또는 Plan.createRecalculated로 만든 저장 전 플랜
	 * @param inputHash calc_run에 남길 입력 해시
	 */
	public SavedDraft save(Plan plan, String inputHash, PlanInputBundle bundle, PlanCalcResult result) {
		// 1. 달별 상품·비용을 만들고 세 달 비용의 합이 총비용과 같은지 확인한다(저장 전에 막는다)
		List<MonthDraft> monthDrafts = PlanDraftMapper.months(bundle, result);
		int costSum = 0;
		for (MonthDraft monthDraft : monthDrafts) {
			costSum += monthDraft.cost();
		}
		if (costSum != result.totalCost()) {
			log.error("달 비용 합이 총비용과 다르다: userId={}, planType={}, 달 비용 합={}, 총비용={}",
					plan.getUserId(), result.planType(), costSum, result.totalCost());
			throw new IllegalStateException("달 비용 합이 총비용과 다르다: planType=" + result.planType());
		}

		// 2. 플랜을 저장한다. 데이터 기준 시각은 조립 시점의 값이다
		planRepository.save(plan);

		// 3. 달을 저장한다. 배정이 이 플랜의 달만 가리키도록 monthIndex로 찾을 수 있게 모아 둔다
		Map<Integer, PlanMonth> monthsByIndex = new HashMap<>();
		for (MonthDraft monthDraft : monthDrafts) {
			PlanMonth planMonth = planMonthRepository
					.save(PlanMonth.create(plan, monthDraft.monthIndex(), monthDraft.month(), monthDraft.cost()));
			monthsByIndex.put(monthDraft.monthIndex(), planMonth);
		}

		// 4. 시청 단위마다 항목을 저장한다(배정 여부와 이유는 엔진 결과 그대로)
		Map<Long, PlanCalcResult.Unit> resultUnits = PlanDraftMapper.unitsById(result);
		Map<Long, PlanItem> itemsByUnit = new HashMap<>();
		for (WatchUnit input : bundle.planInput().units()) {
			PlanCalcResult.Unit unit = resultUnits.get(input.id());
			if (unit == null) {
				throw new IllegalStateException("입력 단위의 계산 결과가 없다: unitId=" + input.id());
			}
			PlanItem item;
			if (unit.scheduled()) {
				item = PlanItem.scheduled(plan, input.id(), input.priority(), unit.completedMonthIndex());
			} else {
				item = PlanItem.unscheduled(plan, input.id(), input.priority(), unit.reason());
			}
			itemsByUnit.put(input.id(), planItemRepository.save(item));
		}

		// 5. 달별 상품을 저장한다
		for (MonthDraft monthDraft : monthDrafts) {
			PlanMonth planMonth = monthsByIndex.get(monthDraft.monthIndex());
			for (MonthProduct product : monthDraft.products()) {
				planMonthProductRepository.save(PlanMonthProduct.create(planMonth, product.productId(),
						product.appliedPrice(), product.priceSource()));
			}
		}

		// 6. 배정을 저장한다. 같은 유형(같은 Plan)에서 만든 항목·달만 짝지어 소속이 어긋나지 않게 한다
		for (WatchUnit input : bundle.planInput().units()) {
			PlanCalcResult.Unit unit = resultUnits.get(input.id());
			PlanItem item = itemsByUnit.get(input.id());
			for (PlanCalcResult.Slot slot : unit.assignments()) {
				PlanMonth planMonth = monthsByIndex.get(slot.monthIndex());
				ProductPriceInfo info = bundle.productPrices().get(slot.productId());
				if (planMonth == null || info == null) {
					throw new IllegalStateException("배정의 달 또는 상품을 찾지 못했다: unitId=" + input.id()
							+ ", monthIndex=" + slot.monthIndex() + ", productId=" + slot.productId());
				}
				// 번들이면 작품이 제공되는 구성 서비스 중 ID가 가장 작은 것을 배정 서비스로 저장한다(표시용, PRD 11절 33번)
				WatchUnitSummary summary = bundle.unitSummaries().get(input.id());
				long assignedServiceId = PlanDraftMapper.assignedServiceId(info, PlanDraftMapper.availableServiceIds(summary));
				planAssignmentRepository.save(PlanAssignment.create(item, planMonth, assignedServiceId, slot.minutes()));
			}
		}

		// 7. 계산 실행 기록을 남긴다. 캐시에서 온 결과여도 원래 계산의 후보 수·소요 시간을 그대로 쓴다
		calcRunRepository.save(CalcRun.create(plan.getId(), inputHash, result.planType(), result.solverType(),
				result.mustCompleted(), result.score(), result.totalCost(), result.candidateCount(), result.elapsedMs()));

		// 8. 응답 조립에 쓸 ID 묶음을 돌려준다
		Map<Long, Long> planItemIdByUnit = new HashMap<>();
		for (Map.Entry<Long, PlanItem> entry : itemsByUnit.entrySet()) {
			planItemIdByUnit.put(entry.getKey(), entry.getValue().getId());
		}
		return new SavedDraft(result.planType(), plan.getId(), planItemIdByUnit);
	}
}
