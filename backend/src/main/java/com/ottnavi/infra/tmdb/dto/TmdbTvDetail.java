package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 드라마 상세 (시즌 목록 포함). credits는 append_to_response에 credits를 줬을 때만 채워진다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvDetail(
        long id,
        String name,
        @JsonProperty("original_name") String originalName,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("first_air_date") String firstAirDate,
        String status,
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("number_of_seasons") Integer numberOfSeasons,
        List<TmdbGenre> genres,
        @JsonProperty("episode_run_time") List<Integer> episodeRunTime,   // 빈 배열 가능
        List<TmdbSeasonSummary> seasons,
        TmdbCredits credits) {
}
