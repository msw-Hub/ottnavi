package com.ottnavi.llmdraft.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 번역 줄거리 대량 되돌리기 결과(계약 OverviewBulkRevertResult).
 *
 * @param revertedCount 영어로 되돌린 작품 수. 미리보기(isPreview)면 되돌려질 작품 수. 대상이 없으면 0
 * @param isPreview      미리보기 요청이었는지. true면 DB를 바꾸지 않았다
 */
public record RevertTranslatedOverviewsResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "37") long revertedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isPreview) {
}
