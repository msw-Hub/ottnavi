package com.ottnavi.global.error;

/**
 * Refresh Token 저장소(Redis)를 쓸 수 없을 때 던진다. 503 AUTH_STORE_UNAVAILABLE로 응답하고 Retry-After: 5 헤더를 붙인다.
 * 인증 정보가 틀린 것이 아니라 저장소 장애라서 401이 아니라 503이며, 화면은 쿠키를 지우지 않고 잠시 뒤 다시 시도한다.
 */
public class AuthStoreUnavailableException extends BusinessException {

	public static final long RETRY_AFTER_SECONDS = 5; // 다시 시도하도록 알리는 초

	public AuthStoreUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.AUTH_STORE_UNAVAILABLE;
	}
}
