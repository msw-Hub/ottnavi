package com.ottnavi.infra.analytics.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GA Data API runReport 응답에서 쓰는 부분. 날짜 범위가 둘 이상이면 GA가 dateRange 차원을 자동으로 더하고
 * 행의 값은 date_range_0, date_range_1 ... 로 온다. 값이 없는 범위는 행 자체가 빠질 수 있다. 모르는 필드는 버린다.
 *
 * @param dimensionHeaders 차원 머리글(dateRange의 위치를 찾는 데 쓴다)
 * @param rows             결과 행. 데이터가 없으면 필드 자체가 없을 수 있다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GaRunReportResponse(List<Header> dimensionHeaders, List<Row> rows) {

	/** 머리글 한 칸 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Header(String name) {
	}

	/** 결과 행 한 줄: 차원 값들과 지표 값들 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Row(List<Value> dimensionValues, List<Value> metricValues) {
	}

	/** 값 한 칸(숫자 지표도 문자열로 온다) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Value(String value) {
	}
}
