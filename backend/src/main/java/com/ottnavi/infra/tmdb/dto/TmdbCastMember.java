package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 출연진 한 명 (credits.cast[]). 상위 10명 자르기는 Task 042 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCastMember(
        long id,
        String name,
        String character,
        Integer order,
        @JsonProperty("profile_path") String profilePath) {
}
