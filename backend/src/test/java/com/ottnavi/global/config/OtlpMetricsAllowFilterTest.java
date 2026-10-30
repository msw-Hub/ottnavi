package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.junit.jupiter.api.Test;

class OtlpMetricsAllowFilterTest {

	private final OtlpMetricsAllowFilter filter = new OtlpMetricsAllowFilter();

	private MeterFilterReply reply(String name, Tags tags) {
		Meter.Id id = new Meter.Id(name, tags, null, null, Meter.Type.COUNTER);
		return filter.accept(id);
	}

	@Test
	void 허용_이름은_통과시킨다() {
		// given
		String name = "ratelimit.rejected";

		// when
		MeterFilterReply result = reply(name, Tags.of("bucket", "public"));

		// then
		assertThat(result).isEqualTo(MeterFilterReply.ACCEPT);
	}

	@Test
	void 허용_접두사로_시작하는_이름은_통과시킨다() {
		// given
		String name = "jvm.memory.used";

		// when
		MeterFilterReply result = reply(name, Tags.of("area", "heap"));

		// then
		assertThat(result).isEqualTo(MeterFilterReply.ACCEPT);
	}

	@Test
	void 허용_목록에_없는_이름은_거부한다() {
		// given
		String name = "app.unlisted.metric";

		// when
		MeterFilterReply result = reply(name, Tags.of("result", "success"));

		// then
		assertThat(result).isEqualTo(MeterFilterReply.DENY);
	}

	@Test
	void 줄거리_번역_호출_지표는_통과시킨다() {
		// given
		String name = "llm.call.duration";

		// when
		MeterFilterReply result = reply(name, Tags.of("operation", "translate_overview", "result", "success"));

		// then
		assertThat(result).isEqualTo(MeterFilterReply.ACCEPT);
	}

	@Test
	void 정확_일치가_필요한_이름에_글자가_더_붙으면_거부한다() {
		// given
		String name = "tmdb.call.extra";

		// when
		MeterFilterReply result = reply(name, Tags.empty());

		// then
		assertThat(result).isEqualTo(MeterFilterReply.DENY);
	}

	@Test
	void 개인정보_성격의_태그_키가_있으면_허용_이름이어도_거부한다() {
		// given
		String name = "ratelimit.rejected";

		// when
		MeterFilterReply result = reply(name, Tags.of("ip", "203.0.113.1"));

		// then
		assertThat(result).isEqualTo(MeterFilterReply.DENY);
	}
}
