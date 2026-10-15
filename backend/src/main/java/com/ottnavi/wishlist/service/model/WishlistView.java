package com.ottnavi.wishlist.service.model;

import java.time.LocalDate;
import java.util.List;

/** 내 찜 목록 전체(계약 WishlistItemList) */
public record WishlistView(
		List<WishlistItemView> items,
		int activeItemCount,     // 상한에 세는 찜 개수("다 봤음" 제외)
		int maxActiveItemCount,  // 찜 상한
		LocalDate dataAsOf) {    // 제공 상태 데이터 기준일(KST)
}
