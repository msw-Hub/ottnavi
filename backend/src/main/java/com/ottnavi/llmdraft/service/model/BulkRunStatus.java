package com.ottnavi.llmdraft.service.model;

import java.time.Instant;

/**
 * 일괄 번역 진행 상황(Redis에 최근 1건만 보관). warningCount가 없는 옛 JSON은 0으로 읽는다.
 *
 * @param bulkRunId          일괄 ID(UUID 문자열)
 * @param state            상태
 * @param total            처리할 작품 수
 * @param doneCount        초안을 만든 작품 수(자동 검사에서 걸린 것·주의 포함)
 * @param failedCount      초안을 만들지 못한 작품 수(호출 실패, 번역할 수 없는 작품, 쿨다운 등)
 * @param guardFailedCount doneCount 중 최종 결과가 실패(FAILED)인 작품 수
 * @param stopReason       중단 사유(멈추지 않았으면 null)
 * @param startedAt        시작 시각
 * @param finishedAt       끝난 시각(실행 중이면 null)
 * @param warningCount     doneCount 중 최종 결과가 주의(WARNING)인 작품 수
 */
public record BulkRunStatus(String bulkRunId, BulkRunState state, int total, int doneCount, int failedCount,
                          int guardFailedCount, BulkStopReason stopReason, Instant startedAt, Instant finishedAt,
                          int warningCount) {

    /** 처리 건수를 바꾼 새 값을 만든다(record는 불변이라 복사해서 쓴다) */
    public BulkRunStatus withProgress(int doneCount, int failedCount, int guardFailedCount, int warningCount) {
        return new BulkRunStatus(bulkRunId, state, total, doneCount, failedCount, guardFailedCount, stopReason,
                startedAt, finishedAt, warningCount);
    }

    /** 끝난 상태(완료 또는 중단)로 바꾼 새 값을 만든다 */
    public BulkRunStatus finished(BulkRunState finalState, BulkStopReason reason, Instant at) {
        return new BulkRunStatus(bulkRunId, finalState, total, doneCount, failedCount, guardFailedCount, reason,
                startedAt, at, warningCount);
    }
}
