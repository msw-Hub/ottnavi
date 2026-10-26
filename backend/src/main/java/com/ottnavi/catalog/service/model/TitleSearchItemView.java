package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.util.List;

/** 검색 결과의 작품 하나 */
public record TitleSearchItemView(
        MediaType mediaType,
        int tmdbId,
        String title,
        Integer releaseYear, // 개봉(첫 방영) 연도. 모르면 null
        String posterPath, // 없으면 null
        List<ServiceStatus> availabilities) { // 서비스별 작품 단위 상태(7개)
}
