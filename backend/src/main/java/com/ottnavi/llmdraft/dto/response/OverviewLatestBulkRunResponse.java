package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.BulkRunState;
import com.ottnavi.llmdraft.service.model.BulkStopReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 초안 목록 응답에 들어가는 최근 일괄 번역 진행 상황. 필드는 BulkDraftRunResponse(계약 BulkDraftRun)와 같다.
 * 별도 클래스로 둔 이유: 목록 응답에서는 이 값이 null일 수 있는데, springdoc은 nullable 표시를 참조하는 스키마 자체에 붙여
 * 일괄 시작 응답(null이 아님)과 같은 클래스를 쓰면 생성 명세의 nullable 표시가 요청 순서에 따라 달라진다.
 *
 * @param bulkRunId          일괄 ID
 * @param state            RUNNING 실행 중 / COMPLETED 끝까지 처리 / STOPPED 도중에 멈춤
 * @param total            처리할 작품 수
 * @param doneCount        초안을 만든 작품 수
 * @param failedCount      초안을 만들지 못한 작품 수
 * @param guardFailedCount doneCount 중 최종 결과가 실패(FAILED)인 작품 수
 * @param stopReason       멈춘 이유(STOPPED일 때만)
 * @param startedAt        시작 시각(KST)
 * @param finishedAt       끝난 시각(KST, 실행 중이면 null)
 * @param warningCount     doneCount 중 최종 결과가 주의(WARNING)인 작품 수
 */
public record OverviewLatestBulkRunResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9a1c7d52-0b8e-4f3a-8d6c-2e5f7a9b1c3d") String bulkRunId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COMPLETED") BulkRunState state,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100") int total,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "99") int doneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int failedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int guardFailedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "null") BulkStopReason stopReason,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-29T10:00:00+09:00") OffsetDateTime startedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "null") OffsetDateTime finishedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5") int warningCount) {

	/** 일괄 응답과 같은 값으로 만든다 */
	public static OverviewLatestBulkRunResponse from(BulkDraftRunResponse bulkRun) {
		return new OverviewLatestBulkRunResponse(bulkRun.bulkRunId(), bulkRun.state(), bulkRun.total(), bulkRun.doneCount(),
				bulkRun.failedCount(), bulkRun.guardFailedCount(), bulkRun.stopReason(), bulkRun.startedAt(), bulkRun.finishedAt(), bulkRun.warningCount());
	}
}
