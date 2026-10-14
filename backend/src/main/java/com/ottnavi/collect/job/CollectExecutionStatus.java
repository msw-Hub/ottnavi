package com.ottnavi.collect.job;

/** 관리자 화면에 보여 주는 수집 실행 상태. Batch의 상태와 달리 서버 재기동으로 끊긴 실행(STOPPED)을 따로 구분한다 */
public enum CollectExecutionStatus {
    RUNNING, // 지금 이 서버에서 실행 중
    COMPLETED, // 끝까지 성공
    FAILED, // 실패했거나 복구로 실패 처리됨
    STOPPED // 서버가 꺼지면서 끊긴 것으로 보이는 실행. 복구가 필요하다
}
