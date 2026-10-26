package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** 제공처 전체 목록의 한 곳 (/watch/providers/movie|tv) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProviderInfo(
        @JsonProperty("provider_id") int providerId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("logo_path") String logoPath,
        @JsonProperty("display_priorities") Map<String, Integer> displayPriorities) {   // 나라별 노출 순서
}
