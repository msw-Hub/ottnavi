package com.ottnavi.collect.job;

/**
 * 수집 실행 안의 단계 하나의 진행 상태(계약 CollectStepStatus).
 * 실행 전체의 상태(CollectExecutionStatus)와 이름을 나눠 둔다: 실행은 실패여도 그 안의 단계는 완료·대기일 수 있다.
 */
public enum CollectStepStatus {
    PENDING, // 아직 시작하지 않음. 실패·중단된 실행이면 시작하지 못한 단계다
    RUNNING, // 지금 이 서버에서 진행 중
    COMPLETED, // 끝남. 재시작한 실행이면 앞선 실행에서 끝낸 단계도 여기에 든다
    FAILED, // 실패했거나 복구로 실패 처리됨
    STOPPED // 서버가 꺼지면서 끝나지 못한 단계(실행 상태가 STOPPED일 때)
}
