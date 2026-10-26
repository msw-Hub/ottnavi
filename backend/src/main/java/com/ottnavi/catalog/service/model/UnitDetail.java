package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import java.time.LocalDate;

/** 시청 단위(영화 1편 또는 시즌 1개) 하나의 수집 결과 */
public record UnitDetail(
        UnitType unitType,
        Integer seasonNumber, // 영화는 null
        Integer tmdbSeasonId, // 시즌만
        String name, // 시즌 표시 이름. 영화는 null(title 값을 쓴다)
        String posterPath,
        LocalDate airDate,
        RuntimeResult runtime) { // 회차 수·방영 회차·러닝타임 합·추정 여부
}
