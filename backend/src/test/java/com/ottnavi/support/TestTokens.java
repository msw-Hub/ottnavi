package com.ottnavi.support;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.user.domain.enums.UserRole;

/**
 * 통합 테스트에서 실제 JwtTokenProvider로 Access Token을 만들어 Authorization 헤더 값으로 돌려준다.
 * 사용: new TestTokens(jwtTokenProvider).adminBearer()
 */
public class TestTokens {

	private static final long DEFAULT_USER_ID = 1L;

	private final JwtTokenProvider jwtTokenProvider;

	public TestTokens(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	/** USER 권한 토큰의 Authorization 헤더 값("Bearer ...") */
	public String userBearer() {
		return bearer(DEFAULT_USER_ID, UserRole.USER);
	}

	/** ADMIN 권한 토큰의 Authorization 헤더 값("Bearer ...") */
	public String adminBearer() {
		return bearer(DEFAULT_USER_ID, UserRole.ADMIN);
	}

	/** 지정한 사용자 id·권한의 Authorization 헤더 값 */
	public String bearer(long userId, UserRole role) {
		return "Bearer " + jwtTokenProvider.issueAccessToken(userId, role);
	}
}
