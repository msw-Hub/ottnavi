package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;

class LokiLogFilterTest {

	private final LokiLogFilter filter = new LokiLogFilter();
	private final LoggerContext context = new LoggerContext();

	private FilterReply decide(String loggerName, Level level) {
		Logger logger = context.getLogger(loggerName);
		LoggingEvent event = new LoggingEvent("test", logger, level, "메시지", null, null);
		return filter.decide(event);
	}

	@Test
	void 우리_코드의_INFO_로그는_통과한다() {
		// given
		String loggerName = "com.ottnavi.plan.service.PlanService";

		// when
		FilterReply reply = decide(loggerName, Level.INFO);

		// then
		assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
	}

	@Test
	void 우리_코드의_DEBUG_로그는_거부한다() {
		// given
		String loggerName = "com.ottnavi.plan.service.PlanService";

		// when
		FilterReply reply = decide(loggerName, Level.DEBUG);

		// then
		assertThat(reply).isEqualTo(FilterReply.DENY);
	}

	@Test
	void 라이브러리의_INFO_로그는_거부한다() {
		// given
		String loggerName = "org.hibernate.SQL";

		// when
		FilterReply reply = decide(loggerName, Level.INFO);

		// then
		assertThat(reply).isEqualTo(FilterReply.DENY);
	}

	@Test
	void 라이브러리의_WARN_로그는_통과한다() {
		// given
		String loggerName = "org.hibernate.SQL";

		// when
		FilterReply reply = decide(loggerName, Level.WARN);

		// then
		assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
	}

	@Test
	void 로그_전송_라이브러리의_ERROR_로그도_거부한다() {
		// given
		String loggerName = "com.github.loki4j.logback.Loki4jAppender";

		// when
		FilterReply reply = decide(loggerName, Level.ERROR);

		// then
		assertThat(reply).isEqualTo(FilterReply.DENY);
	}

	@Test
	void 지표_전송_라이브러리의_ERROR_로그도_거부한다() {
		// given
		String loggerName = "io.micrometer.registry.otlp.OtlpMeterRegistry";

		// when
		FilterReply reply = decide(loggerName, Level.ERROR);

		// then
		assertThat(reply).isEqualTo(FilterReply.DENY);
	}

	@Test
	void Hibernate의_DB_오류_로그는_값이_담길_수_있어_WARN이어도_거부한다() {
		// given
		String loggerName = "org.hibernate.orm.jdbc.error";

		// when
		FilterReply reply = decide(loggerName, Level.WARN);

		// then
		assertThat(reply).isEqualTo(FilterReply.DENY);
	}

	@Test
	void 이름만_비슷한_다른_패키지는_우리_코드로_보지_않는다() {
		// given
		String loggerName = "com.ottnaviX.other.Thing";

		// when
		FilterReply reply = decide(loggerName, Level.INFO);

		// then
		assertThat(reply).isEqualTo(FilterReply.DENY);
	}
}
