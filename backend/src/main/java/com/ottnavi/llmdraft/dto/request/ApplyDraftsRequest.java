package com.ottnavi.llmdraft.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 초안 반영 요청(계약 ApplyOverviewDraftsRequest). draftIds와 allPassed 중 정확히 하나만 보낸다.
 *
 * @param draftIds  반영할 초안 ID들(최대 500개)
 * @param allPassed true면 성공(실패도 경고도 없는) 초안 전부를 반영한다. 주의(경고만 있는) 초안은 제외된다
 */
public record ApplyDraftsRequest(
		@Size(max = 500, message = "500개 이하여야 합니다.")
		@ArraySchema(arraySchema = @Schema(nullable = true), schema = @Schema(nullable = false))
		List<String> draftIds,
		@Schema(nullable = true, example = "false") Boolean allPassed) {
}
