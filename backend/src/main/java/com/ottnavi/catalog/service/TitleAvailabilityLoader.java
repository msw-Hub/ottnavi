package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.service.model.UnitAvailabilities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 조회 API가 쓰는 제공 상태(availability) 일괄 조회. 시청 단위(영화 1편 또는 드라마 시즌 1개) ID 목록으로 한 번에 읽어 시청 단위별로 묶는다 */
@Component
@RequiredArgsConstructor
public class TitleAvailabilityLoader {

    private final AvailabilityRepository availabilityRepository;

    /** 시청 단위들의 제공 상태를 한 번의 쿼리로 읽어 (시청 단위 → 서비스 → 상태)로 묶는다 */
    @Transactional(readOnly = true)
    public UnitAvailabilities load(Collection<Long> unitIds) {
        // 1. 시청 단위가 없으면 쿼리하지 않는다
        Map<Long, Map<Long, AvailabilityStatus>> statusByUnit = new HashMap<>();
        Map<Long, List<Instant>> checkedAtByUnit = new HashMap<>();
        if (unitIds.isEmpty()) {
            return new UnitAvailabilities(statusByUnit, checkedAtByUnit);
        }

        // 2. IN 조건으로 제공 상태 행(서비스별 있음·없음·모름)을 한 번에 조회
        List<Availability> rows = availabilityRepository.findWithServiceByWatchUnitIdIn(unitIds);

        // 3. 시청 단위별로 서비스 상태와 확인 시각을 모은다
        for (Availability row : rows) {
            Long unitId = row.getWatchUnit().getId();
            Map<Long, AvailabilityStatus> statuses = statusByUnit.get(unitId);
            if (statuses == null) {
                statuses = new HashMap<>();
                statusByUnit.put(unitId, statuses);
            }
            statuses.put(row.getOttService().getId(), row.getStatus());

            List<Instant> checkedAts = checkedAtByUnit.get(unitId);
            if (checkedAts == null) {
                checkedAts = new ArrayList<>();
                checkedAtByUnit.put(unitId, checkedAts);
            }
            checkedAts.add(row.getCheckedAt());
        }
        return new UnitAvailabilities(statusByUnit, checkedAtByUnit);
    }

    /** 전체 제공 상태 기록 중 가장 최근 확인 시각. 기록이 하나도 없으면 null */
    @Transactional(readOnly = true)
    public Instant latestCheckedAt() {
        return availabilityRepository.findLatestCheckedAt();
    }
}
