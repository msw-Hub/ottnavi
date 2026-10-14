package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.CastCandidate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 작품 하나의 상세 수집 결과. TMDB 상세·시즌 응답을 규칙으로 손질한 값을 한 묶음으로 담는다.
 * 작품 단위로 전부 모은 뒤 한 번에 쓰므로, 일부 호출만 성공한 상태로 저장되는 일이 없다.
 */
public record TitleDetail(
        MediaType mediaType,
        int tmdbId, // 이 두 값으로 title 행을 찾는다
        String title, // 한국어 제목(없으면 원제). 둘 다 없으면 null이고 기존 제목을 유지한다
        String originalTitle,
        String overview, // 한국어 → 영어 대체 규칙을 적용한 줄거리. 둘 다 없으면 null
        String overviewLang, // "ko" / "en" / null
        LocalDate releaseDate,
        String posterPath,
        String backdropPath,
        String originalLanguage,
        String tvStatus, // 드라마만
        BigDecimal popularity,
        BigDecimal voteAverage,
        List<GenreInfo> genres,
        List<CastCandidate> cast, // 이미 상위 10명으로 자른 값
        List<UnitDetail> units) { // 영화는 1개, 드라마는 방영된 시즌들
}
