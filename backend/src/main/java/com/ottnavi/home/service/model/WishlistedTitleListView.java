package com.ottnavi.home.service.model;

import com.ottnavi.catalog.service.model.TitleSearchItemView;
import java.time.LocalDate;
import java.util.List;

/**
 * 찜 많은 작품 목록.
 *
 * @param minUserCount 목록에 넣는 최소 찜 사용자 수(이보다 적은 작품은 뺐다)
 * @param dataAsOf     제공 상태의 데이터 기준일(KST)
 */
public record WishlistedTitleListView(int minUserCount, LocalDate dataAsOf, List<Item> items) {

	/** 작품 카드와 그 작품을 찜한 사용자 수("다 봤음" 제외, 작품 단위) */
	public record Item(TitleSearchItemView title, long wishlistUserCount) {
	}
}
