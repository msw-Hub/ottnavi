package com.ottnavi.catalog.service.model;

import java.util.List;

/** 드라마의 시즌 하나(조회 API용) */
public record TitleSeasonView(
        int seasonNumber,
        String name,
        int episodeCount,
        Integer airYear, // 방영 연도. 모르면 null
        int totalRuntimeMin,
        boolean isRuntimeEstimated,
        List<ServiceStatus> availabilities) {
}
