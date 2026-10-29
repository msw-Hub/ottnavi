package com.ottnavi.llmdraft.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/**
 * 줄거리 번역 초안 기능의 업무 규칙 위반을 알린다. 오류 코드(LLM_*)를 생성자로 받아 하나의 클래스로 구분한다
 * (코드마다 클래스를 나누면 같은 모양이 여러 개가 되기 때문이다. ProductPriceConflictException과 같은 이유로
 * 규칙의 "생성자 두 개"에서 벗어난다).
 */
public class LlmDraftException extends BusinessException {

	private final ErrorCode errorCode;

	public LlmDraftException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public LlmDraftException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	@Override
	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
