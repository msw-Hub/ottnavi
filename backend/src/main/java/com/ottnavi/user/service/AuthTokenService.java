package com.ottnavi.user.service;

import com.ottnavi.global.error.ForbiddenException;
import com.ottnavi.global.error.UnauthorizedException;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.global.security.token.RefreshRotation;
import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.service.model.TokenIssue;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Access Token 재발급과 로그아웃(TECH 4절). 쿠키를 쓰는 두 경로라 Origin을 먼저 검사한다.
 * Redis 호출은 트랜잭션 밖이고, DB 조회는 UserLoginService가 읽기 트랜잭션으로 맡는다.
 * Redis 장애는 잡지 않고 올려 500으로 응답한다(fail-closed, 401로 오해하지 않게).
 * 토큰 원문·familyId는 로그에 남기지 않는다.
 */
@Slf4j
@Service
public class AuthTokenService {

	private final RefreshTokenStore refreshTokenStore;
	private final UserLoginService userLoginService;
	private final JwtTokenProvider jwtTokenProvider;
	private final String allowedOrigin;

	public AuthTokenService(RefreshTokenStore refreshTokenStore, UserLoginService userLoginService,
			JwtTokenProvider jwtTokenProvider, @Value("${app.frontend-origin}") String allowedOrigin) {
		this.refreshTokenStore = refreshTokenStore;
		this.userLoginService = userLoginService;
		this.jwtTokenProvider = jwtTokenProvider;
		this.allowedOrigin = stripTrailingSlash(allowedOrigin);
	}

	/** Origin 헤더가 허용 주소와 정확히 같지 않으면 403이다(CSRF 방지). 교체·폐기 전에 호출한다 */
	public void verifyOrigin(String origin) {
		if (origin == null || !origin.equals(allowedOrigin)) {
			throw new ForbiddenException("허용되지 않은 요청 출처입니다.");
		}
	}

	/** Refresh Token을 교체하고 새 Access Token을 발급한다. 무효이면 401이다 */
	public TokenIssue refresh(String rawToken) {
		// 1. 쿠키가 없으면 인증 실패
		if (rawToken == null || rawToken.isBlank()) {
			throw new UnauthorizedException("인증이 필요합니다.");
		}
		// 2. 교체 판정(Redis, 원자적)
		RefreshRotation rotation = refreshTokenStore.rotate(rawToken);
		if (rotation.result() == RefreshRotation.Result.NOT_FOUND) {
			throw new UnauthorizedException("인증이 필요합니다.");
		}
		if (rotation.result() == RefreshRotation.Result.REUSED) {
			log.warn("Refresh Token 재사용을 감지해 계열을 폐기했습니다: userId={}", rotation.userId());
			throw new UnauthorizedException("인증이 필요합니다.");
		}
		// 3. 권한을 DB에서 다시 읽는다. 사용자가 없으면 이 사용자의 계열을 모두 폐기한다
		Optional<UserRole> role = userLoginService.findRole(rotation.userId());
		if (role.isEmpty()) {
			refreshTokenStore.revokeAllOf(rotation.userId());
			throw new UnauthorizedException("인증이 필요합니다.");
		}
		// 4. Access Token을 발급한다. 교체한 경우에만 새 Refresh Token을 함께 돌려준다(유예 응답은 null)
		String accessToken = jwtTokenProvider.issueAccessToken(rotation.userId(), role.get());
		return new TokenIssue(accessToken, role.get(), rotation.newToken());
	}

	/** 쿠키의 Refresh Token 계열을 폐기한다. 쿠키가 없거나 무효이면 아무것도 하지 않는다 */
	public void logout(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		refreshTokenStore.revoke(rawToken);
	}

	private static String stripTrailingSlash(String value) {
		String result = value.strip();
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}
}
