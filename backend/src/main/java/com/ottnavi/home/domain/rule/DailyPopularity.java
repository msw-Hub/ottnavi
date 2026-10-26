package com.ottnavi.home.domain.rule;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 인기 스냅샷 한 줄(그날 어떤 작품의 TMDB 인기도). 서비스는 순위 계산에 필요하지 않아 담지 않는다 */
public record DailyPopularity(
        LocalDate date,
        MediaType mediaType,
        int tmdbId,
        String title,
        String posterPath,
        LocalDate releaseDate,
        BigDecimal popularity) {
}
