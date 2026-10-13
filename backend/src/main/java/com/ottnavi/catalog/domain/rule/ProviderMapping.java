package com.ottnavi.catalog.domain.rule;

import com.ottnavi.provider.domain.enums.ProviderTier;

/** TMDB 제공처 번호 하나가 가리키는 우리 서비스와 tier(tmdb_provider 한 행에서 MAPPED인 것만 옮긴 값) */
public record ProviderMapping(
        long ottServiceId, // 연결된 ott_service.id
        ProviderTier tier) { // STANDARD / AD(광고형)
}
