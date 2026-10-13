package com.ottnavi.catalog.domain.rule;

import java.util.Set;

/**
 * 작품(영화) 또는 시즌 하나의 한국(KR) 제공처 조회 결과 중 판정에 쓰는 부분.
 * TMDB 응답(TmdbWatchProvidersResponse)을 이 형태로 바꾸는 일은 수집 Job(Task 044)이 한다.
 */
public record KrProviders(
        boolean krDataPresent, // 응답의 results에 "KR" 키가 있었는지
        Set<Integer> flatrateProviderIds) { // KR의 flatrate(구독) 제공처 번호들. 없으면 빈 집합

    public KrProviders {
        flatrateProviderIds = flatrateProviderIds == null ? Set.of() : Set.copyOf(flatrateProviderIds);
    }

    /** KR 데이터 자체가 없는 경우 */
    public static KrProviders noKrData() {
        return new KrProviders(false, Set.of());
    }

    /** KR 데이터가 있고 flatrate 제공처가 주어진 경우(빈 집합이면 대여·구매만 있거나 아무것도 없는 것) */
    public static KrProviders of(Set<Integer> flatrateProviderIds) {
        return new KrProviders(true, flatrateProviderIds);
    }
}
