package com.ottnavi.llmdraft.service.model;

/** 일괄 번역의 상태 */
public enum BulkRunState {
    RUNNING,    // 실행 중
    COMPLETED,  // 끝까지 처리했다
    STOPPED     // 중단 사유(BulkStopReason)로 도중에 멈췄다
}
