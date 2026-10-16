package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.service.model.PlanDraftView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 플랜의 한 달(계약 PlanMonth).
 *
 * @param monthIndex  0 이번 달, 1·2 다음 두 달
 * @param month       해당 달(YYYY-MM)
 * @param isConfirmed 이번 달만 true
 * @param cost        그 달 비용(원). 구독 상품이 없으면 0
 * @param products    그 달 상품들. 빈 배열이면 그 달은 구독하지 않는다
 * @param assignments 그 달에 볼 작품들
 */
public record PlanMonthResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "2", example = "0") int monthIndex,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-10") String month,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isConfirmed,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13500") int cost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlanMonthProductResponse> products,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlanAssignmentResponse> assignments) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanMonthResponse from(PlanDraftView.Month month) {
		List<PlanMonthProductResponse> products = new ArrayList<>();
		for (PlanDraftView.Product product : month.products()) {
			products.add(PlanMonthProductResponse.from(product));
		}
		List<PlanAssignmentResponse> assignments = new ArrayList<>();
		for (PlanDraftView.Assignment assignment : month.assignments()) {
			assignments.add(PlanAssignmentResponse.from(assignment));
		}
		return new PlanMonthResponse(month.monthIndex(), YearMonth.from(month.month()).toString(), month.isConfirmed(),
				month.cost(), products, assignments);
	}
}
