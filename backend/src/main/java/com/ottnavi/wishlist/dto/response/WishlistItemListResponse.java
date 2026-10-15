package com.ottnavi.wishlist.dto.response;

import com.ottnavi.wishlist.service.model.WishlistItemView;
import com.ottnavi.wishlist.service.model.WishlistView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 내 찜 목록(계약 WishlistItemList). 최근에 찜한 순이며 "다 봤음" 항목도 포함한다.
 *
 * @param items            찜 항목들. 없으면 빈 배열
 * @param activeItemCount  상한에 세는 찜 개수("다 봤음" 제외)
 * @param maxActiveItemCount 찜 상한
 * @param dataAsOf         제공 상태 데이터 기준일
 */
public record WishlistItemListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<WishlistItemResponse> items,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int activeItemCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int maxActiveItemCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-08") LocalDate dataAsOf) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static WishlistItemListResponse from(WishlistView view) {
		List<WishlistItemResponse> items = new ArrayList<>();
		for (WishlistItemView item : view.items()) {
			items.add(WishlistItemResponse.from(item));
		}
		return new WishlistItemListResponse(items, view.activeItemCount(), view.maxActiveItemCount(), view.dataAsOf());
	}
}
