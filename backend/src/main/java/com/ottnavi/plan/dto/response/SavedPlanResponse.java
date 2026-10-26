package com.ottnavi.plan.dto.response;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.service.model.SavedPlanView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 저장된 플랜(계약 SavedPlan, status = ACTIVE).
 *
 * @param planId      플랜 ID
 * @param planType    플랜 유형
 * @param status      이 응답에서는 항상 ACTIVE
 * @param activatedAt 저장(활성화) 시각(KST 오프셋)
 */
public record SavedPlanResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "301") long planId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "RECOMMENDED") PlanType planType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ACTIVE") PlanStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-12T21:05:00+09:00") OffsetDateTime activatedAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다. 시각은 KST 오프셋으로 내보낸다 */
	public static SavedPlanResponse from(SavedPlanView view) {
		return new SavedPlanResponse(view.planId(), view.planType(), PlanStatus.ACTIVE,
				view.activatedAt().atZone(KST).toOffsetDateTime());
	}
}
