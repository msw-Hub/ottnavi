package com.ottnavi.user.controller;

import com.ottnavi.global.security.AuthUser;
import com.ottnavi.global.security.token.RefreshTokenCookies;
import com.ottnavi.user.service.UserWithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자 본인의 계정 API(/api/me). 내 설정은 UserSettingController가 맡는다 */
@Tag(name = "user")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class UserController {

	private final UserWithdrawService userWithdrawService;
	private final RefreshTokenCookies refreshTokenCookies;

	/** 계정을 즉시 영구 삭제하고 Refresh Token 쿠키를 지운다. 쿠키 Path가 /api/auth라 이 요청에는 쿠키가 오지 않아 사용자 ID로 계열을 폐기한다 */
	@Operation(operationId = "withdrawMe", summary = "회원 탈퇴")
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdrawMe(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			HttpServletResponse response) {
		userWithdrawService.withdraw(authUser.userId());
		refreshTokenCookies.clear(response);
	}
}
