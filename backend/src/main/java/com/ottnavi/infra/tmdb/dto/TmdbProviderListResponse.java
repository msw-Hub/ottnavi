package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 제공처 전체 목록 응답. 우리 7곳 외 제공처를 "미매핑"으로 저장하는 일은 Task 043 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProviderListResponse(List<TmdbProviderInfo> results) {
}
