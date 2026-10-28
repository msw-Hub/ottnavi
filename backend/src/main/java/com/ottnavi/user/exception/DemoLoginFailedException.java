package com.ottnavi.user.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 데모 로그인의 아이디 또는 비밀번호가 틀렸을 때 던진다. 401 DEMO_LOGIN_FAILED로 응답한다(어느 쪽이 틀렸는지는 알리지 않는다) */
public class DemoLoginFailedException extends BusinessException {

	public DemoLoginFailedException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.DEMO_LOGIN_FAILED;
	}
}
