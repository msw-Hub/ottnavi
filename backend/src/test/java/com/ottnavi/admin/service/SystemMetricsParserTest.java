package com.ottnavi.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.admin.service.model.SystemMetricsView.ProcessView;
import org.junit.jupiter.api.Test;

/**
 * /proc·cgroup 글자 해석 규칙을 샘플 문자열로 확인한다.
 * 막으려는 사고: kB를 바이트로 바꾸지 않아 1024배 작게 보이는 일, "max"나 깨진 값에서 예외가 나 지표 조회가 500이 되는 일.
 */
class SystemMetricsParserTest {

	private static final String PROC_STATUS = """
			Name:\tjava
			VmPeak:\t 3000000 kB
			VmHWM:\t  600000 kB
			VmRSS:\t  520000 kB
			RssAnon:\t  480000 kB
			RssFile:\t   40000 kB
			Threads:\t45
			""";

	@Test
	void proc_status의_kB_값을_바이트로_바꾼다() {
		// when
		ProcessView result = SystemMetricsParser.parseProcStatus(PROC_STATUS);

		// then
		assertThat(result.rssBytes()).isEqualTo(520000L * 1024);
		assertThat(result.peakRssBytes()).isEqualTo(600000L * 1024);
		assertThat(result.rssAnonBytes()).isEqualTo(480000L * 1024);
		assertThat(result.rssFileBytes()).isEqualTo(40000L * 1024);
	}

	@Test
	void proc_status_내용이_없으면_모두_null이다() {
		// when
		ProcessView result = SystemMetricsParser.parseProcStatus(null);

		// then
		assertThat(result).isEqualTo(new ProcessView(null, null, null, null));
	}

	@Test
	void 일부_줄이_없으면_그_값만_null이다() {
		// when
		ProcessView result = SystemMetricsParser.parseProcStatus("VmRSS:\t 1000 kB\n");

		// then
		assertThat(result.rssBytes()).isEqualTo(1000L * 1024);
		assertThat(result.peakRssBytes()).isNull();
		assertThat(result.rssAnonBytes()).isNull();
	}

	@Test
	void 형식이_깨진_줄은_null이고_예외를_던지지_않는다() {
		// given
		String broken = "VmRSS:\t abc kB\nVmHWM:\t 100 MB\nRssAnon:\t -5 kB\nRssFile:\t 7\n";

		// when
		ProcessView result = SystemMetricsParser.parseProcStatus(broken);

		// then
		assertThat(result).isEqualTo(new ProcessView(null, null, null, null));
	}

	@Test
	void cgroup_숫자_값을_읽는다() {
		// when & then
		assertThat(SystemMetricsParser.parseCgroupValue("1073741824\n")).isEqualTo(1073741824L);
	}

	@Test
	void cgroup_max는_null이다() {
		// when & then
		assertThat(SystemMetricsParser.parseCgroupValue("max\n")).isNull();
	}

	@Test
	void cgroup_값이_비었거나_깨졌거나_null이면_null이다() {
		// when & then
		assertThat(SystemMetricsParser.parseCgroupValue("")).isNull();
		assertThat(SystemMetricsParser.parseCgroupValue("  \n")).isNull();
		assertThat(SystemMetricsParser.parseCgroupValue("12abc")).isNull();
		assertThat(SystemMetricsParser.parseCgroupValue("-1")).isNull();
		assertThat(SystemMetricsParser.parseCgroupValue(null)).isNull();
	}

	@Test
	void cgroup_v1의_한도_없음_큰_수는_null이다() {
		// when & then
		assertThat(SystemMetricsParser.parseCgroupValue("9223372036854771712\n")).isNull();
	}
}
