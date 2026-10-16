package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.service.model.PlanDraftView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 플랜 초안 목록(계약 PlanDraftList). 계산하면 유형마다 1개씩 항상 3개다.
 *
 * @param drafts 초안들
 */
public record PlanDraftListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlanDraftResponse> drafts) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanDraftListResponse from(List<PlanDraftView> views) {
		List<PlanDraftResponse> drafts = new ArrayList<>();
		for (PlanDraftView view : views) {
			drafts.add(PlanDraftResponse.from(view));
		}
		return new PlanDraftListResponse(drafts);
	}
}
