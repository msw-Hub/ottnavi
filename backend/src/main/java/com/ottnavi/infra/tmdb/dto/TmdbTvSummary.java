package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** discover·search 드라마 결과 한 건 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvSummary(
        long id,
        String name,
        @JsonProperty("original_name") String originalName,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("first_air_date") String firstAirDate,   // 빈 문자열("")이 올 수 있다 (c7 TMDB 예시)
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("genre_ids") List<Integer> genreIds,
        @JsonProperty("origin_country") List<String> originCountry) {
}
