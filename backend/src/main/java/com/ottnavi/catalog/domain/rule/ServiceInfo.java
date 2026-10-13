package com.ottnavi.catalog.domain.rule;

import com.ottnavi.provider.domain.enums.OttServiceCode;

/** 판정 대상 서비스 한 곳(ott_service에서 필요한 값만 옮긴 값). 쿠팡플레이 영화 규칙을 코드로 구분한다 */
public record ServiceInfo(
        long ottServiceId, // ott_service.id
        OttServiceCode code) { // NETFLIX, COUPANG_PLAY 등
}
