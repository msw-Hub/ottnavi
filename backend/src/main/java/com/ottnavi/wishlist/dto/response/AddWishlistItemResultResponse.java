package com.ottnavi.wishlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 찜 추가 결과(계약 AddWishlistItemResult). 이미 찜한 작품(시즌)이면 기존 항목 ID다.
 *
 * @param wishlistItemId 찜 항목 ID
 */
public record AddWishlistItemResultResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") long wishlistItemId) {
}
