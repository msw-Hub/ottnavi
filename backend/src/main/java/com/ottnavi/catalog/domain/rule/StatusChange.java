package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;

/** 제공 상태가 바뀐 기록 한 건(availability_change 한 행의 before/after) */
public record StatusChange(
        AvailabilityStatus before, // 신규(기존 상태 없음)면 null
        AvailabilityStatus after) {
}
