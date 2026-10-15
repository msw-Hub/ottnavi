package com.ottnavi.wishlist.dto.request;

import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 찜 추가 요청(계약 AddWishlistItemRequest).
 *
 * @param mediaType    작품 구분(movie/tv)
 * @param tmdbId       TMDB ID
 * @param seasonNumber 드라마(tv)는 찜할 시즌 번호(1부터), 영화(movie)는 null
 * @param priority     우선순위
 */
public record AddWishlistItemRequest(
		@NotNull(message = "작품 구분은 필수입니다.")
		@Pattern(regexp = "movie|tv", message = "movie 또는 tv여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "tv") String mediaType,
		@NotNull(message = "TMDB ID는 필수입니다.")
		@Min(value = 1, message = "1 이상이어야 합니다.")
		@Max(value = 2147483647, message = "2147483647 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "93405") Long tmdbId,
		@Min(value = 1, message = "시즌 번호는 1 이상이어야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1") Integer seasonNumber,
		@NotNull(message = "우선순위는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MUST") WishlistPriority priority) {
}
