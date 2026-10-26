package com.ottnavi.global.security.token;

/**
 * Refresh Token 서버 측 저장소(TECH 4절, R11-21). 토큰은 `{familyId}.{secret}` 형식이고 서버에는 해시만 둔다.
 * 구현은 Redis이며, 나중에 PostgreSQL 구현으로 바꿀 수 있게 인터페이스로 둔다.
 * 저장소 오류(Redis 장애)는 잡지 않고 예외로 올린다(fail-closed).
 */
public interface RefreshTokenStore {

	/** 로그인 때 새 계열을 만들고 Refresh Token 원문을 돌려준다 */
	String issue(Long userId);

	/** 토큰을 교체한다. 토큰 형식이 틀리거나 계열이 없으면 NOT_FOUND다 */
	RefreshRotation rotate(String rawToken);

	/** 현재 토큰이나 직전 토큰이 맞을 때만 그 계열을 폐기한다(로그아웃). 맞지 않으면 아무것도 하지 않는다 */
	void revoke(String rawToken);

	/** 사용자의 모든 계열을 폐기한다(탈퇴·전체 로그아웃용) */
	void revokeAllOf(Long userId);
}
