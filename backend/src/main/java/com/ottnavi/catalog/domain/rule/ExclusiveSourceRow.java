package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import java.time.Instant;

/**
 * 독점 판정의 입력 한 줄: 시청 단위(영화 1편 또는 시즌 1개) × 서비스 1곳의 제공 상태.
 * availability 테이블 한 행을 판정에 필요한 값만 남긴 것이다(엔티티를 통째로 읽지 않으려고 쿼리가 바로 이 모양으로 읽는다).
 */
public record ExclusiveSourceRow(
        long watchUnitId, // 시청 단위 ID
        long titleId, // 소속 작품 ID
        UnitType unitType, // 영화 / 시즌
        Integer seasonNumber, // 영화는 null
        long ottServiceId, // 서비스 ID
        AvailabilityStatus status, // 있음 / 없음 / 모름
        UnknownReason unknownReason, // 모름일 때만 값이 있다
        Instant checkedAt) { // 마지막 확인 시각
}
