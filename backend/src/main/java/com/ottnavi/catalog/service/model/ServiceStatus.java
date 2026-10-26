package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;

/** 서비스 하나의 제공 상태(조회 API용). 서비스 이름은 서비스 목록 API에서 ottServiceId로 찾는다 */
public record ServiceStatus(
        long ottServiceId,
        AvailabilityStatus status) {
}
