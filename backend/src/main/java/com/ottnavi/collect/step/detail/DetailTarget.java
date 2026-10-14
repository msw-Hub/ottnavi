package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.MediaType;

/** 상세 Step이 읽는 작품 한 건: TMDB를 부르는 데 필요한 값만 담는다(엔티티를 들고 다니지 않는다) */
public record DetailTarget(
        long titleId, // 로그용
        MediaType mediaType,
        int tmdbId) {
}
