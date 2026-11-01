package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 추적(Tracing) 스위치 검증: 실제 application.yml·application-observability.yml을 읽고,
 * TRACING_ENABLED가 꺼짐(기본)이면 내보내기 객체가 없고, 켜면 만들어지는지 확인한다.
 * DB·Redis 없이 자동 구성만 올리므로 Docker가 필요 없고, 외부로 전송하지 않는다(주소는 닫힌 로컬 포트).
 */
class TracingToggleTest {

	private ApplicationContextRunner runner(String... extraProperties) {
		ApplicationContextRunner base = new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())   // 실제 YAML 설정을 읽는다
				.withConfiguration(AutoConfigurations.of(
						MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class,
						OtlpMetricsExportAutoConfiguration.class,
						OpenTelemetrySdkAutoConfiguration.class, OpenTelemetryTracingAutoConfiguration.class,
						OtlpTracingAutoConfiguration.class, NoopTracerAutoConfiguration.class))
				.withUserConfiguration(ObservabilityConfig.class, TracingConfig.class)
				.withPropertyValues(
						"GRAFANA_OTLP_ENDPOINT=http://127.0.0.1:1/v1/metrics",
						"GRAFANA_OTLP_AUTH=Basic fake-test-value",
						"GRAFANA_OTLP_TRACES_ENDPOINT=http://127.0.0.1:1/v1/traces",
						"GRAFANA_OTLP_TRACES_AUTH=Basic fake-test-value",
						"spring.profiles.active=observability");
		return base.withPropertyValues(extraProperties);
	}

	@Test
	void 기본값에서는_내보내기와_추적_공급자가_만들어지지_않는다() {
		runner().run(context -> {
			// then
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(SpanExporter.class);
			assertThat(context).doesNotHaveBean(SdkTracerProvider.class);
		});
	}

	@Test
	void 꺼짐이면_주소와_인증이_있어도_내보내기가_없다() {
		runner("TRACING_ENABLED=false").run(context -> {
			// then
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(SpanExporter.class);
			assertThat(context).doesNotHaveBean(SdkTracerProvider.class);
		});
	}

	@Test
	void 스위치가_빈_문자열로_정의되면_켜지지_않고_기동이_실패해_잘못된_설정을_바로_알린다() {
		// 빈 값은 기본값(false)으로 대체되지 않고 boolean 바인딩에서 실패한다. 조용히 켜지는 일은 없다
		runner("TRACING_ENABLED=").run(context -> {
			// then
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("management.opentelemetry.enabled");
		});
	}

	@Test
	void observability_프로필이_없어도_기본은_꺼짐이다() {
		runner("spring.profiles.active=local").run(context -> {
			// then
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(SpanExporter.class);
			assertThat(context.getBean(Tracer.class)).isSameAs(Tracer.NOOP);
		});
	}

	@Test
	void 꺼짐이면_Tracer는_아무것도_기록하지_않는_no_op이다() {
		runner().run(context -> {
			// then
			Tracer tracer = context.getBean(Tracer.class);
			assertThat(tracer).isSameAs(Tracer.NOOP);
			assertThat(tracer.nextSpan().context().traceId()).isEmpty();
		});
	}

	@Test
	void 켜면_내보내기와_추적_공급자가_만들어지고_표본_비율_기본은_0점1이다() {
		runner("TRACING_ENABLED=true").run(context -> {
			// then
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(SpanExporter.class);
			assertThat(context).hasSingleBean(SdkTracerProvider.class);
			assertThat(context.getEnvironment().getProperty("management.tracing.sampling.probability"))
					.isEqualTo("0.1");
		});
	}

	@Test
	void 켜면_Tracer가_실제로_추적_번호를_만든다() {
		runner("TRACING_ENABLED=true").run(context -> {
			// then
			Tracer tracer = context.getBean(Tracer.class);
			assertThat(tracer).isNotSameAs(Tracer.NOOP);
			assertThat(tracer.nextSpan().context().traceId()).isNotEmpty();
		});
	}

	@Test
	void 켰는데_전송_주소가_비어_있으면_기동이_실패해_잘못된_설정을_바로_알린다() {
		runner("TRACING_ENABLED=true", "GRAFANA_OTLP_TRACES_ENDPOINT=").run(context -> {
			// then
			assertThat(context).hasFailed();
		});
	}

	@Test
	void observability_프로필_없이_켜면_주소가_없어_내보내기_없이_기동만_된다() {
		runner("spring.profiles.active=local", "TRACING_ENABLED=true").run(context -> {
			// then
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(SpanExporter.class);
		});
	}

	@Test
	void 표본_비율은_환경_변수로_바꾼다() {
		runner("TRACING_ENABLED=true", "TRACING_SAMPLING_PROBABILITY=0.5").run(context -> {
			// then
			assertThat(context.getEnvironment().getProperty("management.tracing.sampling.probability"))
					.isEqualTo("0.5");
		});
	}

	@Test
	void 추적을_켜도_OTLP_지표_레지스트리는_그대로_만들어진다() {
		runner("TRACING_ENABLED=true").run(context -> {
			// then
			assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
		});
	}

	@Test
	void 추적을_꺼도_OTLP_지표_레지스트리는_그대로_만들어진다() {
		runner().run(context -> {
			// then
			assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
		});
	}
}
