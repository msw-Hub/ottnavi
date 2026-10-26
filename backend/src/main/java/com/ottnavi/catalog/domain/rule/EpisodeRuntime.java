package com.ottnavi.catalog.domain.rule;

import java.time.LocalDate;

/** 시즌 상세의 회차 한 건에서 러닝타임 계산에 필요한 값만 옮긴 것 */
public record EpisodeRuntime(
        LocalDate airDate, // 방영일. 미정이거나 비어 있으면 null
        Integer runtime) { // 분. 비었으면 null (0도 빈 값으로 본다)
}
