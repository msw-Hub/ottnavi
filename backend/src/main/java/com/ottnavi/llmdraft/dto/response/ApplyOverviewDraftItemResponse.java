package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.ApplyItemResult;
import com.ottnavi.llmdraft.service.model.ApplyOutcome;
import com.ottnavi.llmdraft.service.model.ApplySkipReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 초안 한 건의 반영 결과(계약 ApplyOverviewDraftItem).
 *
 * @param draftId 초안 ID
 * @param titleId 작품 ID(초안이 없어 건너뛰었으면 null)
 * @param outcome APPLIED 반영함 / SKIPPED 건너뜀
 * @param reason  건너뛴 이유(SKIPPED일 때만): DRAFT_NOT_FOUND 초안이 없음 / GUARD_FAILED 자동 검사에서 걸림 /
 *                ORIGINAL_CHANGED 번역한 뒤 줄거리가 바뀌었거나 이미 한국어임
 */
public record ApplyOverviewDraftItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3f2b8c1e-5d4a-4c6b-9a7e-1b2c3d4e5f60") String draftId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1024") Long titleId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "APPLIED") ApplyOutcome outcome,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "null") ApplySkipReason reason) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static ApplyOverviewDraftItemResponse from(ApplyItemResult result) {
		return new ApplyOverviewDraftItemResponse(result.draftId(), result.titleId(), result.outcome(), result.reason());
	}
}
