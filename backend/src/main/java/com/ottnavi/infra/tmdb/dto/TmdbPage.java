package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 목록 응답 공통 틀(discover·search). 모르는 필드는 버린다(Boot 4 + Jackson 3은 기본이 실패, 3.5절) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbPage<T>(
        int page,
        List<T> results,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("total_results") int totalResults) {
}
