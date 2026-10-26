package com.ottnavi.catalog.domain.rule;

/** 러닝타임 계산 결과. watch_unit의 회차·러닝타임 컬럼에 그대로 대응한다 */
public record RuntimeResult(
        int episodeCount, // 전체 회차 수(영화는 1)
        int airedEpisodeCount, // 방영된 회차 수(영화는 1)
        int totalRuntimeMin, // 방영된 회차 runtime 합 / 영화 runtime
        int runtimeMissingCount, // runtime이 비어 기본값을 쓴 회차 수
        boolean runtimeEstimated) { // 추정값 포함 여부(화면 "추정" 표시)
}
