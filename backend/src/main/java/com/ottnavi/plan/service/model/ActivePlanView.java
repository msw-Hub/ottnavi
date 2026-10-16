package com.ottnavi.plan.service.model;

import java.time.Instant;

/**
 * 현재 플랜(ACTIVE) 한 건의 값. 초안과 같은 내용에 저장 시각이 더해진다. 서비스 안에서만 쓰고 응답 DTO로 바꿔 내보낸다.
 *
 * @param plan        플랜 내용(초안과 같은 구조)
 * @param activatedAt 저장(활성화) 시각
 */
public record ActivePlanView(PlanDraftView plan, Instant activatedAt) {
}
