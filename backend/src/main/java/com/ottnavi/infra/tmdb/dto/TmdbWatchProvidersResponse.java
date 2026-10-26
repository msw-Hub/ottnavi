package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** 작품(영화 또는 시즌)의 나라별 시청 제공처. results의 키는 나라 코드("KR"). KR만 골라 쓰는 일은 Task 042 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbWatchProvidersResponse(
        long id,
        Map<String, TmdbRegionProviders> results) {
}
