package com.ottnavi.user.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 내 설정(온보딩)을 한 번도 저장하지 않은 사용자가 설정 행이 필요한 저장을 요청할 때 던진다. 409 ONBOARDING_REQUIRED로 응답한다 */
public class OnboardingRequiredException extends BusinessException {

	public OnboardingRequiredException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.ONBOARDING_REQUIRED;
	}
}
