package com.ottnavi.plan.service.model;

import com.ottnavi.engine.model.PlanType;
import java.time.Instant;

/**
 * 저장(활성화)한 플랜의 결과 값. 서비스 안에서만 쓰고 응답 DTO로 바꿔 내보낸다.
 *
 * @param planId      저장된 플랜 ID
 * @param planType    플랜 유형
 * @param activatedAt 저장 시각
 */
public record SavedPlanView(long planId, PlanType planType, Instant activatedAt) {
}
