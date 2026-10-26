package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.Map;

/** 시청 단위 여러 개의 요약과 그 데이터 기준일(KST) */
public record WatchUnitSummaries(
        Map<Long, WatchUnitSummary> byUnitId, // 시청 단위 ID → 요약
        LocalDate dataAsOf) {                 // 판정 확인 시각 중 가장 오래된 날짜. 대상이 없으면 오늘
}
