package com.ottnavi.provider.service.model;

import com.ottnavi.provider.domain.enums.ProviderTier;

/** MAPPED 상태의 TMDB 제공처 하나가 가리키는 우리 서비스와 tier */
public record MappedProvider(
        int providerId, // TMDB provider_id
        long ottServiceId, // 연결된 ott_service.id
        ProviderTier tier) {
}
