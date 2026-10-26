package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.util.List;

/**
 * 시청 단위(영화 1편 또는 시즌 1개) 하나의 요약. 찜 목록처럼 시청 단위 기준 화면이 쓴다.
 * 시즌 이름이 비면 "시즌 N"으로 채우고, 영화는 seasonNumber·seasonName이 null이다.
 */
public record WatchUnitSummary(
        long watchUnitId,
        long titleId,               // 소속 작품(title) ID. 같은 시리즈의 시즌을 묶는 식별자로 쓴다
        MediaType mediaType,
        int tmdbId,
        String title,
        String posterPath,          // 작품 포스터. 없으면 null
        Integer seasonNumber,       // 영화는 null
        String seasonName,          // 영화는 null
        int requiredMin,            // 다 보는 데 필요한 시간(분)
        boolean isRuntimeEstimated, // 추정값 포함 여부
        List<ServiceStatus> availabilities) { // 서비스별 제공 상태(서비스 ID 오름차순, 판정 행이 없으면 UNKNOWN)
}
