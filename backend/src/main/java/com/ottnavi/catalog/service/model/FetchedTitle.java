package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.rule.KrProviders;
import java.util.Map;

/**
 * TMDB에서 한 번에 받아 둔 작품 하나의 저장용 묶음(상세 + 시청 단위별 KR 제공처). TitleFillService가 한 트랜잭션으로 저장한다.
 * 외부 호출을 모두 끝낸 뒤에 만들므로, 이 값이 있으면 저장 중에는 TMDB를 부르지 않는다.
 */
public record FetchedTitle(
        TitleDetail detail,
        Map<Integer, KrProviders> providersBySeason) { // 시즌 번호별 KR 제공처. 영화(시청 단위 1개)는 키가 null
}
