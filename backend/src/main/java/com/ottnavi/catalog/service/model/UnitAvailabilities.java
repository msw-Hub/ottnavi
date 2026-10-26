package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 시청 단위 여러 개의 제공 판정을 읽기 좋게 묶은 값(조회 API 내부용) */
public record UnitAvailabilities(
        Map<Long, Map<Long, AvailabilityStatus>> statusByUnit, // 시청 단위 ID → (서비스 ID → 상태)
        Map<Long, List<Instant>> checkedAtByUnit) { // 시청 단위 ID → 판정 확인 시각들

    /** 시청 단위의 서비스별 판정 행. 판정이 없으면 빈 맵이다 */
    public Map<Long, AvailabilityStatus> rowsOf(long unitId) {
        Map<Long, AvailabilityStatus> rows = statusByUnit.get(unitId);
        if (rows == null) {
            return Map.of();
        }
        return rows;
    }

    /** 시청 단위의 판정 확인 시각들. 판정이 없으면 빈 목록이다 */
    public List<Instant> checkedAtOf(long unitId) {
        List<Instant> checkedAts = checkedAtByUnit.get(unitId);
        if (checkedAts == null) {
            return List.of();
        }
        return checkedAts;
    }
}
