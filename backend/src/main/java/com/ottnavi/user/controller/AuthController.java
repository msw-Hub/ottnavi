package com.ottnavi.user.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.error.UnauthorizedException;
import com.ottnavi.global.security.token.RefreshTokenCookies;
import com.ottnavi.user.dto.response.AccessTokenResponse;
import com.ottnavi.user.service.AuthTokenService;
import com.ottnavi.user.service.model.TokenIssue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access Token 재발급·로그아웃 API. Refresh Token은 HttpOnly 쿠키로만 오간다.
 * /api/auth/** 는 보안 설정에서 permitAll이라 Origin 검사와 토큰 검증을 서비스가 스스로 한다.
 */
@Tag(name = "auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthTokenService authTokenService;
	private final RefreshTokenCookies refreshTokenCookies;

	/** Refresh Token 쿠키로 새 Access Token을 발급한다. 교체한 경우 새 쿠키를 함께 내려 준다 */
	@Operation(operationId = "refreshAccessToken", summary = "Access Token 재발급")
	@PostMapping("/refresh")
	public CommonResponse<AccessTokenResponse> refreshAccessToken(
			@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
			HttpServletRequest request, HttpServletResponse response) {
		// 1. Origin을 먼저 검사한다(교체 전에 끝낸다)
		authTokenService.verifyOrigin(origin);
		// 2. 교체·발급. 인증 실패면 쿠키를 지우고 401로 응답한다
		TokenIssue issue;
		try {
			issue = authTokenService.refresh(refreshTokenCookies.read(request));
		} catch (UnauthorizedException e) {
			refreshTokenCookies.clear(response);
			throw e;
		}
		// 3. 교체했으면 새 Refresh Token 쿠키를 심는다
		if (issue.newRefreshToken() != null) {
			refreshTokenCookies.add(response, issue.newRefreshToken());
		}
		return CommonResponse.success(new AccessTokenResponse(issue.accessToken(), issue.role()));
	}

	/** Refresh Token 계열을 폐기하고 쿠키를 지운다. 쿠키가 없거나 무효여도 204다 */
	@Operation(operationId = "logout", summary = "로그아웃")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(
			@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
			HttpServletRequest request, HttpServletResponse response) {
		authTokenService.verifyOrigin(origin);
		authTokenService.logout(refreshTokenCookies.read(request));
		refreshTokenCookies.clear(response);
	}
}
