package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 드라마 상세 안의 시즌 요약 (seasons[]). 시즌 0(스페셜)도 포함돼 있다. 거르는 일은 Task 042 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSeasonSummary(
        long id,                                                 // tmdb_season_id
        @JsonProperty("season_number") int seasonNumber,
        String name,
        @JsonProperty("air_date") String airDate,                // null·"" 가능
        @JsonProperty("episode_count") Integer episodeCount,
        @JsonProperty("poster_path") String posterPath) {
}
