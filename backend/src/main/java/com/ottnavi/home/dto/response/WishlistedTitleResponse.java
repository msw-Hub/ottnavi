package com.ottnavi.home.dto.response;

import com.ottnavi.catalog.dto.response.TitleSearchItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 찜이 많은 작품 한 줄(계약 WishlistedTitle).
 *
 * @param title             작품 카드
 * @param wishlistUserCount 이 작품을 찜한 사용자 수("다 봤음" 제외, 작품 단위)
 */
public record WishlistedTitleResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) TitleSearchItemResponse title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") int wishlistUserCount) {
}
