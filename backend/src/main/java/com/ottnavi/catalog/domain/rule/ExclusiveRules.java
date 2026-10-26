package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 서비스별 독점 작품 판정(PRD FR-04, Task 078). 프레임워크에 의존하지 않는 순수 규칙이다.
 * 독점 = "확인된 범위에서 7곳 중 이 서비스에서만 구독으로 볼 수 있는 시청 단위(영화 1편 또는 시즌 1개)".
 * 1. 이 서비스가 있음(AVAILABLE)이고 다른 서비스는 모두 있음이 아니어야 한다. 없음과 모름은 모두 "다른 곳에 없음"으로 본다.
 * 2. 모름 중 구조적 공백(STRUCTURAL_GAP, 쿠팡플레이 영화)은 비교에서 뺀다. 알 수 없는 것이 구조적으로 정해진 서비스라 비교 대상이 아니다.
 * 3. 비교에서 빠진 서비스를 뺀 나머지가 비교한 서비스 수이고, 그중 모름의 수를 함께 돌려줘 "다른 곳에도 있을 수 있음"을 화면이 알린다.
 */
public final class ExclusiveRules {

    private ExclusiveRules() {
    }

    /**
     * 모든 제공 상태에서 서비스별 독점 작품을 찾는다.
     *
     * @param rows       시청 단위 × 서비스 제공 상태 전체
     * @param serviceIds 비교에 쓰는 서비스 전체 ID(7곳). 한 시청 단위에 행이 없는 서비스는 모름으로 센다
     * @return 서비스 ID → 독점 작품 묶음(작품 ID 오름차순). 독점이 하나도 없는 서비스는 키가 없다
     */
    public static Map<Long, List<ExclusiveTitleGroup>> findExclusiveTitles(List<ExclusiveSourceRow> rows,
                                                                            List<Long> serviceIds) {
        // 1. 시청 단위별로 서비스 상태를 모은다(시청 단위 ID 순서로 처리해 결과를 항상 같게 한다)
        Map<Long, List<ExclusiveSourceRow>> rowsByUnit = new TreeMap<>();
        for (ExclusiveSourceRow row : rows) {
            List<ExclusiveSourceRow> unitRows = rowsByUnit.get(row.watchUnitId());
            if (unitRows == null) {
                unitRows = new ArrayList<>();
                rowsByUnit.put(row.watchUnitId(), unitRows);
            }
            unitRows.add(row);
        }

        // 2. 시청 단위마다 독점인 서비스를 찾아 (서비스, 작품) 별로 시즌·비교 수를 쌓는다
        Map<Long, Map<Long, Accumulator>> accumulators = new HashMap<>();
        for (List<ExclusiveSourceRow> unitRows : rowsByUnit.values()) {
            Verdict verdict = judgeUnit(unitRows, serviceIds);
            if (verdict == null) {
                continue;
            }
            ExclusiveSourceRow first = unitRows.get(0);
            Map<Long, Accumulator> byTitle = accumulators.get(verdict.exclusiveServiceId());
            if (byTitle == null) {
                byTitle = new TreeMap<>();
                accumulators.put(verdict.exclusiveServiceId(), byTitle);
            }
            Accumulator accumulator = byTitle.get(first.titleId());
            if (accumulator == null) {
                accumulator = new Accumulator(first.unitType() == UnitType.MOVIE);
                byTitle.put(first.titleId(), accumulator);
            }
            accumulator.add(first.seasonNumber(), verdict);
        }

        // 3. 작품 묶음으로 바꾼다
        Map<Long, List<ExclusiveTitleGroup>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<Long, Accumulator>> serviceEntry : accumulators.entrySet()) {
            List<ExclusiveTitleGroup> groups = new ArrayList<>();
            for (Map.Entry<Long, Accumulator> titleEntry : serviceEntry.getValue().entrySet()) {
                groups.add(titleEntry.getValue().toGroup(titleEntry.getKey()));
            }
            result.put(serviceEntry.getKey(), groups);
        }
        return result;
    }

    /** 시청 단위 하나가 독점이면 그 서비스와 비교 수를, 아니면 null을 돌려준다 */
    private static Verdict judgeUnit(List<ExclusiveSourceRow> unitRows, List<Long> serviceIds) {
        // 1. 서비스별 상태 표를 만든다
        Map<Long, ExclusiveSourceRow> byService = new HashMap<>();
        for (ExclusiveSourceRow row : unitRows) {
            byService.put(row.ottServiceId(), row);
        }

        // 2. 있음인 서비스가 정확히 하나여야 독점이다(0곳이면 아무 데도 없고, 2곳 이상이면 독점이 아니다)
        Long exclusiveServiceId = null;
        for (Long serviceId : serviceIds) {
            ExclusiveSourceRow row = byService.get(serviceId);
            if (row != null && row.status() == AvailabilityStatus.AVAILABLE) {
                if (exclusiveServiceId != null) {
                    return null;
                }
                exclusiveServiceId = serviceId;
            }
        }
        if (exclusiveServiceId == null) {
            return null;
        }

        // 3. 나머지 서비스를 비교한다: 구조적 공백은 비교에서 빼고, 행이 없는 서비스는 모름으로 센다
        int compared = 0;
        int unknown = 0;
        for (Long serviceId : serviceIds) {
            if (serviceId.equals(exclusiveServiceId)) {
                continue;
            }
            ExclusiveSourceRow row = byService.get(serviceId);
            if (row != null && row.status() == AvailabilityStatus.UNKNOWN
                    && row.unknownReason() == UnknownReason.STRUCTURAL_GAP) {
                continue;
            }
            compared++;
            if (row == null || row.status() == AvailabilityStatus.UNKNOWN) {
                unknown++;
            }
        }
        return new Verdict(exclusiveServiceId, compared, unknown);
    }

    private record Verdict(long exclusiveServiceId, int comparedServiceCount, int unknownElsewhereCount) {
    }

    /** 같은 작품·서비스의 독점 시청 단위들을 모으는 임시 상자 */
    private static final class Accumulator {
        private final boolean movie;
        private final List<Integer> seasonNumbers = new ArrayList<>();
        private int compared;
        private int unknown;

        Accumulator(boolean movie) {
            this.movie = movie;
        }

        void add(Integer seasonNumber, Verdict verdict) {
            if (seasonNumber != null) {
                seasonNumbers.add(seasonNumber);
            }
            if (verdict.comparedServiceCount() > compared) {
                compared = verdict.comparedServiceCount();
            }
            if (verdict.unknownElsewhereCount() > unknown) {
                unknown = verdict.unknownElsewhereCount();
            }
        }

        ExclusiveTitleGroup toGroup(long titleId) {
            List<Integer> seasons = null;
            if (!movie) {
                Collections.sort(seasonNumbers);
                seasons = List.copyOf(seasonNumbers);
            }
            return new ExclusiveTitleGroup(titleId, seasons, compared, unknown);
        }
    }
}
