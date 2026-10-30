package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class LokiRootCauseConverterTest {

	private final LokiRootCauseConverter converter = new LokiRootCauseConverter();
	private final LoggerContext context = new LoggerContext();

	private LoggingEvent eventWith(Throwable throwable) {
		Logger logger = context.getLogger("com.ottnavi.test");
		LoggingEvent event = new LoggingEvent("test", logger, Level.ERROR, "메시지", null, null);
		if (throwable != null) {
			event.setThrowableProxy(new ThrowableProxy(throwable));
		}
		return event;
	}

	@Test
	void 예외가_없으면_빈_문자열이다() {
		// given
		LoggingEvent event = eventWith(null);

		// when
		String result = converter.convert(event);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	void cause가_없는_예외는_그_클래스_이름이다() {
		// given
		LoggingEvent event = eventWith(new IllegalStateException("hong@example.com 이 문제다"));

		// when
		String result = converter.convert(event);

		// then
		assertThat(result).isEqualTo("java.lang.IllegalStateException");
	}

	@Test
	void 래핑된_예외는_가장_안쪽_원인의_클래스_이름이다() {
		// given
		Throwable wrapped = new RuntimeException("바깥", new IllegalArgumentException("중간", new SocketTimeoutException("안쪽")));
		LoggingEvent event = eventWith(wrapped);

		// when
		String result = converter.convert(event);

		// then
		assertThat(result).isEqualTo("java.net.SocketTimeoutException");
	}

	@Test
	void 예외_메시지는_결과에_들어가지_않는다() {
		// given
		LoggingEvent event = eventWith(new RuntimeException("hong@example.com 에게 보내지 못했다"));

		// when
		String result = converter.convert(event);

		// then
		assertThat(result).doesNotContain("hong@example.com");
		assertThat(result).doesNotContain("보내지 못했다");
	}

	@Test
	void cause_사슬이_깊이_상한보다_길어도_끝나고_클래스_이름을_돌려준다() {
		// given
		Throwable chain = new IllegalStateException("끝");
		for (int i = 0; i < 30; i++) {
			chain = new RuntimeException("층 " + i, chain);
		}
		LoggingEvent event = eventWith(chain);

		// when
		String result = converter.convert(event);

		// then
		assertThat(result).isNotEmpty();
	}
}
