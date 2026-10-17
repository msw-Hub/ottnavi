package com.ottnavi.home.dto.response;

import com.ottnavi.catalog.dto.response.TitleSearchItemResponse;
import com.ottnavi.home.service.model.WishlistedTitleListView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 찜 많은 작품 목록(계약 WishlistedTitleList).
 *
 * @param minUserCount 목록에 넣는 최소 찜 사용자 수(이보다 적은 작품은 뺐다)
 * @param dataAsOf     제공 상태의 데이터 기준일(KST)
 * @param items        찜 사용자 수 많은 순(없으면 빈 배열)
 */
public record WishlistedTitleListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int minUserCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-18") LocalDate dataAsOf,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<WishlistedTitleResponse> items) {

	/** 조회 결과를 응답으로 바꾼다 */
	public static WishlistedTitleListResponse from(WishlistedTitleListView view) {
		List<WishlistedTitleResponse> items = new ArrayList<>();
		for (WishlistedTitleListView.Item item : view.items()) {
			items.add(new WishlistedTitleResponse(TitleSearchItemResponse.from(item.title()),
					Math.toIntExact(item.wishlistUserCount())));
		}
		return new WishlistedTitleListResponse(view.minUserCount(), view.dataAsOf(), items);
	}
}
