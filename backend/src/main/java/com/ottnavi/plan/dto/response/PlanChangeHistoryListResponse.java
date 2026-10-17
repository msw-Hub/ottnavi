package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.service.model.PlanChangeHistoryEntryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 변경 내역 목록(계약 PlanChangeHistoryList). 최신순 최대 6건이고 없으면 빈 배열이다.
 *
 * @param items 변경 내역
 */
public record PlanChangeHistoryListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlanChangeHistoryEntryResponse> items) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanChangeHistoryListResponse from(List<PlanChangeHistoryEntryView> views) {
		List<PlanChangeHistoryEntryResponse> items = new ArrayList<>();
		for (PlanChangeHistoryEntryView view : views) {
			items.add(PlanChangeHistoryEntryResponse.from(view));
		}
		return new PlanChangeHistoryListResponse(items);
	}
}
