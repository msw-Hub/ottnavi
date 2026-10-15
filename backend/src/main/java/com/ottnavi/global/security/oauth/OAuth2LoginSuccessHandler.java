package com.ottnavi.global.security.oauth;

import com.ottnavi.global.security.token.RefreshTokenCookies;
import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.user.service.UserLoginService;
import com.ottnavi.user.service.model.GoogleProfile;
import com.ottnavi.user.service.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 구글 인증이 끝나면 사용자 행을 만들거나 갱신하고, Refresh Token 쿠키를 심은 뒤 프론트 콜백 화면으로 보낸다.
 * 이동 주소는 설정 값(app.frontend-origin)으로 고정한 절대 주소이고 URL에 토큰을 싣지 않는다(사용자 입력 미사용).
 * 사용자 저장은 트랜잭션 안(UserLoginService), Redis 호출은 트랜잭션 밖이다. 실패하면 실패 핸들러와 같은 경로로 간다.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserLoginService userLoginService;
	private final RefreshTokenStore refreshTokenStore;
	private final RefreshTokenCookies refreshTokenCookies;
	private final OAuth2LoginFailureHandler failureHandler;
	private final String callbackUrl;

	public OAuth2LoginSuccessHandler(UserLoginService userLoginService, RefreshTokenStore refreshTokenStore,
			RefreshTokenCookies refreshTokenCookies, OAuth2LoginFailureHandler failureHandler,
			@Value("${app.frontend-origin}") String frontendOrigin) {
		this.userLoginService = userLoginService;
		this.refreshTokenStore = refreshTokenStore;
		this.refreshTokenCookies = refreshTokenCookies;
		this.failureHandler = failureHandler;
		this.callbackUrl = callbackUrl(frontendOrigin);
	}

	/** 프론트 주소(끝의 /는 제거)로 로그인 콜백 화면 주소를 만든다 */
	static String callbackUrl(String frontendOrigin) {
		String base = frontendOrigin.strip();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base + "/auth/callback";
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		try {
			// 1. 구글 프로필(sub·email·name)을 꺼낸다. 필수 값이 없으면 실패로 본다
			GoogleProfile profile = extractProfile(authentication);
			// 2. 사용자 가입·갱신(트랜잭션)
			LoginUser loginUser = userLoginService.login(profile);
			// 3. 트랜잭션 밖에서 Refresh Token을 발급해 쿠키로 심는다
			String refreshToken = refreshTokenStore.issue(loginUser.userId());
			refreshTokenCookies.add(response, refreshToken);
		} catch (RuntimeException e) {
			failureHandler.redirect(response, e.getClass().getSimpleName());
			return;
		}
		// 4. 토큰 없이 프론트 콜백 화면으로 이동한다. 화면이 /api/auth/refresh로 Access Token을 받는다
		response.sendRedirect(callbackUrl);
	}

	private GoogleProfile extractProfile(Authentication authentication) {
		if (!(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
			throw new IllegalStateException("OAuth2 사용자 정보가 없습니다.");
		}
		String sub = oauth2User.getAttribute("sub");
		String email = oauth2User.getAttribute("email");
		String name = oauth2User.getAttribute("name");
		if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
			throw new IllegalStateException("구글 프로필에 sub 또는 email이 없습니다.");
		}
		// 이메일이 검증된 계정만 허용한다(email_verified가 없거나 true가 아니면 거부)
		if (!isEmailVerified(oauth2User.getAttribute("email_verified"))) {
			throw new UnverifiedEmailException();
		}
		return new GoogleProfile(sub, email, name);
	}

	private boolean isEmailVerified(Object value) {
		if (value instanceof Boolean verified) {
			return verified;
		}
		return value instanceof String text && "true".equalsIgnoreCase(text);
	}

	/** 이메일 미검증 계정. 실패 핸들러가 클래스 이름만 warn으로 남기므로 이름이 곧 사유다(이메일·ID는 남기지 않는다) */
	static class UnverifiedEmailException extends RuntimeException {
		UnverifiedEmailException() {
			super("이메일 미검증 계정");
		}
	}
}
