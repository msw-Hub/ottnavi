package com.ottnavi.provider.domain;

/** TMDB 제공처의 매핑 상태. 판정에는 MAPPED만 쓴다 */
public enum MappingState {
    UNMAPPED, // 처음 발견된 상태
    MAPPED, // 우리 서비스에 연결됨
    IGNORED // 관련 없는 제공처
}
