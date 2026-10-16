package com.ottnavi.plan.service;

import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.plan.service.PlanDraftMapper.MonthDraft;
import com.ottnavi.plan.service.PlanDraftMapper.MonthProduct;
import com.ottnavi.plan.service.PlanDraftMapper.Segment;
import com.ottnavi.plan.service.model.PlanCalcResult;
import com.ottnavi.plan.service.model.PlanCalcResults;
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.ProductPriceInfo;
import com.ottnavi.plan.service.model.SavedDraft;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 계산 결과와 저장 직후의 ID로 응답에 쓸 값(PlanDraftView)을 만든다. DB를 다시 읽지 않고 입력 번들과 계산 결과만 쓴다.
 * 달별 상품·비용, 조각 번호 규칙은 저장과 같은 PlanDraftMapper를 써서 응답과 DB 값이 어긋나지 않게 한다.
 */
public final class PlanDraftViewAssembler {

	private PlanDraftViewAssembler() {
	}

	/** 유형 순서(결과 순서)대로 DRAFT 응답 값을 만든다. dataAsOf는 zone의 날짜로 바꾼다. */
	public static List<PlanDraftView> assemble(PlanInputBundle bundle, PlanCalcResults results, List<SavedDraft> saved,
			ZoneId zone) {
		// 1. 유형별 저장 ID를 찾을 수 있게 한다
		Map<PlanType, SavedDraft> savedByType = new HashMap<>();
		for (SavedDraft draft : saved) {
			savedByType.put(draft.planType(), draft);
		}

		// 2. 유형마다 응답 값을 만든다
		LocalDate dataAsOf = bundle.dataAsOf().atZone(zone).toLocalDate();
		List<PlanDraftView> views = new ArrayList<>();
		for (PlanCalcResult result : results.results()) {
			SavedDraft draft = savedByType.get(result.planType());
			if (draft == null) {
				throw new IllegalStateException("저장된 DRAFT가 없는 유형이다: planType=" + result.planType());
			}
			views.add(assembleOne(bundle, result, draft, dataAsOf));
		}
		return views;
	}

	private static PlanDraftView assembleOne(PlanInputBundle bundle, PlanCalcResult result, SavedDraft draft,
			LocalDate dataAsOf) {
		Map<Long, PlanCalcResult.Unit> resultUnits = PlanDraftMapper.unitsById(result);

		// 1. 달마다 상품과 그 달에 볼 조각들을 만든다. 조각은 입력 단위(배정) 순서다
		List<PlanDraftView.Month> months = new ArrayList<>();
		for (MonthDraft monthDraft : PlanDraftMapper.months(bundle, result)) {
			List<PlanDraftView.Product> products = new ArrayList<>();
			for (MonthProduct product : monthDraft.products()) {
				ProductPriceInfo info = bundle.productPrices().get(product.productId());
				products.add(new PlanDraftView.Product(product.productId(), info.productName(),
						List.of(info.ottServiceId()), product.appliedPrice(), product.priceSource()));
			}
			List<PlanDraftView.Assignment> assignments = new ArrayList<>();
			for (WatchUnit input : bundle.planInput().units()) {
				PlanCalcResult.Unit unit = resultUnits.get(input.id());
				for (PlanCalcResult.Slot slot : unit.assignments()) {
					if (slot.monthIndex() != monthDraft.monthIndex()) {
						continue;
					}
					assignments.add(toAssignment(bundle, draft, input, unit, slot));
				}
			}
			months.add(new PlanDraftView.Month(monthDraft.monthIndex(), monthDraft.month(), monthDraft.monthIndex() == 0,
					monthDraft.cost(), products, assignments));
		}

		// 2. 배정하지 못한 작품을 꼭 → 보고 싶음 → 여유될 때 순으로, 같은 우선순위 안에서는 엔진 결과 순서로 모은다
		Map<Long, WatchUnit> inputsById = new HashMap<>();
		for (WatchUnit input : bundle.planInput().units()) {
			inputsById.put(input.id(), input);
		}
		List<PlanDraftView.Unscheduled> unscheduled = new ArrayList<>();
		int scheduledCount = 0;
		for (Priority priority : Priority.values()) { // 선언 순서가 MUST → WANT → MAYBE다
			for (PlanCalcResult.Unit unit : result.units()) {
				WatchUnit input = inputsById.get(unit.unitId());
				if (input == null) {
					throw new IllegalStateException("결과 단위가 입력에 없다: unitId=" + unit.unitId());
				}
				if (input.priority() != priority) {
					continue;
				}
				if (unit.scheduled()) {
					scheduledCount++;
					continue;
				}
				WatchUnitSummary summary = summaryOf(bundle, unit.unitId());
				unscheduled.add(new PlanDraftView.Unscheduled(draft.planItemIdByUnit().get(unit.unitId()),
						summary.mediaType(), summary.tmdbId(), summary.title(), summary.seasonNumber(), priority,
						unit.reason()));
			}
		}

		// 3. 플랜 한 건의 값으로 묶는다
		return new PlanDraftView(draft.planId(), result.planType(), bundle.startMonth(), result.solverType(),
				bundle.monthlyBudget(), bundle.monthlyWatchMinutes(), PlanDraftMapper.mustTotal(bundle.planInput()),
				result.mustCompleted(), scheduledCount, bundle.planInput().units().size(), result.score(),
				result.totalCost(), bundle.allSubscribeCost(), bundle.allSubscribeCost() - result.totalCost(), months,
				unscheduled, dataAsOf);
	}

	/** 배정 한 조각을 만든다. 서비스·작품 정보는 번들에서 찾는다. */
	private static PlanDraftView.Assignment toAssignment(PlanInputBundle bundle, SavedDraft draft, WatchUnit input,
			PlanCalcResult.Unit unit, PlanCalcResult.Slot slot) {
		WatchUnitSummary summary = summaryOf(bundle, input.id());
		ProductPriceInfo info = bundle.productPrices().get(slot.productId());
		if (info == null) {
			throw new IllegalStateException("배정 상품의 가격 정보가 없다: productId=" + slot.productId());
		}
		Segment segment = PlanDraftMapper.segmentOf(unit, slot.monthIndex());
		return new PlanDraftView.Assignment(draft.planItemIdByUnit().get(input.id()), summary.mediaType(),
				summary.tmdbId(), summary.title(), summary.seasonNumber(), input.priority(), info.ottServiceId(),
				slot.minutes(), segment.index(), segment.count(), segment.isContinuation());
	}

	private static WatchUnitSummary summaryOf(PlanInputBundle bundle, long unitId) {
		WatchUnitSummary summary = bundle.unitSummaries().get(unitId);
		if (summary == null) {
			throw new IllegalStateException("시청 단위 요약이 없다: unitId=" + unitId);
		}
		return summary;
	}
}
