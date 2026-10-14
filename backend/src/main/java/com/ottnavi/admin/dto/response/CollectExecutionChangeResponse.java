package com.ottnavi.admin.dto.response;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 실행에서 제공 상태가 바뀐 작품 한 건(계약 CollectExecutionChange). 필드는 모두 required이고 null 가능 값은 nullable이다.
 *
 * @param mediaType      작품 종류(소문자 movie·tv)
 * @param tmdbId         TMDB 작품 ID
 * @param title          한국어 제목
 * @param seasonNumber   드라마 시즌 번호. 영화는 null
 * @param ottServiceId   상태가 바뀐 서비스 ID
 * @param previousStatus 이전 상태. null이면 이번에 처음 확인된 작품
 * @param currentStatus  바뀐 뒤 상태
 */
public record CollectExecutionChangeResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "movie") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "496243") long tmdbId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "기생충") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer seasonNumber,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "6") long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) AvailabilityStatus previousStatus,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AvailabilityStatus currentStatus) {
}
