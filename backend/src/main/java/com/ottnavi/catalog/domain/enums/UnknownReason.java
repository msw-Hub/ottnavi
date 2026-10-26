package com.ottnavi.catalog.domain.enums;

/** 제공 상태가 UNKNOWN인 이유 */
public enum UnknownReason {
    NO_KR_DATA, // TMDB 응답에 KR 데이터가 없음
    STRUCTURAL_GAP // 구조적으로 알 수 없음(쿠팡플레이 영화 등)
}
