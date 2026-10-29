package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.BulkRunState;
import com.ottnavi.llmdraft.service.model.BulkRunStatus;
import com.ottnavi.llmdraft.service.model.BulkStopReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 일괄 번역 진행 상황(계약 BulkDraftRun). 일괄 시작 응답(202)과 초안 목록 응답의 bulkRun 필드가 같은 모양이다.
 *
 * @param bulkRunId          일괄 ID
 * @param state            RUNNING 실행 중 / COMPLETED 끝까지 처리 / STOPPED 도중에 멈춤
 * @param total            처리할 작품 수
 * @param doneCount        초안을 만든 작품 수(검사에서 걸린 것 포함)
 * @param failedCount      초안을 만들지 못한 작품 수
 * @param guardFailedCount doneCount 중 최종 결과가 실패(FAILED)인 작품 수
 * @param stopReason       멈춘 이유(STOPPED일 때만): DISABLED 키 없음 / RATE_LIMITED 분당 상한·구글 429 /
 *                         DAILY_LIMIT 하루 상한 / DRAFT_LIMIT 대기 초안 가득 참 / CALL_FAILED_REPEATED 호출 실패 연속 /
 *                         SERVER_RESTARTED 서버 재시작
 * @param startedAt        시작 시각(KST)
 * @param finishedAt       끝난 시각(KST, 실행 중이면 null)
 * @param warningCount     doneCount 중 최종 결과가 주의(WARNING)인 작품 수
 */
public record BulkDraftRunResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9a1c7d52-0b8e-4f3a-8d6c-2e5f7a9b1c3d") String bulkRunId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "RUNNING") BulkRunState state,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100") int total,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "37") int doneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int failedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int guardFailedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "null") BulkStopReason stopReason,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-29T10:00:00+09:00") OffsetDateTime startedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "null") OffsetDateTime finishedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5") int warningCount) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static BulkDraftRunResponse from(BulkRunStatus status) {
		OffsetDateTime finishedAt = null;
		if (status.finishedAt() != null) {
			finishedAt = status.finishedAt().atZone(KST).toOffsetDateTime();
		}
		return new BulkDraftRunResponse(status.bulkRunId(), status.state(), status.total(), status.doneCount(),
				status.failedCount(), status.guardFailedCount(), status.stopReason(),
				status.startedAt().atZone(KST).toOffsetDateTime(), finishedAt, status.warningCount());
	}
}
