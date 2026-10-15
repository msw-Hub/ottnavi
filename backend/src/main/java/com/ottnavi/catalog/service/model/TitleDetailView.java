package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.time.LocalDate;
import java.util.List;

/**
 * 작품 상세 조회 결과. 응답으로 바뀌기 전의 값이며 titleDetail 캐시에 JSON으로 저장되는 값이기도 하다.
 * 필드를 바꾸면 캐시에 남은 옛 값과 맞지 않을 수 있으므로, 배포 때 캐시를 비우거나 TTL(24시간)을 기다린다.
 */
public record TitleDetailView(
        MediaType mediaType,
        int tmdbId,
        String title,
        String originalTitle,
        String posterPath, // 없으면 null
        String overview, // 없으면 null
        String overviewLang, // "ko" / "en" / null
        List<String> genres,
        LocalDate releaseDate, // 모르면 null
        Integer runtimeMin, // 영화만. 드라마는 null
        boolean isRuntimeEstimated, // 영화만 의미가 있고 드라마는 false
        List<CastView> cast,
        List<ServiceStatus> availabilities,
        List<TitleSeasonView> seasons, // 영화는 빈 목록
        LocalDate dataAsOf) {
}
