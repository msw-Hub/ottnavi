package com.ottnavi.home.dto.response;

import com.ottnavi.catalog.dto.response.TitleSearchItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인기 순위의 한 줄(계약 PopularityRankingItem).
 *
 * @param rank  순위(1부터)
 * @param title 작품 카드. 우리 DB에 없는 작품이면 availabilities가 빈 배열이다
 */
public record PopularityRankingItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int rank,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) TitleSearchItemResponse title) {
}
