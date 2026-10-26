package com.ottnavi.plan.service.model;

import com.ottnavi.engine.model.PlanType;
import java.util.Map;

/**
 * DRAFT 한 건을 저장한 뒤 응답 조립에 필요한 ID 묶음. 응답을 만들 때 DB를 다시 읽지 않으려고 저장 직후의 ID를 그대로 넘긴다.
 *
 * @param planType         플랜 유형
 * @param planId           저장된 plan.id
 * @param planItemIdByUnit 시청 단위 ID → 저장된 plan_item.id
 */
public record SavedDraft(PlanType planType, long planId, Map<Long, Long> planItemIdByUnit) {
}
