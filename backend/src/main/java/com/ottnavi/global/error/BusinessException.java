package com.ottnavi.global.error;

/**
 * 도메인 예외 기반 클래스. 하위 클래스가 getErrorCode()로 응답 오류 코드를 정한다.
 * 생성자는 규칙(backend.md)대로 (message), (message, cause) 두 개만 둔다.
 */
public abstract class BusinessException extends RuntimeException {

	protected BusinessException(String message) {
		super(message);
	}

	protected BusinessException(String message, Throwable cause) {
		super(message, cause);
	}

	/** 이 예외에 대응하는 오류 코드를 반환한다 */
	public abstract ErrorCode getErrorCode();
}
