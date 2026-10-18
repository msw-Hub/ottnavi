package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 한 서비스의 독점 목록 전체(인기도 높은 순). 서비스마다 하나씩 Redis `exclusiveTitles` 캐시에 저장한다.
 * 데이터 부족 서비스(judged=false)는 목록을 만들지 않으므로 items가 비어 있다.
 */
public record ExclusiveTitleList(
        long ottServiceId,
        boolean judged, // 독점을 판정했는지. 데이터 부족(INSUFFICIENT) 서비스는 false
        LocalDate dataAsOf, // 제공 상태의 데이터 기준일(KST)
        List<ExclusiveTitleItem> items) {
}
