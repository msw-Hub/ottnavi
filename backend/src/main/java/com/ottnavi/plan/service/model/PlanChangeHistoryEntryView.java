package com.ottnavi.plan.service.model;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.plan.domain.rule.PlanChangeSummary;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 변경 내역 한 건의 값(계약 PlanChangeHistoryEntry의 서비스 쪽 모양). 컨트롤러 DTO가 이 값을 옮겨 담으며 내부용 필드를 뺀다.
 *
 * @param changeId  변경 내역 ID(새 플랜의 ID)
 * @param changedAt 재계산해 플랜이 바뀐 시각(새 플랜의 저장 시각)
 * @param planType  플랜 유형
 * @param dataAsOf  이 재계산에 쓴 데이터 기준일(KST)
 * @param summary   저장해 둔 변경 요약
 */
public record PlanChangeHistoryEntryView(
		long changeId,
		Instant changedAt,
		PlanType planType,
		LocalDate dataAsOf,
		PlanChangeSummary summary) {
}
