package com.ottnavi.llmdraft.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 영어로 되돌린 결과(계약 OverviewRevertResult).
 *
 * @param titleId 되돌린 작품 ID. 줄거리와 언어가 비워졌고, 다음 수집이 TMDB 영어 줄거리를 다시 채운다
 */
public record OverviewRevertResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1024") Long titleId) {
}
