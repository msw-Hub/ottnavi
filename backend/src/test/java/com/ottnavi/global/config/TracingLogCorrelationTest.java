package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 추적을 켜도 콘솔 로그에 추적 번호가 붙지 않는지 확인한다.
 * Boot는 logging.expect-correlation-id를 management.tracing.export.enabled에서 가져와 true면 로그 줄에 [traceId-spanId]를 넣는다.
 * EnvironmentPostProcessor는 SpringApplication으로만 돌기 때문에 빈 설정으로 애플리케이션을 띄워 환경 값을 본다.
 */
class TracingLogCorrelationTest {

	@Configuration
	static class EmptyConfig {
	}

	private String expectCorrelationId(String tracingEnabled) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(EmptyConfig.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "spring.profiles.active=local", "TRACING_ENABLED=" + tracingEnabled);
		try (ConfigurableApplicationContext context = builder.run()) {
			return context.getEnvironment().getProperty("logging.expect-correlation-id");
		}
	}

	@Test
	void 추적을_켜도_로그에_추적_번호를_넣지_않는다() {
		// when
		String value = expectCorrelationId("true");

		// then
		assertThat(value).isEqualTo("false");
	}

	@Test
	void 추적이_꺼져_있어도_로그에_추적_번호를_넣지_않는다() {
		// when
		String value = expectCorrelationId("false");

		// then
		assertThat(value).isEqualTo("false");
	}
}
