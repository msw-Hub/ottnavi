package com.ottnavi.global.config;

import io.micrometer.observation.ObservationFilter;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 요청 추적(Tracing) 설정이다. 스위치는 환경 변수 TRACING_ENABLED(기본 false)이고
 * application.yml에서 management.opentelemetry.enabled와 management.tracing.export.enabled에 연결돼 있다.
 */
@Configuration
public class TracingConfig {

	/**
	 * 추적이 꺼져 있으면 아무것도 기록하지 않는 Tracer를 등록한다.
	 *
	 * 이유: 스위치를 꺼도 Boot가 기본으로 OTel Tracer를 만들고, 그러면 요청마다 span 객체가 만들어졌다가 버려진다.
	 * 우리가 먼저 Tracer를 등록하면 Boot의 OTel 브리지 Tracer는 (ConditionalOnMissingBean이라) 물러나고,
	 * 관측 핸들러는 이 no-op Tracer를 쓰므로 꺼짐 상태의 요청 처리가 추적을 넣기 전과 사실상 같아진다.
	 */
	@Bean
	@ConditionalOnBooleanProperty(name = "management.opentelemetry.enabled", havingValue = false)
	public Tracer noopTracer() {
		return Tracer.NOOP;
	}

	/**
	 * 추적이 켜졌을 때만 개인정보 필터를 등록한다. Boot가 ObservationFilter 빈을 ObservationRegistry에 자동으로 붙인다.
	 * 꺼짐이면 빈 자체가 없어 요청 처리에 끼어들지 않는다.
	 */
	@Bean
	@ConditionalOnBooleanProperty(name = "management.opentelemetry.enabled")
	public ObservationFilter tracingPrivacyFilter() {
		return new TracingPrivacyFilter();
	}

	/**
	 * 추적이 켜졌을 때만, Boot가 만든 span 내보내기를 예외 상세 제거 래퍼로 감싼다.
	 * BeanPostProcessor라 static 메서드로 등록한다(설정 클래스보다 먼저 만들어져야 해서).
	 */
	@Bean
	@ConditionalOnBooleanProperty(name = "management.opentelemetry.enabled")
	public static BeanPostProcessor spanExporterSanitizer() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				if (bean instanceof SpanExporter && !(bean instanceof SanitizingSpanExporter)) {
					return new SanitizingSpanExporter((SpanExporter) bean);
				}
				return bean;
			}
		};
	}
}
