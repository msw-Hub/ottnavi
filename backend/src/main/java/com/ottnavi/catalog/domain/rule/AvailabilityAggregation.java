package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 시청 단위별 제공 상태를 작품 단위·화면 단위로 합치는 규칙 모음(Task 050·051). 순수 정적 함수.
 * 드라마는 시즌이 시청 단위라서, 작품 전체 상태는 시즌 상태를 합쳐 만든다.
 */
public final class AvailabilityAggregation {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private AvailabilityAggregation() {
    }

    /**
     * 시청 단위 상태 여러 개를 작품 하나의 상태로 합친다.
     * 하나라도 AVAILABLE이면 AVAILABLE, 아니면 UNKNOWN이 하나라도 있으면 UNKNOWN, 아니면 NOT_AVAILABLE.
     * 목록이 비면(시청 단위가 아직 수집되지 않음) UNKNOWN이다.
     */
    public static AvailabilityStatus merge(List<AvailabilityStatus> statuses) {
        // 1. 시청 단위가 없으면 판정할 근거가 없으므로 모름
        if (statuses == null || statuses.isEmpty()) {
            return AvailabilityStatus.UNKNOWN;
        }

        // 2. 있음이 하나라도 있으면 있음, 아니면 모름이 하나라도 있는지 본다
        boolean hasUnknown = false;
        for (AvailabilityStatus status : statuses) {
            if (status == AvailabilityStatus.AVAILABLE) {
                return AvailabilityStatus.AVAILABLE;
            }
            if (status == AvailabilityStatus.UNKNOWN) {
                hasUnknown = true;
            }
        }

        // 3. 모름이 있으면 모름, 전부 없음이면 없음
        if (hasUnknown) {
            return AvailabilityStatus.UNKNOWN;
        }
        return AvailabilityStatus.NOT_AVAILABLE;
    }

    /**
     * 시청 단위 하나의 서비스별 상태를 서비스 ID 순으로 만든다. 판정 행이 없는 서비스는 UNKNOWN이다.
     *
     * @param serviceIds 7개 서비스 ID
     * @param unitRows   이 시청 단위의 판정 행(서비스 ID → 상태). 비어 있을 수 있다
     */
    public static Map<Long, AvailabilityStatus> statusByService(List<Long> serviceIds,
                                                                 Map<Long, AvailabilityStatus> unitRows) {
        Map<Long, AvailabilityStatus> result = new TreeMap<>();
        for (Long serviceId : serviceIds) {
            AvailabilityStatus status = unitRows.get(serviceId);
            if (status == null) {
                status = AvailabilityStatus.UNKNOWN;
            }
            result.put(serviceId, status);
        }
        return result;
    }

    /**
     * 시청 단위 여러 개를 작품 단위의 서비스별 상태로 합친다(서비스 ID 순).
     * 예: 시즌1 AVAILABLE·시즌2 NOT_AVAILABLE → AVAILABLE, 시즌1 NOT_AVAILABLE·시즌2 행 없음 → UNKNOWN.
     *
     * @param serviceIds 7개 서비스 ID
     * @param unitRows   시청 단위마다의 판정 행(서비스 ID → 상태). 시청 단위 수만큼 들어 있다
     */
    public static Map<Long, AvailabilityStatus> mergeByService(List<Long> serviceIds,
                                                                List<Map<Long, AvailabilityStatus>> unitRows) {
        Map<Long, AvailabilityStatus> result = new TreeMap<>();
        for (Long serviceId : serviceIds) {
            List<AvailabilityStatus> statuses = new ArrayList<>();
            for (Map<Long, AvailabilityStatus> rows : unitRows) {
                AvailabilityStatus status = rows.get(serviceId);
                if (status == null) {
                    status = AvailabilityStatus.UNKNOWN;
                }
                statuses.add(status);
            }
            result.put(serviceId, merge(statuses));
        }
        return result;
    }

    /**
     * 데이터 기준일을 정한다(KST 날짜). 대상 판정의 checkedAt 중 가장 오래된 것의 날짜다.
     * 대상 판정이 없으면 전체 판정의 최신 checkedAt, 그것도 없으면 오늘이다.
     *
     * @param checkedAts    대상 작품들 판정의 확인 시각
     * @param latestOverall 전체 판정의 최신 확인 시각. 없으면 null
     * @param today         오늘(KST)
     */
    public static LocalDate dataAsOf(List<Instant> checkedAts, Instant latestOverall, LocalDate today) {
        // 1. 대상 판정 중 가장 오래된 확인 시각을 찾는다
        Instant oldest = null;
        for (Instant checkedAt : checkedAts) {
            if (oldest == null || checkedAt.isBefore(oldest)) {
                oldest = checkedAt;
            }
        }
        if (oldest != null) {
            return oldest.atZone(KST).toLocalDate();
        }

        // 2. 대상이 없으면 전체의 최신 확인 시각, 그것도 없으면 오늘
        if (latestOverall != null) {
            return latestOverall.atZone(KST).toLocalDate();
        }
        return today;
    }
}
