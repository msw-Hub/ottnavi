package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.service.model.ActivePlanView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

/**
 * 현재 플랜 조회 응답(계약 CurrentPlan). 저장한 플랜이 없으면 plan이 null이다(404가 아니다).
 *
 * @param plan 현재 플랜. 없으면 null
 */
public record CurrentPlanResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ActivePlanResponse plan) {

	/** 서비스 계층 값을 응답으로 바꾼다. 값이 없으면 plan이 null이다 */
	public static CurrentPlanResponse from(Optional<ActivePlanView> view) {
		if (view.isEmpty()) {
			return new CurrentPlanResponse(null);
		}
		return new CurrentPlanResponse(ActivePlanResponse.from(view.get()));
	}
}
