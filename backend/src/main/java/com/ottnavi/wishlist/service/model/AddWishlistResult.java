package com.ottnavi.wishlist.service.model;

/** 찜 추가 결과. isCreated가 true면 새로 만든 것(201), false면 이미 찜한 항목(200)이다 */
public record AddWishlistResult(
		long wishlistItemId,
		boolean isCreated) {
}
