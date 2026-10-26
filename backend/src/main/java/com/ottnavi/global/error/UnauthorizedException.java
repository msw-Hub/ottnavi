package com.ottnavi.global.error;

/** 인증에 실패했을 때 던진다(Refresh Token 없음·무효·재사용 감지 등). 401 UNAUTHORIZED로 응답한다 */
public class UnauthorizedException extends BusinessException {

	public UnauthorizedException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.UNAUTHORIZED;
	}
}
