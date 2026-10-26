package com.ottnavi.plan.domain.rule;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 변경 이유 판정에 쓰는 근거 값 묶음. 서비스가 DB에서 읽어 채우고 PlanChangeDetector는 값만 본다.
 *
 * @param availabilityShifts 시청 단위 × 서비스별 제공 상태 변경(availability_change). 이전 플랜 이후 것만 골라 넘기지 않아도 되며
 *                           detector가 changedAt > 이전 플랜 dataAsOf로 다시 거른다
 * @param watchedUnitIds     이전 플랜 저장 뒤(watched_at > 이전 activatedAt) "다 봤음"으로 체크된 시청 단위 ID
 * @param serviceNames       서비스 ID → 화면에 쓰는 서비스 이름(문장에 쓴다)
 */
public record PlanChangeEvidence(
		List<AvailabilityShift> availabilityShifts,
		Set<Long> watchedUnitIds,
		Map<Long, String> serviceNames) {

	/**
	 * 제공 상태 변경 한 줄. 상태 이름이 아니라 "있음인지"만 담는다.
	 *
	 * @param beforeAvailable 변경 전 있음 여부(신규 등록이라 변경 전이 없으면 false)
	 * @param afterAvailable  변경 후 있음 여부
	 */
	public record AvailabilityShift(long watchUnitId, long ottServiceId, boolean beforeAvailable,
			boolean afterAvailable, Instant changedAt) {
	}
}
