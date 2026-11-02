package com.ottnavi.global.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * 추적 개인정보 규칙 검증: 실제 설정으로 추적을 켜고 요청 하나를 처리한 뒤,
 * 전송된 OTLP 본문(가짜 서버 WireMock이 받는다)에 경로 템플릿만 있고 쿼리·IP·UA·인증값·이메일이 없는지 본다.
 * 실제 Grafana는 부르지 않는다. DB·Redis 없이 자동 구성만 올리므로 Docker가 필요 없다.
 */
class TracingPrivacyTest {

	private static final String FAKE_TRACES_AUTH = "Basic fake-trace-value";   // 명백히 가짜인 테스트용 값
	private static final String TEMPLATE = "/api/public/titles/{mediaType}/{tmdbId}";
	private static final String REAL_PATH = "/api/public/titles/movie/603";

	private WireMockServer wireMock;

	@BeforeEach
	void startWireMock() {
		wireMock = new WireMockServer(wireMockConfig().dynamicPort());
		wireMock.start();
		wireMock.stubFor(post(urlPathEqualTo("/v1/traces")).willReturn(aResponse().withStatus(200)));
	}

	@AfterEach
	void stopWireMock() {
		wireMock.stop();
	}

	private ApplicationContextRunner runner() {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withConfiguration(AutoConfigurations.of(
						MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class,
						ObservationAutoConfiguration.class,
						OpenTelemetrySdkAutoConfiguration.class, OpenTelemetryTracingAutoConfiguration.class,
						OtlpTracingAutoConfiguration.class, NoopTracerAutoConfiguration.class,
						MicrometerTracingAutoConfiguration.class))
				.withUserConfiguration(TracingConfig.class)
				.withPropertyValues(
						"spring.profiles.active=observability",
						"GRAFANA_OTLP_ENDPOINT=http://127.0.0.1:1/v1/metrics",
						"GRAFANA_OTLP_AUTH=Basic fake-test-value",
						"TRACING_ENABLED=true",
						"TRACING_SAMPLING_PROBABILITY=1.0",   // 시험에서는 모두 기록한다
						"GRAFANA_OTLP_TRACES_ENDPOINT=" + wireMock.baseUrl() + "/v1/traces",
						"GRAFANA_OTLP_TRACES_AUTH=" + FAKE_TRACES_AUTH,
						"management.opentelemetry.tracing.export.schedule-delay=100ms");   // 시험이 오래 걸리지 않게 전송 주기를 줄인다
	}

	/** 개인정보가 가득 실린 요청 하나를 서버 관측 필터를 거쳐 처리한다. 컨트롤러 대신 예외를 던져 오류 경로도 함께 본다. */
	private void handleSensitiveRequest(AssertableApplicationContext context, boolean fail)
			throws ServletException, IOException {
		ObservationRegistry registry = context.getBean(ObservationRegistry.class);
		ServerHttpObservationFilter filter = new ServerHttpObservationFilter(registry);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", REAL_PATH);
		request.setQueryString("q=secret-query-word&email=hong@example.com");
		request.setRemoteAddr("203.0.113.77");
		request.addHeader("User-Agent", "SecretAgent/9.9");
		request.addHeader("X-Forwarded-For", "198.51.100.5");
		request.addHeader("Authorization", "Bearer secret-access-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain chain = (req, res) -> {
			// 실제 요청에서는 DispatcherServlet이 핸들러를 찾은 뒤 경로 템플릿을 관측 컨텍스트에 넣는다
			ServerRequestObservationContext observationContext = ServerHttpObservationFilter
					.findObservationContext(request).orElseThrow();
			observationContext.setPathPattern(TEMPLATE);
			if (fail) {
				throw new IllegalStateException("실패 사유에 hong@example.com 이 섞였다");
			}
		};
		try {
			filter.doFilter(request, response, chain);
		}
		catch (ServletException | RuntimeException expected) {
			// 예외가 요청 밖으로 나가는 경로(오류 기록)를 시험하려는 것이라 무시한다
		}
		context.getBean(SdkTracerProvider.class).forceFlush().join(5, java.util.concurrent.TimeUnit.SECONDS);
	}

	private String waitForBody(int seconds) throws InterruptedException {
		long deadline = System.currentTimeMillis() + seconds * 1000L;
		List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/traces")));
		while (requests.isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(200);
			requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/traces")));
		}
		StringBuilder all = new StringBuilder();
		for (LoggedRequest request : requests) {
			all.append(new String(request.getBody(), StandardCharsets.UTF_8));
		}
		return all.toString();
	}

	@Test
	void 추적을_켜면_가짜_서버로_span이_오고_추적용_Authorization_헤더가_붙는다() {
		runner().run(context -> {
			// when
			handleSensitiveRequest(context, false);
			String body = waitForBody(10);

			// then
			assertThat(body).isNotEmpty();
			List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/traces")));
			assertThat(requests.get(0).getHeader("Authorization")).isEqualTo(FAKE_TRACES_AUTH);
		});
	}

	@Test
	void span에는_경로_템플릿만_있고_실제_경로가_없다() {
		runner().run(context -> {
			// when
			handleSensitiveRequest(context, false);
			String body = waitForBody(10);

			// then
			assertThat(body).contains(TEMPLATE);
			assertThat(body).doesNotContain(REAL_PATH);
		});
	}

	@Test
	void span에는_쿼리_문자열과_이메일이_없다() {
		runner().run(context -> {
			// when
			handleSensitiveRequest(context, false);
			String body = waitForBody(10);

			// then
			assertThat(body).contains(TEMPLATE);   // 본문이 압축·인코딩되지 않고 읽히는 상태에서 부재를 확인한다
			assertThat(body).doesNotContain("secret-query-word");
			assertThat(body).doesNotContain("hong@example.com");
			assertThat(body).doesNotContain("q=");
		});
	}

	@Test
	void span에는_클라이언트_IP와_사용자_에이전트와_인증값이_없다() {
		runner().run(context -> {
			// when
			handleSensitiveRequest(context, false);
			String body = waitForBody(10);

			// then
			assertThat(body).contains(TEMPLATE);   // 본문이 압축·인코딩되지 않고 읽히는 상태에서 부재를 확인한다
			assertThat(body).doesNotContain("203.0.113.77");
			assertThat(body).doesNotContain("198.51.100.5");
			assertThat(body).doesNotContain("SecretAgent");
			assertThat(body).doesNotContain("secret-access-token");
			assertThat(body).doesNotContain("Bearer");
		});
	}

	@Test
	void 예외가_나도_span에는_예외_메시지와_스택이_없고_종류만_남는다() {
		runner().run(context -> {
			// when
			handleSensitiveRequest(context, true);
			String body = waitForBody(10);

			// then
			assertThat(body).isNotEmpty();
			assertThat(body).doesNotContain("hong@example.com");
			assertThat(body).doesNotContain("실패 사유");
			assertThat(body).doesNotContain("TracingPrivacyTest");   // 스택에 찍히는 테스트 클래스 이름
			assertThat(body).contains("IllegalStateException");
			assertThat(body).doesNotContain("exception.stacktrace");
		});
	}
}
