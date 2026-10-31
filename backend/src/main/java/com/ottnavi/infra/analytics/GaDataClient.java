package com.ottnavi.infra.analytics;

import com.ottnavi.infra.analytics.dto.GaRunReportRequest;
import com.ottnavi.infra.analytics.dto.GaRunReportRequest.GaDateRange;
import com.ottnavi.infra.analytics.dto.GaRunReportRequest.GaMetric;
import com.ottnavi.infra.analytics.dto.GaRunReportResponse;
import com.ottnavi.infra.analytics.dto.GaRunReportResponse.Header;
import com.ottnavi.infra.analytics.dto.GaRunReportResponse.Row;
import com.ottnavi.infra.analytics.exception.AnalyticsCredentialsException;
import com.ottnavi.infra.analytics.exception.AnalyticsUnavailableException;
import feign.FeignException;
import feign.codec.DecodeException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 동의한 방문자 수를 GA Data API에서 읽는 창구.
 * 요청 한 번에 날짜 범위 4개(오늘·어제·최근 7일·최근 30일)를 보내 GA 일일 토큰 한도(표준 속성 20만)를 아낀다.
 * 상대 날짜(today·yesterday·NdaysAgo)는 GA가 속성의 시간대(서울) 기준으로 풀어 주므로 서버가 날짜를 계산하지 않는다.
 * 실패는 모두 AnalyticsUnavailableException으로 바뀐다. 메시지에는 속성 ID·토큰·응답 본문을 넣지 않는다.
 * 외부 호출이므로 트랜잭션 밖에서 부른다(이 기능은 DB를 쓰지 않는다).
 */
@RequiredArgsConstructor
@Component
public class GaDataClient {

	private static final String ACTIVE_USERS = "activeUsers";
	private static final String DATE_RANGE_DIMENSION = "dateRange"; // 날짜 범위가 둘 이상이면 GA가 자동으로 더하는 차원
	private static final String DATE_RANGE_PREFIX = "date_range_"; // 값은 date_range_0, date_range_1 ...

	private final GaDataApi api;
	private final AnalyticsProperties props;

	/** 오늘·어제·최근 7일·최근 30일(오늘 포함)의 activeUsers를 한 번에 읽는다. 행이 없는 범위는 0이다 */
	public VisitorCounts fetchVisitorCounts() {
		// 1. 날짜 범위 4개와 지표 1개를 한 요청으로 보낸다(순서가 응답의 date_range_0~3과 같다)
		GaRunReportRequest request = new GaRunReportRequest(
				List.of(new GaDateRange("today", "today"),
						new GaDateRange("yesterday", "yesterday"),
						new GaDateRange("6daysAgo", "today"),
						new GaDateRange("29daysAgo", "today")),
				List.of(new GaMetric(ACTIVE_USERS)));

		// 2. 호출하고, 실패는 고정 문구 예외로 바꾼다(인증·HTTP 오류는 오류 디코더·토큰 공급자가 이미 같은 계열 예외로 던진다)
		GaRunReportResponse response;
		try {
			response = api.runReport(props.propertyId(), request);
		} catch (DecodeException e) {
			throw new AnalyticsUnavailableException("GA 응답을 해석하지 못했다");
		} catch (FeignException e) {
			throw new AnalyticsUnavailableException("GA에 연결하지 못했거나 응답 시간을 넘겼다");
		}

		// 3. 응답 행을 날짜 범위별 숫자 4개로 바꾼다
		return toCounts(response);
	}

	/** 행의 dateRange 값(date_range_N)으로 N번째 범위의 값을 모은다. 응답에 없는 범위는 0이다 */
	private VisitorCounts toCounts(GaRunReportResponse response) {
		// 1. 응답이 비었거나 행이 없으면 전부 0
		long[] values = new long[4];
		if (response == null || response.rows() == null) {
			return new VisitorCounts(0, 0, 0, 0);
		}

		// 2. dateRange 차원이 몇 번째 칸인지 머리글에서 찾는다(없으면 첫 칸으로 본다)
		int dimensionIndex = 0;
		if (response.dimensionHeaders() != null) {
			for (int i = 0; i < response.dimensionHeaders().size(); i++) {
				Header header = response.dimensionHeaders().get(i);
				if (header != null && DATE_RANGE_DIMENSION.equals(header.name())) {
					dimensionIndex = i;
				}
			}
		}

		// 3. 행마다 범위 번호와 지표 값을 읽는다
		for (Row row : response.rows()) {
			if (row == null || row.dimensionValues() == null || row.metricValues() == null
					|| row.dimensionValues().size() <= dimensionIndex || row.metricValues().isEmpty()) {
				throw new AnalyticsUnavailableException("GA 응답의 행 구조가 예상과 다르다");
			}
			String label = row.dimensionValues().get(dimensionIndex).value();
			int rangeIndex = parseRangeIndex(label);
			long count = parseCount(row.metricValues().get(0).value());
			if (rangeIndex >= 0 && rangeIndex < values.length) {
				values[rangeIndex] = count;
			}
		}
		return new VisitorCounts(values[0], values[1], values[2], values[3]);
	}

	private int parseRangeIndex(String label) {
		if (label == null || !label.startsWith(DATE_RANGE_PREFIX)) {
			throw new AnalyticsUnavailableException("GA 응답의 날짜 범위 표시가 예상과 다르다");
		}
		try {
			return Integer.parseInt(label.substring(DATE_RANGE_PREFIX.length()));
		} catch (NumberFormatException e) {
			throw new AnalyticsUnavailableException("GA 응답의 날짜 범위 표시가 예상과 다르다");
		}
	}

	private long parseCount(String text) {
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			throw new AnalyticsUnavailableException("GA 응답의 지표 값이 숫자가 아니다");
		}
	}
}
