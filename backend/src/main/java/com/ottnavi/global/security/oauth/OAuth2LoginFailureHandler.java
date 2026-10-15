package com.ottnavi.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 구글 로그인 실패 시 프론트 콜백 화면으로 보낸다(302 {프론트 주소}/auth/callback?error=login_failed).
 * 이동 주소는 설정 값으로 고정하고, 예외 메시지·요청 값은 응답과 로그에 싣지 않는다(에러 클래스 이름만 남긴다).
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

	private final String failureUrl;

	public OAuth2LoginFailureHandler(@Value("${app.frontend-origin}") String frontendOrigin) {
		this.failureUrl = OAuth2LoginSuccessHandler.callbackUrl(frontendOrigin) + "?error=login_failed";
	}

	/** 인증 단계(코드 교환·ID 토큰 검증 등)에서 실패했을 때 호출된다 */
	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		redirect(response, exception.getClass().getSimpleName());
	}

	/** 로그인 처리 중 사용자·Redis 오류가 났을 때 성공 핸들러가 같은 경로로 보낸다 */
	void redirect(HttpServletResponse response, String causeClassName) throws IOException {
		log.warn("구글 로그인에 실패했습니다: {}", causeClassName);
		response.sendRedirect(failureUrl);
	}
}
