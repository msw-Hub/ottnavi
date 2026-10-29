package com.ottnavi.llmdraft.repository;

/** 호출 상한 검사 결과 */
public enum QuotaDecision {
    ACQUIRED,         // 통과하고 카운터를 1 올렸다
    MINUTE_EXCEEDED,  // 이번 1분의 상한을 다 썼다
    DAY_EXCEEDED      // 오늘(태평양 시간)의 상한을 다 썼다
}
