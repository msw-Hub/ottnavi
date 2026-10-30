package com.ottnavi.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 메모리 게이지 등록을 확인한다.
 * 막으려는 사고: 파일이 없는 환경에서 게이지가 예외를 던져 지표 수집이나 기동이 깨지는 일, 값이 바이트가 아닌 단위로 나오는 일.
 */
class SystemMemoryMetricsTest {

	@TempDir
	Path dir;

	@Test
	void 파일을_읽을_수_없으면_게이지는_예외_없이_NaN이다() {
		// given
		SystemMetricsService service = new SystemMetricsService(dir.resolve("없음1"), dir.resolve("없음2"),
				dir.resolve("없음3"), dir.resolve("없음4"), dir.resolve("없음5"));
		SimpleMeterRegistry registry = new SimpleMeterRegistry();

		// when
		new SystemMemoryMetrics(service).bindTo(registry);

		// then
		Gauge rss = registry.find("ottnavi.process.rss").gauge();
		Gauge container = registry.find("ottnavi.container.memory.used").gauge();
		assertThat(rss.value()).isNaN();
		assertThat(container.value()).isNaN();
	}

	@Test
	void 파일이_있으면_게이지가_바이트_값을_돌려준다() throws IOException {
		// given
		Path status = dir.resolve("status");
		Files.writeString(status, "VmRSS:\t   2048 kB\n");
		Path current = dir.resolve("current");
		Files.writeString(current, "700\n");
		SystemMetricsService service = new SystemMetricsService(status, current, dir.resolve("없음"),
				dir.resolve("없음1"), dir.resolve("없음2"));
		SimpleMeterRegistry registry = new SimpleMeterRegistry();

		// when
		new SystemMemoryMetrics(service).bindTo(registry);

		// then
		assertThat(registry.find("ottnavi.process.rss").gauge().value()).isEqualTo(2048.0 * 1024);
		assertThat(registry.find("ottnavi.container.memory.used").gauge().value()).isEqualTo(700.0);
		assertThat(registry.find("ottnavi.process.rss").gauge().getId().getBaseUnit()).isEqualTo("bytes");
	}
}
