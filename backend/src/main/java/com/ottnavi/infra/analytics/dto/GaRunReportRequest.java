package com.ottnavi.infra.analytics.dto;

import java.util.List;

/**
 * GA Data API runReport 요청 본문. 차원 없이 날짜 범위 여러 개와 지표만 보낸다.
 *
 * @param dateRanges 날짜 범위들(응답의 date_range_0, date_range_1 ... 순서와 같다)
 * @param metrics    지표들
 */
public record GaRunReportRequest(List<GaDateRange> dateRanges, List<GaMetric> metrics) {

	/** 날짜 범위. 날짜는 YYYY-MM-DD 또는 today·yesterday·NdaysAgo이며 상대 날짜는 GA 속성의 시간대 기준으로 계산된다 */
	public record GaDateRange(String startDate, String endDate) {
	}

	/** 지표 이름(예: activeUsers) */
	public record GaMetric(String name) {
	}
}
