package com.ottnavi.wishlist.service.model;

import com.ottnavi.wishlist.domain.enums.WishlistPriority;

/**
 * 플랜 계산에 쓰는 찜 한 건("다 봤음"이 아닌 것만). 시청 단위 정보는 catalog 서비스가 따로 채운다.
 *
 * @param wishlistItemId 찜 ID
 * @param watchUnitId    찜한 시청 단위 ID
 * @param priority       우선순위
 */
public record ActiveWishlistEntry(
		long wishlistItemId,
		long watchUnitId,
		WishlistPriority priority) {
}
