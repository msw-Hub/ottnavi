package com.ottnavi.wishlist.dto.request;

import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 찜 항목 변경 요청(계약 UpdateWishlistItemRequest). 보낸 필드만 바꾸며 하나 이상 필요하다.
 *
 * @param priority  바꿀 우선순위. 안 보내면(null) 그대로 둔다
 * @param isWatched true면 "다 봤음"(watchedAt 기록), false면 되돌림. 안 보내면(null) 그대로 둔다
 */
public record UpdateWishlistItemRequest(
		@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "WANT") WishlistPriority priority,
		@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "true") Boolean isWatched) {
}
