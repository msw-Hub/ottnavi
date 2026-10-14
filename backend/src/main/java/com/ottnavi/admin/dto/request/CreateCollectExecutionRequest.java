package com.ottnavi.admin.dto.request;

import com.ottnavi.collect.job.CollectTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 수집 실행 요청(계약 CreateCollectExecutionRequest).
 *
 * @param targetDate 수집 기준 날짜. 비우면 오늘(KST). 오늘이 아닌 날짜는 로컬 프로필에서만 받는다
 * @param tier       수집 범위. MVP는 ALL만 실행할 수 있다
 */
public record CreateCollectExecutionRequest(
		@Schema(nullable = true, example = "2026-10-12") LocalDate targetDate,
		@NotNull(message = "수집 범위는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ALL") CollectTier tier) {
}
