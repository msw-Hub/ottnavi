package com.ottnavi.global.config;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Loki 메시지 패턴의 %rootCause 변환기다. 로그에 붙은 예외의 가장 안쪽 원인 클래스 이름만 돌려준다.
 * 스택 트레이스와 예외 메시지(getMessage 문장)에는 수신 주소·입력값 같은 개인정보가 섞일 수 있어 Loki로 보내지 않고,
 * 어떤 종류의 오류인지 알 수 있는 클래스 이름만 남긴다. 전체 스택은 콘솔(Cloudtype 로그)에만 남는다.
 *
 * 예: 예외 없음 → "" / IllegalStateException → "java.lang.IllegalStateException" /
 * MailSendException(cause=SocketTimeoutException) → "java.net.SocketTimeoutException"
 *
 * 기존 ExceptionRootCause는 Throwable을 받지만 logback 이벤트는 예외를 IThrowableProxy로 들고 있어, 같은 깊이 상한으로 프록시를 따라간다.
 */
public class LokiRootCauseConverter extends ClassicConverter {

	private static final int MAX_DEPTH = 10; // cause가 서로를 가리키는 순환을 끊는 깊이 상한(ExceptionRootCause와 같다)

	@Override
	public String convert(ILoggingEvent event) {
		// 1. 예외가 없으면 빈 문자열(패턴에서 이 조각이 통째로 사라진다)
		IThrowableProxy proxy = event.getThrowableProxy();
		if (proxy == null) {
			return "";
		}

		// 2. cause를 따라 가장 안쪽까지 내려간다
		IThrowableProxy current = proxy;
		for (int depth = 0; depth < MAX_DEPTH; depth++) {
			IThrowableProxy next = current.getCause();
			if (next == null) {
				break;
			}
			current = next;
		}

		// 3. 메시지는 쓰지 않고 클래스 이름만 돌려준다
		return current.getClassName();
	}
}
