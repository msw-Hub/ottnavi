package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.UnitType;

/** 판정 Step이 읽는 시청 단위 한 건: 제공처 조회에 필요한 값만 담는다 */
public record AvailabilityTarget(
        long watchUnitId,
        UnitType unitType, // MOVIE면 영화 제공처, SEASON이면 시즌 제공처를 부른다
        int tmdbId, // 작품의 TMDB ID
        Integer seasonNumber) { // 영화는 null
}
