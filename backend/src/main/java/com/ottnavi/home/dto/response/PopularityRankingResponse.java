package com.ottnavi.home.dto.response;

import com.ottnavi.home.domain.enums.PopularityPeriod;
import com.ottnavi.home.service.model.PopularityRankingView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 서비스(또는 전체)의 인기 순위(계약 PopularityRanking).
 *
 * @param period       순위 기간(TODAY/WEEK)
 * @param ottServiceId 요청한 서비스 ID. 전체 순위면 null
 * @param snapshotDate 사용한 가장 최근 스냅샷 날짜(데이터 기준일). 스냅샷이 없으면 null
 * @param daysCounted  평균에 쓴 스냅샷 날 수
 * @param notice       화면에 함께 적을 기준 안내 문구
 * @param items        인기도 높은 순위(없으면 빈 배열)
 */
public record PopularityRankingResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PopularityPeriod period,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1") Long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2026-10-18") LocalDate snapshotDate,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int daysCounted,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TMDB 인기도 기준, OTT 공식 순위 아님") String notice,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PopularityRankingItemResponse> items) {

	/** 순위 조회 결과를 응답으로 바꾼다 */
	public static PopularityRankingResponse from(PopularityRankingView view) {
		List<PopularityRankingItemResponse> items = new ArrayList<>();
		for (PopularityRankingView.Item item : view.items()) {
			items.add(new PopularityRankingItemResponse(item.rank(),
					com.ottnavi.catalog.dto.response.TitleSearchItemResponse.from(item.title())));
		}
		return new PopularityRankingResponse(view.period(), view.ottServiceId(), view.snapshotDate(), view.daysCounted(),
				view.notice(), items);
	}
}
