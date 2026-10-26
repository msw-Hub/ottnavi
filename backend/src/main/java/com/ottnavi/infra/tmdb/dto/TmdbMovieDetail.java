package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 영화 상세. credits는 append_to_response에 credits를 줬을 때만 채워지고 아니면 null이다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieDetail(
        long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        String overview,                                         // 한국어 줄거리가 ""인지 null인지는 확인 필요
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("release_date") String releaseDate,
        Integer runtime,                                         // null·0 가능 (추정은 Task 042)
        List<TmdbGenre> genres,
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        TmdbCredits credits) {
}
