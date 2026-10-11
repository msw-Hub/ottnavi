package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 작품 하나에 붙은 제공처 한 곳 (flatrate/rent/buy 배열의 원소) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProviderRef(
        @JsonProperty("provider_id") int providerId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("logo_path") String logoPath,
        @JsonProperty("display_priority") Integer displayPriority) {
}
