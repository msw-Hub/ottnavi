package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** discover·search 영화 결과 한 건 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieSummary(
        long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("release_date") String releaseDate,      // 날짜도 String. 빈 문자열("")이 올 수 있다
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("genre_ids") List<Integer> genreIds) {
}
