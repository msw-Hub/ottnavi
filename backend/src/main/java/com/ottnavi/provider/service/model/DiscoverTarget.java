package com.ottnavi.provider.service.model;

import com.ottnavi.provider.domain.enums.OttServiceCode;

/** discover를 돌릴 서비스 한 곳과 그 서비스의 TMDB 제공처 번호 묶음 */
public record DiscoverTarget(
        OttServiceCode serviceCode, // 우리 서비스 코드
        String withWatchProviders) { // TMDB with_watch_providers 값. 번호를 |(OR)로 묶는다. 예: "8|1796"
}
