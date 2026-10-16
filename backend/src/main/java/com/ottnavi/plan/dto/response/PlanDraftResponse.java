package com.ottnavi.plan.dto.response;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.plan.service.model.PlanDraftView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 플랜 초안 하나(계약 PlanDraft, plan.status = DRAFT).
 *
 * @param planId              플랜 ID(저장 요청에 쓴다)
 * @param planType            플랜 유형
 * @param startMonth          첫 달(YYYY-MM)
 * @param algorithm           계산 방식(EXACT·GREEDY)
 * @param monthlyBudget       계산에 쓴 월 예산(원)
 * @param monthlyWatchMinutes 계산에 쓴 월 시청 시간(분)
 * @param mustTotal           꼭 볼 작품 수
 * @param mustCompleted       그중 3개월 안에 다 볼 수 있는 수
 * @param scheduledCount      배정된 작품(시즌) 수
 * @param totalCount          계산 대상 찜 수
 * @param score               점수
 * @param totalCost           3개월 총비용(원)
 * @param allSubscribeCost    전체 구독 비용(원)
 * @param savedAmount         절감액 = allSubscribeCost - totalCost
 * @param months              3개월(monthIndex 0, 1, 2 순)
 * @param unscheduled         남은 작품과 이유. 없으면 빈 배열
 * @param dataAsOf            계산에 쓴 데이터 기준일
 */
public record PlanDraftResponse(
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
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-15") LocalDate dataAsOf) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanDraftResponse from(PlanDraftView view) {
		List<PlanMonthResponse> months = new ArrayList<>();
		for (PlanDraftView.Month month : view.months()) {
			months.add(PlanMonthResponse.from(month));
		}
		List<UnscheduledItemResponse> unscheduled = new ArrayList<>();
		for (PlanDraftView.Unscheduled item : view.unscheduled()) {
			unscheduled.add(UnscheduledItemResponse.from(item));
		}
		return new PlanDraftResponse(view.planId(), view.planType(), YearMonth.from(view.startMonth()).toString(),
				view.algorithm(), view.monthlyBudget(), view.monthlyWatchMinutes(), view.mustTotal(),
				view.mustCompleted(), view.scheduledCount(), view.totalCount(), view.score(), view.totalCost(),
				view.allSubscribeCost(), view.savedAmount(), months, unscheduled, view.dataAsOf());
	}
}
