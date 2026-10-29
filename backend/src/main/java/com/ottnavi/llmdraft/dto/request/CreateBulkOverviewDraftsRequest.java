package com.ottnavi.llmdraft.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 일괄 번역 요청(계약 CreateBulkOverviewDraftsRequest). titleIds와 count 중 정확히 하나만 보낸다.
 *
 * @param titleIds 번역할 작품 ID들(1~100개, 이미 초안이 있어도 다시 번역해 교체한다)
 * @param count    번역 후보 정렬(찜 많은 순 → 인기순) 상위 몇 개를 번역할지(1~100, 이미 초안이 있는 작품은 건너뛴다)
 */
public record CreateBulkOverviewDraftsRequest(
		@Size(max = 100, message = "100개 이하여야 합니다.")
		@ArraySchema(arraySchema = @Schema(nullable = true, example = "[1024, 2048]"), schema = @Schema(nullable = false))
		List<Long> titleIds,
		@Min(value = 1, message = "1 이상이어야 합니다.")
		@Max(value = 100, message = "100 이하여야 합니다.")
		@Schema(nullable = true, example = "50") Integer count) {
}
