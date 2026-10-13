package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnknownReason;

/** 서비스 한 곳의 판정 결과. availability 한 행에 저장할 값과 같은 모양이다 */
public record AvailabilityVerdict(
        long ottServiceId,
        AvailabilityStatus status, // AVAILABLE / NOT_AVAILABLE / UNKNOWN
        UnknownReason unknownReason, // UNKNOWN일 때만 값이 있다
        boolean inStandard, // STANDARD tier 제공처로 구독 제공 중
        boolean inAdTier) { // AD tier 제공처로 구독 제공 중
}
