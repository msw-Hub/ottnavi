package com.ottnavi.plan.dto.response;

import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 3개월 안에 배정하지 못한 작품 하나(계약 UnscheduledItem).
 *
 * @param planItemId   플랜 항목 ID
 * @param mediaType    작품 구분(movie/tv)
 * @param tmdbId       TMDB ID
 * @param title        한국어 제목
 * @param seasonNumber 드라마 시즌 번호. 영화는 null
 * @param priority     우선순위
 * @param reason       배정하지 못한 이유(BUDGET·TIME·UNKNOWN·NO_PROVIDER)
 */
public record UnscheduledItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "502") long planItemId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "movie") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "550") long tmdbId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "파이트 클럽") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer seasonNumber,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "WANT") WishlistPriority priority,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "BUDGET") UnitReason reason) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static UnscheduledItemResponse from(PlanDraftView.Unscheduled item) {
		return new UnscheduledItemResponse(item.planItemId(), item.mediaType().toApiValue(), item.tmdbId(),
				item.title(), item.seasonNumber(), WishlistPriority.valueOf(item.priority().name()), item.reason());
	}
}
