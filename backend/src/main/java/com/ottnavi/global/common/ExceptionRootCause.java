package com.ottnavi.global.common;

/**
 * 예외 체인의 가장 안쪽 원인을 찾는 도구.
 * 래핑된 예외(예: MailSendException → MessagingException → SocketTimeoutException)에서 진짜 원인 종류만 로그에 남기려고 쓴다.
 * 메시지에 수신 주소·접속 정보가 섞일 수 있어 클래스 이름만 다룬다.
 */
public final class ExceptionRootCause {

	private static final int MAX_DEPTH = 10; // cause가 서로를 가리키는 순환을 끊는 깊이 상한
	private static final String NO_EXCEPTION = "없음";

	private ExceptionRootCause() {
	}

	/**
	 * 예외 체인의 가장 안쪽 원인 클래스의 전체 이름(패키지 포함)을 돌려준다.
	 *
	 * 예시
	 * - cause가 없는 IllegalStateException → "java.lang.IllegalStateException"
	 * - RuntimeException(cause=IOException(cause=SocketTimeoutException)) → "java.net.SocketTimeoutException"
	 * - 순환 cause이거나 깊이가 10을 넘으면 10단계까지 따라간 예외의 이름
	 * - null → "없음"
	 */
	public static String rootCauseName(Throwable throwable) {
		// 1. 예외가 없으면 고정 문구
		if (throwable == null) {
			return NO_EXCEPTION;
		}

		// 2. cause를 따라 내려가되, 순환을 막으려고 깊이 상한까지만 간다
		Throwable current = throwable;
		for (int depth = 0; depth < MAX_DEPTH; depth++) {
			Throwable next = current.getCause();
			if (next == null) {
				break;
			}
			current = next;
		}
		return current.getClass().getName();
	}
}
