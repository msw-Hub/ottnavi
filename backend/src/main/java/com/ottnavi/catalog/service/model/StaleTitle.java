package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;

/** 마지막 수집 시각이 기준을 넘긴 작품 한 건: 6개월 갱신 점검이 TMDB에 다시 물을 때 필요한 값만 담는다 */
public record StaleTitle(
        Long titleId,
        MediaType mediaType,
        Integer tmdbId) {
}
