package com.ottnavi.llmdraft.service.model;

/** 초안 목록 필터. 자동 검사 결과로 나눈다 */
public enum DraftStatusFilter {
    PASSED,    // 성공: 실패도 경고도 없는 초안
    WARNING,   // 주의: 실패는 없지만 경고가 있는 초안
    FAILED     // 실패: 자동 검사에서 걸린 초안
}
