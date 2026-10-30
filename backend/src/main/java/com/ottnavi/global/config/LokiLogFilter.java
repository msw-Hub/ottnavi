package com.ottnavi.global.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Loki로 보낼 로그를 거르는 필터다(observability 프로필의 Loki 어펜더에만 붙는다. 콘솔 출력에는 영향이 없다).
 * 우리 코드(com.ottnavi)는 INFO 이상, 그 밖의 라이브러리는 WARN 이상만 보내고,
 * 로그 전송 라이브러리 자신과 지표 전송 로그는 전송 실패가 로그를 낳고 그 로그가 다시 전송되는 순환을 막으려고 보내지 않는다.
 * Hibernate의 DB 오류 로그(org.hibernate.orm.jdbc.error)는 중복 키 오류에 열 값(이메일, 구글 계정 고유 번호)이 담겨 보내지 않는다.
 *
 * 어펜더 하나에 로거별로 다른 임계값을 거는 기능이 logback 기본 필터에 없어서 작은 필터 클래스로 만들었다.
 *
 * 예: com.ottnavi.plan.X INFO → 통과 / com.ottnavi.plan.X DEBUG → 거부 / org.hibernate.Y INFO → 거부 /
 * org.hibernate.Y WARN → 통과 / com.github.loki4j.Z ERROR → 거부
 */
public class LokiLogFilter extends Filter<ILoggingEvent> {

	private static final String APP_PACKAGE = "com.ottnavi";

	// 이 접두사의 로거는 수준과 상관없이 보내지 않는다(피드백 루프 방지)
	private static final String[] EXCLUDED_LOGGER_PREFIXES = {
			"com.github.loki4j",
			"io.micrometer.registry.otlp",
			// Hibernate의 DB 오류 로그는 "Detail: Key (컬럼)=(값) already exists." 줄에 값을 그대로 담는다.
			// users 테이블의 이메일·구글 계정 고유 번호(oauth_subject)도 해당돼, 이메일 마스킹으로는 고유 번호를 못 가리므로 보내지 않는다
			"org.hibernate.orm.jdbc.error"
	};

	@Override
	public FilterReply decide(ILoggingEvent event) {
		String loggerName = event.getLoggerName();

		// 1. 전송 라이브러리 자신의 로그는 보내지 않는다
		for (String prefix : EXCLUDED_LOGGER_PREFIXES) {
			if (isSameOrChildLogger(loggerName, prefix)) {
				return FilterReply.DENY;
			}
		}

		// 2. 우리 코드는 INFO 이상
		if (isSameOrChildLogger(loggerName, APP_PACKAGE)) {
			if (event.getLevel().isGreaterOrEqual(Level.INFO)) {
				return FilterReply.NEUTRAL;
			}
			return FilterReply.DENY;
		}

		// 3. 그 밖의 라이브러리는 WARN 이상
		if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
			return FilterReply.NEUTRAL;
		}
		return FilterReply.DENY;
	}

	/** 로거 이름이 접두사와 같거나 그 하위 패키지인지 본다(com.ottnaviX 같은 이름을 우리 코드로 오인하지 않는다). */
	private boolean isSameOrChildLogger(String loggerName, String prefix) {
		if (loggerName == null) {
			return false;
		}
		return loggerName.equals(prefix) || loggerName.startsWith(prefix + ".");
	}
}
