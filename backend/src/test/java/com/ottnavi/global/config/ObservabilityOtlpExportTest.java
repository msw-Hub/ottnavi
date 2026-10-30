package com.ottnavi.global.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * OTLP 지표 전송 설정 검증: 실제 application.yml·application-observability.yml을 읽고, 전송 대상은 WireMock으로 대신한다.
 * DB·Redis 없이 지표 자동 구성만 올리므로 Docker가 필요 없다. 실제 Grafana는 부르지 않는다.
 */
class ObservabilityOtlpExportTest {

	private static final String FAKE_AUTH = "Basic fake-test-value";   // 명백히 가짜인 테스트용 값

	private WireMockServer wireMock;

	@BeforeEach
	void startWireMock() {
		wireMock = new WireMockServer(wireMockConfig().dynamicPort());
		wireMock.start();
		wireMock.stubFor(post(urlPathEqualTo("/v1/metrics")).willReturn(aResponse().withStatus(200)));
	}

	@AfterEach
	void stopWireMock() {
		wireMock.stop();
	}

	private ApplicationContextRunner runner(String... extraProperties) {
		ApplicationContextRunner base = new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())   // 실제 YAML 설정을 읽는다
				.withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
						CompositeMeterRegistryAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class))
				.withUserConfiguration(ObservabilityConfig.class)
				.withPropertyValues(
						"GRAFANA_OTLP_ENDPOINT=" + wireMock.baseUrl() + "/v1/metrics",
						"GRAFANA_OTLP_AUTH=" + FAKE_AUTH,
						"management.otlp.metrics.export.step=1s");   // 테스트가 오래 걸리지 않게 전송 주기를 줄인다
		return base.withPropertyValues(extraProperties);
	}

	private void registerSampleMeters(AssertableApplicationContext context) {
		MeterRegistry registry = context.getBean(MeterRegistry.class);
		registry.counter("ratelimit.rejected", "bucket", "public").increment();
		registry.counter("app.unlisted.metric", "result", "success").increment();   // 허용 목록에 없는 이름
		registry.counter("ratelimit.store_error", "ip", "203.0.113.1").increment();
	}

	private List<LoggedRequest> waitForRequests(int seconds) throws InterruptedException {
		long deadline = System.currentTimeMillis() + seconds * 1000L;
		while (System.currentTimeMillis() < deadline) {
			List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/metrics")));
			if (!requests.isEmpty()) {
				return requests;
			}
			Thread.sleep(200);
		}
		return wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/metrics")));
	}

	@Test
	void 프로필을_켜면_가짜_서버로_지표가_오고_Authorization_헤더가_붙는다() {
		runner("spring.profiles.active=observability").run(context -> {
			// given
			registerSampleMeters(context);

			// when
			List<LoggedRequest> requests = waitForRequests(10);

			// then
			assertThat(requests).isNotEmpty();
			assertThat(requests.get(0).getHeader("Authorization")).isEqualTo(FAKE_AUTH);
		});
	}

	@Test
	void 프로필을_끄면_지표_요청이_한_건도_가지_않는다() {
		runner().run(context -> {
			// given
			registerSampleMeters(context);

			// when
			List<LoggedRequest> requests = waitForRequests(4);

			// then
			assertThat(requests).isEmpty();
			assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
		});
	}

	@Test
	void 전송된_본문에는_허용_이름만_있고_거부된_이름과_개인정보_태그_값은_없다() {
		runner("spring.profiles.active=observability").run(context -> {
			// given
			registerSampleMeters(context);

			// when
			List<LoggedRequest> requests = waitForRequests(10);
			StringBuilder all = new StringBuilder();
			for (LoggedRequest request : requests) {
				all.append(new String(request.getBody(), StandardCharsets.ISO_8859_1));
			}
			String body = all.toString();

			// then
			assertThat(body).contains("ratelimit.rejected");
			assertThat(body).doesNotContain("app.unlisted.metric");
			assertThat(body).doesNotContain("ratelimit.store_error");   // ip 태그가 붙어 거부됨
			assertThat(body).doesNotContain("203.0.113.1");
		});
	}

	@Test
	void 전송_대상에_연결할_수_없어도_컨텍스트와_지표_기록은_멈추지_않는다() {
		runner("spring.profiles.active=observability",
				"GRAFANA_OTLP_ENDPOINT=http://127.0.0.1:1/v1/metrics").run(context -> {
			// given
			MeterRegistry registry = context.getBean(MeterRegistry.class);

			// when
			registry.counter("ratelimit.rejected", "bucket", "public").increment();
			Thread.sleep(2500);   // 전송 주기(1초)가 한 번 이상 지나게 한다

			// then
			assertThat(context).hasNotFailed();
			assertThat(registry.counter("ratelimit.rejected", "bucket", "public").count()).isEqualTo(1.0);
		});
	}
}
