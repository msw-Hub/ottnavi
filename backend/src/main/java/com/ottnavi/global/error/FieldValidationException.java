package com.ottnavi.global.error;

import lombok.Getter;

/**
 * 특정 요청 필드의 검증 실패를 알리는 예외. 400 VALIDATION_FAILED로 응답하며 fieldErrors에 (field, message) 한 건이 담긴다.
 * BusinessException 하위 클래스의 생성자 규칙((message), (message, cause))에서 벗어나는 이유는,
 * 응답의 fieldErrors에 필드명이 필요해 field를 함께 받아야 하기 때문이다.
 */
@Getter
public class FieldValidationException extends BusinessException {

	private final String field; // 오류가 난 요청 필드명

	public FieldValidationException(String field, String message) {
		super(message);
		this.field = field;
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.VALIDATION_FAILED;
	}
}
