package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 한 나라의 제공처. 목록이 없는 항목(flatrate 없음 등)은 null로 온다 → 소비 쪽이 null을 빈 목록으로 본다.
 * ads·free가 같은 구조로 더 올 수 있지만 쓰지 않아 ignoreUnknown으로 버린다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbRegionProviders(
        String link,
        List<TmdbProviderRef> flatrate,   // 구독
        List<TmdbProviderRef> rent,       // 대여
        List<TmdbProviderRef> buy) {      // 구매
}
