package com.ottnavi.llmdraft.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 관리자 번역 줄거리 대량 되돌리기 요청(계약 OverviewBulkRevertRequest).
 *
 * @param translatedBefore 이 시각보다 먼저 반영한 번역만 되돌린다. 없으면(null 또는 생략) 번역 전부를 되돌린다
 * @param isPreview         true면 DB를 바꾸지 않고 되돌려질 작품 수만 센다(미리보기). 없으면(null 또는 생략) false로 본다
 */
public record RevertTranslatedOverviewsRequest(
		@Schema(nullable = true, example = "2026-04-29T00:00:00+09:00") OffsetDateTime translatedBefore,
		@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "false", example = "false") Boolean isPreview) {
}
