package com.ottnavi.llmdraft.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 번역 초안 수정 요청(계약 EditOverviewDraftRequest).
 *
 * @param translatedText 관리자가 고친 번역문. 공백만으로는 보낼 수 없고, 자동 검사의 최대 글자 수(app.llm.guard.max-length 기본 1000)와
 *                       맞춰 1000자까지 받는다
 */
public record EditDraftRequest(
		@NotBlank(message = "번역문은 비어 있을 수 없습니다.")
		@Size(max = 1000, message = "1000자 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 1000,
				example = "한 형사가 조용한 해안 마을에서 살인범을 쫓는다.") String translatedText) {
}
