package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import java.time.Instant;

/**
 * 시청 단위 × 서비스의 제공 상태 변경 한 건. 플랜 재계산이 "작품이 서비스에서 빠짐·들어옴" 이유를 판정하는 근거다.
 *
 * @param watchUnitId  시청 단위 ID
 * @param ottServiceId 상태가 바뀐 서비스 ID
 * @param beforeStatus 이전 상태. null이면 이번에 처음 확인됨(신규)
 * @param afterStatus  바뀐 뒤 상태
 * @param changedAt    변경 시각
 */
public record UnitAvailabilityShift(
		long watchUnitId,
		long ottServiceId,
		AvailabilityStatus beforeStatus,
		AvailabilityStatus afterStatus,
		Instant changedAt) {
}
