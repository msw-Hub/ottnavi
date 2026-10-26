package com.ottnavi.home.domain.rule;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 인기 순위의 한 작품(순위는 목록 위치). 작품 표시값은 가장 최근 스냅샷의 것이다 */
public record RankedPopularity(
        MediaType mediaType,
        int tmdbId,
        String title,
        String posterPath,
        LocalDate releaseDate,
        BigDecimal averagePopularity) { // 스냅샷 날 수로 나눈 평균 인기도(없던 날은 0으로 센다)
}
