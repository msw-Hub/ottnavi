package com.ottnavi.wishlist.service.model;

import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import java.time.Instant;

/** 바뀐 찜 항목의 상태(계약 UpdatedWishlistItem) */
public record UpdatedWishlistItemView(
		long wishlistItemId,
		WishlistPriority priority,
		Instant watchedAt) { // 안 봤으면 null
}
