package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 시즌 상세 안의 회차 한 건 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbEpisode(
        @JsonProperty("episode_number") int episodeNumber,
        String name,
        @JsonProperty("air_date") String airDate,                // 미방영이면 null·"" 가능
        Integer runtime,                                         // null 가능 (ERD runtime_missing_count, 추정은 Task 042)
        @JsonProperty("episode_type") String episodeType) {      // 예: standard
}
