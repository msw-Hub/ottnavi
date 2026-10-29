package com.ottnavi.llmdraft.service.model;

/** 일괄 번역이 도중에 멈춘 이유 */
public enum BulkStopReason {
    DISABLED,              // Gemini 키가 없다
    RATE_LIMITED,          // 분당 상한 또는 구글 429
    DAILY_LIMIT,           // 하루 상한을 다 썼다
    DRAFT_LIMIT,           // 대기 초안이 가득 찼다
    CALL_FAILED_REPEATED,  // 호출 실패가 연속으로 이어졌다
    SERVER_RESTARTED       // 실행 중 서버가 다시 시작됐다(상태를 읽을 때 판정)
}
