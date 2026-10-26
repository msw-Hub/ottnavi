package com.ottnavi.wishlist.dto.response;

import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.service.model.UpdatedWishlistItemView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 바뀐 찜 항목의 상태(계약 UpdatedWishlistItem).
 *
 * @param wishlistItemId 찜 항목 ID
 * @param priority       우선순위
 * @param watchedAt      "다 봤음" 시각. 안 봤으면 null
 */
public record UpdatedWishlistItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") long wishlistItemId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MUST") WishlistPriority priority,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant watchedAt) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static UpdatedWishlistItemResponse from(UpdatedWishlistItemView view) {
		return new UpdatedWishlistItemResponse(view.wishlistItemId(), view.priority(), view.watchedAt());
	}
}
