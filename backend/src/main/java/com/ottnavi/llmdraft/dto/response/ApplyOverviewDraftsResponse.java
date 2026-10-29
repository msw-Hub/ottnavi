package com.ottnavi.llmdraft.dto.response;

import com.ottnavi.llmdraft.service.model.ApplyItemResult;
import com.ottnavi.llmdraft.service.model.ApplyOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 초안 반영 결과(계약 ApplyOverviewDraftsResult).
 *
 * @param appliedCount 줄거리를 바꾼 건수
 * @param skippedCount 건너뛴 건수
 * @param results      초안별 결과
 */
public record ApplyOverviewDraftsResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "97") int appliedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int skippedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ApplyOverviewDraftItemResponse> results) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static ApplyOverviewDraftsResponse from(List<ApplyItemResult> results) {
		List<ApplyOverviewDraftItemResponse> items = new ArrayList<>();
		int applied = 0;
		for (ApplyItemResult result : results) {
			items.add(ApplyOverviewDraftItemResponse.from(result));
			if (result.outcome() == ApplyOutcome.APPLIED) {
				applied++;
			}
		}
		return new ApplyOverviewDraftsResponse(applied, results.size() - applied, items);
	}
}
