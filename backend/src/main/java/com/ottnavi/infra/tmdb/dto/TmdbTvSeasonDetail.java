package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 드라마 시즌 상세(회차 목록) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvSeasonDetail(
        Long id,                                                 // 확인 필요: c7 예시에는 문자열 _id만 보였다. 없으면 null (tmdb_season_id는 TmdbSeasonSummary.id를 쓴다)
        @JsonProperty("season_number") int seasonNumber,
        String name,
        @JsonProperty("air_date") String airDate,
        @JsonProperty("poster_path") String posterPath,
        List<TmdbEpisode> episodes) {
}
