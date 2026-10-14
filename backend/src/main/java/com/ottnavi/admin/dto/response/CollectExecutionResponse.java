package com.ottnavi.admin.dto.response;

import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.CollectTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 수집 실행 한 건(계약 CollectExecution). 필드는 모두 required이고 null이 올 수 있는 값은 nullable로 표시한다.
 *
 * @param executionId    실행 ID(Spring Batch jobExecutionId)
 * @param targetDate     수집 기준 날짜
 * @param tier           수집 범위
 * @param status         화면 상태(서버 재기동으로 끊긴 실행은 STOPPED)
 * @param startedAt      시작 시각(KST 오프셋)
 * @param endedAt        끝난 시각. 실행 중·중단됨·복구된 실행은 null
 * @param processedCount 처리한 작품 수
 * @param changedCount   제공 상태가 바뀐 건수(시청 단위 × 서비스)
 * @param failureReason  실패 사유. 실패가 아니면 null
 * @param canRecover     복구 버튼을 켤 수 있는지
 * @param canRestart     재시작 버튼을 켤 수 있는지
 */
public record CollectExecutionResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5") long executionId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-12") LocalDate targetDate,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ALL") CollectTier tier,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COMPLETED") CollectExecutionStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-12T03:00:00+09:00") OffsetDateTime startedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) OffsetDateTime endedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1840") int processedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") int changedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String failureReason,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean canRecover,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean canRestart) {
}
