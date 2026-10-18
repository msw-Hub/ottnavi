package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.util.List;

/** 독점 목록의 작품 하나. Redis 캐시에 JSON으로 저장되는 값의 일부다 */
public record ExclusiveTitleItem(
        MediaType mediaType,
        int tmdbId,
        String title,
        Integer releaseYear, // 개봉(첫 방영) 연도. 모르면 null
        String posterPath, // 없으면 null
        List<Integer> exclusiveSeasonNumbers, // 드라마에서 이 서비스 독점인 시즌 번호(오름차순). 영화는 null
        int comparedServiceCount, // 비교한 다른 서비스 수(구조적 공백 제외)
        int unknownElsewhereCount) { // 비교한 다른 서비스 중 모름인 곳의 수
}
