package com.ottnavi.plan.dto.response;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.plan.service.model.ActivePlanView;
import com.ottnavi.plan.service.model.PlanDraftView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 현재 플랜(계약 ActivePlan, status = ACTIVE). 초안(PlanDraft)의 필드를 모두 담고 저장 시각이 더해진다.
 * 필드 설명은 {@link PlanDraftResponse}와 같다.
 *
 * @param activatedAt 저장(활성화) 시각(KST 오프셋)
 */
public record ActivePlanResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "301") long planId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "RECOMMENDED") PlanType planType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-10") String startMonth,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "EXACT") SolverType algorithm,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "30000") int monthlyBudget,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "900") int monthlyWatchMinutes,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") int mustTotal,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") int mustCompleted,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int scheduledCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4") int totalCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int score,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "27000") int totalCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "40500") int allSubscribeCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13500") int savedAmount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlanMonthResponse> months,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UnscheduledItemResponse> unscheduled,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-15") LocalDate dataAsOf,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-12T21:05:00+09:00") OffsetDateTime activatedAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다. 저장 시각은 KST 오프셋으로 내보낸다 */
	public static ActivePlanResponse from(ActivePlanView activeView) {
		PlanDraftView view = activeView.plan();
		List<PlanMonthResponse> months = new ArrayList<>();
		for (PlanDraftView.Month month : view.months()) {
			months.add(PlanMonthResponse.from(month));
		}
		List<UnscheduledItemResponse> unscheduled = new ArrayList<>();
		for (PlanDraftView.Unscheduled item : view.unscheduled()) {
			unscheduled.add(UnscheduledItemResponse.from(item));
		}
		return new ActivePlanResponse(view.planId(), view.planType(), YearMonth.from(view.startMonth()).toString(),
				view.algorithm(), view.monthlyBudget(), view.monthlyWatchMinutes(), view.mustTotal(),
				view.mustCompleted(), view.scheduledCount(), view.totalCount(), view.score(), view.totalCost(),
				view.allSubscribeCost(), view.savedAmount(), months, unscheduled, view.dataAsOf(),
				activeView.activatedAt().atZone(KST).toOffsetDateTime());
	}
}
