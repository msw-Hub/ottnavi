package com.ottnavi.global.security.token;

/**
 * Refresh Token 교체 결과.
 *
 * @param result   교체 판정 결과
 * @param userId   토큰 주인(NOT_FOUND면 null)
 * @param newToken 새 Refresh Token 원문(ROTATED일 때만 값이 있다)
 */
public record RefreshRotation(Result result, Long userId, String newToken) {

	/** 교체 판정 결과 */
	public enum Result {
		ROTATED,    // 정상 교체: 새 토큰 발급, 이전 토큰은 직전 토큰으로 밀려남
		GRACE,      // 직전 토큰이 유예 안에 다시 옴: 교체·무효화 없이 Access Token만 발급
		REUSED,     // 유예가 지난 직전·오래된 토큰 재사용: 계열을 폐기함
		NOT_FOUND   // 계열이 없음(만료·폐기·위조)
	}

	public static RefreshRotation rotated(Long userId, String newToken) {
		return new RefreshRotation(Result.ROTATED, userId, newToken);
	}

	public static RefreshRotation grace(Long userId) {
		return new RefreshRotation(Result.GRACE, userId, null);
	}

	public static RefreshRotation reused(Long userId) {
		return new RefreshRotation(Result.REUSED, userId, null);
	}

	public static RefreshRotation notFound() {
		return new RefreshRotation(Result.NOT_FOUND, null, null);
	}
}
