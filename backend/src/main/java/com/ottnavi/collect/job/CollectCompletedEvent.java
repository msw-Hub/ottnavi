package com.ottnavi.collect.job;

/**
 * 수집 Job이 COMPLETED로 끝났다는 사실을 알리는 이벤트(Task 069). 재계산 같은 후속 작업이 이 이벤트를 받아 시작한다.
 * 실패·중단된 수집은 발행하지 않는다.
 *
 * @param executionId 끝난 수집 실행의 ID(Spring Batch jobExecutionId)
 */
public record CollectCompletedEvent(long executionId) {
}
