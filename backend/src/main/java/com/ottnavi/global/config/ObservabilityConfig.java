package com.ottnavi.global.config;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * observability 프로필에서만 켜지는 관측 설정이다.
 * OTLP 레지스트리에만 허용 목록 필터를 붙여, 다른 레지스트리(메모리 집계 등)와 /actuator 지표는 그대로 둔다.
 */
@Configuration
@Profile("observability")
public class ObservabilityConfig {

	/** OtlpMeterRegistry에만 허용 목록 필터를 붙인다. 라이브러리가 요구하는 커스터마이저 인터페이스라 익명 클래스로 쓴다. */
	@Bean
	public MeterRegistryCustomizer<OtlpMeterRegistry> otlpAllowFilterCustomizer() {
		return new MeterRegistryCustomizer<OtlpMeterRegistry>() {
			@Override
			public void customize(OtlpMeterRegistry registry) {
				registry.config().meterFilter(new OtlpMetricsAllowFilter());
			}
		};
	}
}
