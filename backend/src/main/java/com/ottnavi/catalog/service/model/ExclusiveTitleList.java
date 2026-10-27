package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 한 서비스의 독점 목록 전체(인기도 높은 순). 서비스마다 하나씩 Redis `exclusiveTitlesV2` 캐시에 저장한다.
 * 데이터 부족 서비스(쿠팡플레이)도 판정하므로 judged는 현재 항상 true다. 쿠팡플레이는 영화가 구조적 공백이라 드라마(시즌)만 목록에 나온다.
 */
public record ExclusiveTitleList(
        long ottServiceId,
        boolean judged, // 독점을 판정했는지. 계약 호환을 위해 남겼고 현재는 모든 서비스가 true다
        LocalDate dataAsOf, // 제공 상태의 데이터 기준일(KST)
        List<ExclusiveTitleItem> items) {
}
