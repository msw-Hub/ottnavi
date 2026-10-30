package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.CoreConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * logback-spring.xml에 적힌 Loki 메시지 패턴 문자열을 그대로 꺼내 PatternLayout으로 시험한다(이메일 마스킹·원인 클래스·스택 제외).
 * 패턴을 테스트에 복사해 두지 않으므로 XML의 패턴이 바뀌면 이 시험도 같이 반응한다.
 */
class LokiMessagePatternTest {

	private PatternLayout layout;
	private final LoggerContext context = new LoggerContext();

	@BeforeEach
	void setUp() throws IOException {
		// 1. XML 파일에서 Loki 메시지 패턴을 읽는다
		Path xml = Path.of("src/main/resources/logback-spring.xml");
		String text = Files.readString(xml, StandardCharsets.UTF_8);
		Matcher matcher = Pattern.compile("<pattern>(\\[%thread\\].*?)</pattern>").matcher(text);
		assertThat(matcher.find()).as("logback-spring.xml에서 Loki 메시지 패턴을 찾아야 한다").isTrue();

		// 2. XML의 conversionRule과 같게 %rootCause를 등록한다
		Map<String, String> rules = new HashMap<>();
		rules.put("rootCause", LokiRootCauseConverter.class.getName());
		context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, rules);

		// 3. 레이아웃을 만든다
		layout = new PatternLayout();
		layout.setContext(context);
		layout.setPattern(matcher.group(1));
		layout.start();
	}

	private String render(String message, Throwable throwable) {
		Logger logger = context.getLogger("com.ottnavi.test.Probe");
		LoggingEvent event = new LoggingEvent("test", logger, Level.ERROR, message, null, null);
		if (throwable != null) {
			event.setThrowableProxy(new ThrowableProxy(throwable));
		}
		return layout.doLayout(event);
	}

	@Test
	void 이메일_모양_문자열은_별표로_가려진다() {
		// given
		String message = "발송 실패: hong.gildong+test@example.co.kr 에게 보내지 못했다";

		// when
		String result = render(message, null);

		// then
		assertThat(result).contains("발송 실패: *** 에게 보내지 못했다");
		assertThat(result).doesNotContain("hong");
		assertThat(result).doesNotContain("example.co.kr");
	}

	@Test
	void 이메일이_여러_개여도_모두_가려진다() {
		// given
		String message = "a@example.com 그리고 b@example.org";

		// when
		String result = render(message, null);

		// then
		assertThat(result).contains("*** 그리고 ***");
	}

	@Test
	void 이메일이_없는_메시지는_그대로_나온다() {
		// given
		String message = "수집 완료: 120건 처리, 골뱅이 @ 기호만 있는 문장";

		// when
		String result = render(message, null);

		// then
		assertThat(result).contains(message);
	}

	@Test
	void 예외가_없으면_rootCause_조각이_붙지_않는다() {
		// given
		String message = "정상 로그";

		// when
		String result = render(message, null);

		// then
		assertThat(result).doesNotContain("rootCause");
		assertThat(result).endsWith("정상 로그");
	}

	@Test
	void 예외가_있으면_근본원인_클래스_이름이_붙는다() {
		// given
		Throwable throwable = new RuntimeException("바깥", new IllegalStateException("안쪽"));

		// when
		String result = render("작업 실패", throwable);

		// then
		assertThat(result).endsWith("작업 실패 rootCause=java.lang.IllegalStateException");
	}

	@Test
	void 예외가_있어도_예외_메시지와_스택_줄은_나오지_않는다() {
		// given
		Throwable throwable = new RuntimeException("hong@example.com 문장입니다", new IllegalStateException("안쪽 문장입니다"));

		// when
		String result = render("작업 실패", throwable);

		// then
		assertThat(result).doesNotContain("문장입니다");
		assertThat(result).doesNotContain("\tat ");
		assertThat(result).doesNotContain("at com.ottnavi");
	}
}
