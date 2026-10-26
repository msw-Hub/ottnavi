package com.ottnavi.global.error;

/**
 * 요청 제한을 넘었을 때 던진다. 429 RATE_LIMIT_EXCEEDED로 응답하고 Retry-After 헤더에 retryAfterSeconds를 담는다.
 * 다음 요청이 가능해질 때까지 기다릴 초를 알려 주어야 해서, 규칙(message, cause 두 생성자)과 달리 초 값을 함께 받는다.
 */
public class RateLimitExceededException extends BusinessException {

	private final long retryAfterSeconds; // 다시 시도할 수 있을 때까지 남은 초(올림, 최소 1)

	public RateLimitExceededException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.RATE_LIMIT_EXCEEDED;
	}
}
