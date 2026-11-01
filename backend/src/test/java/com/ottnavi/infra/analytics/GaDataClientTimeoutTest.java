package com.ottnavi.infra.analytics;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.analytics.exception.AnalyticsUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** GA 응답이 읽기 시간 제한을 넘기면 재시도 없이 한 번만 요청하고 예외로 포기하는지 확인한다 */
class GaDataClientTimeoutTest extends GaTestSupport {

	@Override
	protected String[] extraProperties() {
		return new String[] {"spring.cloud.openfeign.client.config.ga.readTimeout=300"};   // 읽기 시간 제한을 0.3초로 줄인다
	}

	@Test
	void 응답이_읽기_시간_제한보다_늦으면_한_번만_요청하고_예외다() {
		// given
		wireMock.stubFor(post(urlPathEqualTo(RUN_REPORT_PATH)).willReturn(aResponse()
				.withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"rows\":[]}")
				.withFixedDelay(1500)));

		// when
		long start = System.nanoTime();
		assertThatThrownBy(() -> gaDataClient.fetchVisitorCounts()).isInstanceOf(AnalyticsUnavailableException.class);
		long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

		// then
		wireMock.verify(1, postRequestedFor(urlPathEqualTo(RUN_REPORT_PATH)));
		assertThat(elapsedMs).isLessThan(1400);   // 지연(1.5초)이 끝나기 전에 포기했다 = 재시도 없이 시간 제한(0.3초)에서 끊었다
	}
}
