package com.ottnavi.user.service;

import com.ottnavi.global.error.ForbiddenException;
import com.ottnavi.global.error.UnauthorizedException;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.global.security.token.RefreshRotation;
import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.service.model.LoginUser;
import com.ottnavi.user.service.model.TokenIssue;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Access Token 재발급과 로그아웃(TECH 4절), 데모 로그인 직후의 토큰 발급. 쿠키를 쓰는 경로라 Origin을 먼저 검사한다.
 * Redis 호출은 트랜잭션 밖이고, DB 조회는 UserLoginService가 읽기 트랜잭션으로 맡는다.
 * Redis 장애는 잡지 않고 올려 500으로 응답한다(fail-closed, 401로 오해하지 않게).
 * 토큰 원문·familyId는 로그에 남기지 않는다.
 */
@Slf4j
@Service
public class AuthTokenService {

	private static final int MAX_LOGGED_ORIGIN_LENGTH = 100; // 로그에 남기는 Origin 최대 길이

	private final RefreshTokenStore refreshTokenStore;
	private final UserLoginService userLoginService;
	private final JwtTokenProvider jwtTokenProvider;
	private final DemoLoginProperties demoLoginProperties; // 데모 로그인을 끈 뒤 남은 데모 세션 정리용
	private final String allowedOrigin;

	public AuthTokenService(RefreshTokenStore refreshTokenStore, UserLoginService userLoginService,
			JwtTokenProvider jwtTokenProvider, DemoLoginProperties demoLoginProperties,
			@Value("${app.frontend-origin}") String allowedOrigin) {
		this.refreshTokenStore = refreshTokenStore;
		this.userLoginService = userLoginService;
		this.jwtTokenProvider = jwtTokenProvider;
		this.demoLoginProperties = demoLoginProperties;
		this.allowedOrigin = stripTrailingSlash(allowedOrigin);
	}

	/** Origin 헤더가 허용 주소와 정확히 같지 않으면 403이다(CSRF 방지). 교체·폐기 전에 호출한다 */
	public void verifyOrigin(String origin) {
		if (origin == null || !origin.equals(allowedOrigin)) {
			// 원인 파악용으로 받은 값만 남긴다. 허용 주소(APP_FRONTEND_ORIGIN)는 남기지 않는다
			log.warn("요청 출처가 허용 주소와 다릅니다: origin={}", sanitizeOriginForLog(origin));
			throw new ForbiddenException("허용되지 않은 요청 출처입니다.");
		}
	}

	/**
	 * 로그에 남길 Origin 값을 정리한다. 로그 주입을 막으려고 줄바꿈·제어문자를 지우고 100자로 자른다.
	 * 예: null → "없음", "https://evil.example\nFAKE" → "https://evil.exampleFAKE"
	 */
	static String sanitizeOriginForLog(String origin) {
		// 1. 헤더가 없으면 "없음"
		if (origin == null) {
			return "없음";
		}
		// 2. 제어문자(줄바꿈 포함)를 건너뛰며 100자까지만 모은다
		StringBuilder cleaned = new StringBuilder();
		for (int i = 0; i < origin.length() && cleaned.length() < MAX_LOGGED_ORIGIN_LENGTH; i++) {
			char c = origin.charAt(i);
			if (!Character.isISOControl(c)) {
				cleaned.append(c);
			}
		}
		return cleaned.toString();
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
		// 4. 데모 로그인을 끈 뒤 남은 데모 세션은 계열을 모두 폐기하고 끝낸다(끄면 데모 계정이 곧바로 쓸 수 없게 된다)
		if (role.get() == UserRole.DEMO && !demoLoginProperties.isEnabled()) {
			refreshTokenStore.revokeAllOf(rotation.userId());
			log.info("데모 로그인이 꺼져 있어 데모 세션을 폐기했습니다: userId={}", rotation.userId());
			throw new UnauthorizedException("인증이 필요합니다.");
		}
		// 5. Access Token을 발급한다. 교체한 경우에만 새 Refresh Token을 함께 돌려준다(유예 응답은 null)
		String accessToken = jwtTokenProvider.issueAccessToken(rotation.userId(), role.get());
		return new TokenIssue(accessToken, role.get(), rotation.newToken());
	}

	/**
	 * 로그인 직후 Refresh Token과 Access Token을 함께 발급한다(데모 로그인). Redis 호출이라 트랜잭션 밖에서 부른다.
	 * 구글 로그인은 화면 이동(리다이렉트)이라 Refresh Token 쿠키만 심고 Access Token은 화면이 재발급으로 받지만,
	 * 데모 로그인은 화면이 직접 부르는 API라 응답 본문에 Access Token을 바로 담는다.
	 */
	public TokenIssue issueForLogin(LoginUser loginUser) {
		String refreshToken = refreshTokenStore.issue(loginUser.userId());
		String accessToken = jwtTokenProvider.issueAccessToken(loginUser.userId(), loginUser.role());
		return new TokenIssue(accessToken, loginUser.role(), refreshToken);
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
