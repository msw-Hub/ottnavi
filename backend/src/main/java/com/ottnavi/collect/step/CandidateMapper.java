package com.ottnavi.collect.step;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.TitleDetailRules;
import com.ottnavi.catalog.service.model.CandidateTitle;
import com.ottnavi.collect.step.CandidateReader.CandidatePage;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** TMDB discover 응답 -> 후보(CandidateTitle) 변환. 응답 DTO를 이 밖으로 넘기지 않는다 */
final class CandidateMapper {

    private static final int MAX_TITLE_LENGTH = 300; // title.title 컬럼 길이

    private CandidateMapper() {
    }

    /** 응답 한 쪽을 후보 한 쪽으로 바꾼다. 저장할 수 없는 항목(제목 없음)은 뺀다 */
    static <T> CandidatePage toPage(TmdbPage<T> page, Function<T, CandidateTitle> mapper) {
        if (page == null || page.results() == null) {
            return new CandidatePage(List.of(), 0);
        }
        List<CandidateTitle> items = page.results().stream().map(mapper).filter(Objects::nonNull).toList();
        return new CandidatePage(items, page.totalPages());
    }

    static CandidateTitle fromMovie(TmdbMovieSummary movie) {
        return toCandidate(MediaType.MOVIE, movie.id(), movie.title(), movie.originalTitle(), movie.releaseDate(),
                movie.posterPath(), movie.backdropPath(), movie.originalLanguage(), movie.popularity(),
                movie.voteAverage());
    }

    static CandidateTitle fromTv(TmdbTvSummary tv) {
        // 드라마의 날짜 필드는 first_air_date다(영화는 release_date)
        return toCandidate(MediaType.TV, tv.id(), tv.name(), tv.originalName(), tv.firstAirDate(),
                tv.posterPath(), tv.backdropPath(), tv.originalLanguage(), tv.popularity(), tv.voteAverage());
    }

    /** 한국어 제목이 없으면 원제를 쓴다. 둘 다 없으면 저장할 수 없어 null(건너뜀). 컬럼 길이·소수 자릿수에 맞춰 다듬는다 */
    private static CandidateTitle toCandidate(MediaType mediaType, long id, String title, String originalTitle,
                                              String date, String posterPath, String backdropPath,
                                              String originalLanguage, Double popularity, Double voteAverage) {
        String chosen = (title != null && !title.isBlank()) ? title : originalTitle;
        if (chosen == null || chosen.isBlank()) {
            return null;
        }
        return new CandidateTitle(mediaType, Math.toIntExact(id), cut(chosen, MAX_TITLE_LENGTH),
                originalTitle == null ? null : cut(originalTitle, MAX_TITLE_LENGTH),
                TitleDetailRules.parseDate(date), posterPath, backdropPath, originalLanguage,
                decimal(popularity, 3), decimal(voteAverage, 2));
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Double을 NUMERIC 자릿수(scale)에 맞춘 BigDecimal로 바꾼다. 비어 있으면 null */
    private static BigDecimal decimal(Double value, int scale) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
