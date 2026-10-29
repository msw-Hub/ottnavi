package com.ottnavi.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.admin.service.model.SystemMetricsView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 지표 측정을 임시 파일 경로로 확인한다.
 * 막으려는 사고: 파일이 없는 환경(Windows 로컬 등)에서 예외가 나는 일, cgroup v2가 없을 때 v1을 못 읽는 일.
 */
class SystemMetricsServiceTest {

	@TempDir
	Path dir;

	@Test
	void 파일이_하나도_없으면_프로세스와_컨테이너는_null이고_JVM_값은_채워진다() {
		// given
		SystemMetricsService service = serviceWithFiles(null, null, null, null, null);

		// when
		SystemMetricsView view = service.measure();

		// then
		assertThat(view.process().rssBytes()).isNull();
		assertThat(view.container().memoryUsedBytes()).isNull();
		assertThat(view.container().memoryLimitBytes()).isNull();
		assertThat(view.jvm().heap().used()).isPositive();
		assertThat(view.jvm().memoryPools()).isNotEmpty();
		assertThat(view.jvm().threads().live()).isPositive();
		assertThat(view.measuredAt()).isNotNull();
	}

	@Test
	void cgroup_v2가_있으면_v2_값을_쓰고_max는_한도_null이다() throws IOException {
		// given
		SystemMetricsService service = serviceWithFiles(null, "700\n", "max\n", "111\n", "222\n");

		// when
		SystemMetricsView view = service.measure();

		// then
		assertThat(view.container().memoryUsedBytes()).isEqualTo(700L);
		assertThat(view.container().memoryLimitBytes()).isNull();
	}

	@Test
	void cgroup_v2가_없으면_v1_값을_쓴다() throws IOException {
		// given
		SystemMetricsService service = serviceWithFiles(null, null, null, "111\n", "222\n");

		// when
		SystemMetricsView view = service.measure();

		// then
		assertThat(view.container().memoryUsedBytes()).isEqualTo(111L);
		assertThat(view.container().memoryLimitBytes()).isEqualTo(222L);
	}

	@Test
	void proc_status가_있으면_프로세스_메모리를_읽는다() throws IOException {
		// given
		SystemMetricsService service = serviceWithFiles("VmRSS:\t 2 kB\nVmHWM:\t 3 kB\n", null, null, null, null);

		// when
		SystemMetricsView view = service.measure();

		// then
		assertThat(view.process().rssBytes()).isEqualTo(2048L);
		assertThat(view.process().peakRssBytes()).isEqualTo(3072L);
	}

	/** 내용이 null이면 파일을 만들지 않는다(없는 파일) */
	private SystemMetricsService serviceWithFiles(String proc, String v2Current, String v2Max, String v1Usage,
			String v1Limit) {
		return new SystemMetricsService(write("proc", proc), write("v2cur", v2Current), write("v2max", v2Max),
				write("v1use", v1Usage), write("v1lim", v1Limit));
	}

	private Path write(String name, String content) {
		Path path = dir.resolve(name);
		if (content == null) {
			return path;
		}
		try {
			Files.writeString(path, content);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return path;
	}
}
