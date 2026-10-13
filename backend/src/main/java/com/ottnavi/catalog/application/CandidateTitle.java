package com.ottnavi.catalog.application;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** discover 목록 한 건에서 뽑은 후보 작품 정보. 줄거리·상세는 Task 044가 채운다 */
public record CandidateTitle(
        MediaType mediaType, // MOVIE / TV
        int tmdbId, // TMDB 작품 ID
        String title, // 목록에 나온 한국어 제목(없으면 원제)
        String originalTitle,
        LocalDate releaseDate, // 영화 개봉일 / 드라마 첫 방영일, 없으면 null
        String posterPath,
        String backdropPath,
        String originalLanguage,
        BigDecimal popularity, // NUMERIC(10,3)에 맞춘 값
        BigDecimal voteAverage) { // NUMERIC(4,2)에 맞춘 값
}
