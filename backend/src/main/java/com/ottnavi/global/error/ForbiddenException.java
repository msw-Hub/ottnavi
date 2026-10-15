package com.ottnavi.global.error;

/** 요청이 허용되지 않을 때 던진다(예: 쿠키 인증 경로의 Origin 불일치). 403 FORBIDDEN으로 응답한다 */
public class ForbiddenException extends BusinessException {

	public ForbiddenException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.FORBIDDEN;
	}
}
