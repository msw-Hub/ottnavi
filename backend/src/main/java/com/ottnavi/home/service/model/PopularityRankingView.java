package com.ottnavi.home.service.model;

import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.home.domain.enums.PopularityPeriod;
import java.time.LocalDate;
import java.util.List;

/**
 * 인기 순위 조회 결과.
 *
 * @param ottServiceId 요청한 서비스 ID. 전체 순위면 null
 * @param snapshotDate 사용한 가장 최근 스냅샷 날짜(데이터 기준일). 스냅샷이 없으면 null
 * @param daysCounted  평균에 쓴 스냅샷 날 수(TODAY는 1, 스냅샷이 없으면 0)
 * @param notice       화면에 함께 적을 기준 안내 문구
 */
public record PopularityRankingView(
		PopularityPeriod period,
		Long ottServiceId,
		LocalDate snapshotDate,
		int daysCounted,
		String notice,
		List<Item> items) {

	/** 순위 한 줄: 순위(1부터)와 작품 카드. 우리 DB에 없는 작품이면 카드의 제공 상태가 빈 목록이다 */
	public record Item(int rank, TitleSearchItemView title) {
	}
}
