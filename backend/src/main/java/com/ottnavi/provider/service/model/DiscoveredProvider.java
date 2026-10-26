package com.ottnavi.provider.service.model;

/** TMDB 제공처 전체 목록의 한 곳(우리 서비스 여부와 무관) */
public record DiscoveredProvider(
        int providerId, // TMDB provider_id
        String name, // TMDB 원본 이름
        String logoPath) { // 로고 상대 경로, 없을 수 있다
}
