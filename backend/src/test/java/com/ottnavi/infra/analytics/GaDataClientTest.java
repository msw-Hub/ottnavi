package com.ottnavi.infra.analytics;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.ottnavi.infra.analytics.exception.AnalyticsCredentialsException;
import com.ottnavi.infra.analytics.exception.AnalyticsUnavailableException;
import org.junit.jupiter.api.Test;

/**
 * GA Data API 호출을 WireMock으로 확인한다(Docker 불필요).
 * 막으려는 사고: 날짜 범위·지표를 잘못 보내는 일, 응답 행을 엉뚱한 범위에 읽는 일,
 * 행이 빠진 범위를 0이 아닌 값으로 두는 일, 오류·잘못된 응답이 예외 없이 지나가는 일.
 */
class GaDataClientTest extends GaTestSupport {

	private static ResponseDefinitionBuilder okJson(String body) {
		return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
	}

	private static String row(int rangeIndex, long count) {
		return "{\"dimensionValues\":[{\"value\":\"date_range_" + rangeIndex + "\"}],"
				+ "\"metricValues\":[{\"value\":\"" + count + "\"}]}";
	}

	@Test
	void 정상_응답이면_날짜_범위별_숫자_4개로_읽는다() {
		// given
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(okJson(
				"{\"dimensionHeaders\":[{\"name\":\"dateRange\"}],"
						+ "\"metricHeaders\":[{\"name\":\"activeUsers\",\"type\":\"TYPE_INTEGER\"}],"
						+ "\"rows\":[" + row(3, 480) + "," + row(0, 12) + "," + row(2, 150) + "," + row(1, 30) + "],"
						+ "\"rowCount\":4,\"kind\":\"analyticsData#runReport\"}")));

		// when
		VisitorCounts counts = gaDataClient.fetchVisitorCounts();

		// then
		assertThat(counts).isEqualTo(new VisitorCounts(12, 30, 150, 480));
	}

	@Test
	void 요청에_날짜_범위_4개와_activeUsers가_들어가고_인증_헤더가_붙는다() {
		// given
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(okJson("{\"rows\":[]}")));

		// when
		gaDataClient.fetchVisitorCounts();

		// then
		String expectedBody = "{\"dateRanges\":["
				+ "{\"startDate\":\"today\",\"endDate\":\"today\"},"
				+ "{\"startDate\":\"yesterday\",\"endDate\":\"yesterday\"},"
				+ "{\"startDate\":\"6daysAgo\",\"endDate\":\"today\"},"
				+ "{\"startDate\":\"29daysAgo\",\"endDate\":\"today\"}],"
				+ "\"metrics\":[{\"name\":\"activeUsers\"}]}";
		wireMock.verify(1, postRequestedFor(urlPathEqualTo(RUN_REPORT_PATH))
				.withHeader("Authorization", equalTo("Bearer " + FakeTokenProvider.TOKEN))
				.withRequestBody(equalToJson(expectedBody)));
	}

	@Test
	void 일부_범위의_행이_없으면_그_범위는_0이다() {
		// given: GA는 값이 없는 범위의 행을 생략할 수 있다
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(okJson(
				"{\"dimensionHeaders\":[{\"name\":\"dateRange\"}],\"rows\":[" + row(2, 7) + "," + row(3, 9) + "]}")));

		// when
		VisitorCounts counts = gaDataClient.fetchVisitorCounts();

		// then
		assertThat(counts).isEqualTo(new VisitorCounts(0, 0, 7, 9));
	}

	@Test
	void 행이_아예_없으면_모두_0이다() {
		// given: 데이터가 없으면 rows 필드 자체가 빠진다
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(okJson("{\"kind\":\"analyticsData#runReport\"}")));

		// when
		VisitorCounts counts = gaDataClient.fetchVisitorCounts();

		// then
		assertThat(counts).isEqualTo(new VisitorCounts(0, 0, 0, 0));
	}

	@Test
	void 지표_값이_숫자가_아니면_예외다() {
		// given
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(okJson(
				"{\"rows\":[{\"dimensionValues\":[{\"value\":\"date_range_0\"}],\"metricValues\":[{\"value\":\"abc\"}]}]}")));

		// when & then
		assertThatThrownBy(() -> gaDataClient.fetchVisitorCounts()).isInstanceOf(AnalyticsUnavailableException.class);
	}

	@Test
	void 권한_없음_403이면_예외이고_메시지에_속성_ID와_본문이_없다() {
		// given
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(aResponse().withStatus(403)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"error\":{\"code\":403,\"message\":\"비밀 본문\",\"status\":\"PERMISSION_DENIED\"}}")));

		// when & then
		assertThatThrownBy(() -> gaDataClient.fetchVisitorCounts())
				.isInstanceOf(AnalyticsUnavailableException.class)
				.hasMessageContaining("403")
				.hasMessageNotContaining(PROPERTY_ID)
				.hasMessageNotContaining("비밀 본문");
	}

	@Test
	void 서버_오류_500이면_재시도_없이_한_번만_요청하고_예외다() {
		// given
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(aResponse().withStatus(500)));

		// when & then
		assertThatThrownBy(() -> gaDataClient.fetchVisitorCounts()).isInstanceOf(AnalyticsUnavailableException.class);
		wireMock.verify(1, postRequestedFor(urlPathEqualTo(RUN_REPORT_PATH)));
	}

	@Test
	void 토큰을_못_받으면_GA를_호출하지_않고_인증_예외다() {
		// given
		tokenProvider.failing = true;

		// when & then
		assertThatThrownBy(() -> gaDataClient.fetchVisitorCounts()).isInstanceOf(AnalyticsCredentialsException.class);
		wireMock.verify(0, postRequestedFor(urlPathEqualTo(RUN_REPORT_PATH)));
	}
}
