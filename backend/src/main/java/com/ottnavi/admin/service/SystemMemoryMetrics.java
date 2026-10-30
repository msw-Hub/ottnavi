package com.ottnavi.admin.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * 프로세스 RSS와 컨테이너 메모리 사용량을 Micrometer 게이지로 등록한다(모니터링 대시보드용).
 * 값은 SystemMetricsService가 /proc, cgroup 파일에서 읽는다. 수집 주기마다 작은 파일 두세 개만 읽는다.
 * 값을 읽을 수 없는 환경(Windows·로컬)에서는 게이지가 NaN을 돌려줘 예외 없이 값 없음으로 보인다.
 * 게이지 이름: ottnavi.process.rss, ottnavi.container.memory.used (단위 바이트).
 */
@Component
public class SystemMemoryMetrics implements MeterBinder {

	static final String PROCESS_RSS_NAME = "ottnavi.process.rss";
	static final String CONTAINER_MEMORY_USED_NAME = "ottnavi.container.memory.used";

	private final SystemMetricsService systemMetricsService;

	public SystemMemoryMetrics(SystemMetricsService systemMetricsService) {
		this.systemMetricsService = systemMetricsService;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		// 게이지는 라이브러리가 요구하는 메서드 참조로 값 공급자를 받는다
		Gauge.builder(PROCESS_RSS_NAME, systemMetricsService, SystemMetricsService::readProcessRssBytes)
				.description("프로세스가 실제로 쓰는 물리 메모리(/proc/self/status VmRSS)")
				.baseUnit("bytes")
				.register(registry);
		Gauge.builder(CONTAINER_MEMORY_USED_NAME, systemMetricsService, SystemMetricsService::readContainerMemoryUsedBytes)
				.description("컨테이너(cgroup) 메모리 사용량")
				.baseUnit("bytes")
				.register(registry);
	}
}
