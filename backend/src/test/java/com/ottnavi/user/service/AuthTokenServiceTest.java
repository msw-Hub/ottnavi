package com.ottnavi.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ottnavi.global.error.ForbiddenException;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.global.security.token.RefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Origin 검사와 로그 정리 규칙을 확인한다.
 * 막으려는 사고: 허용 주소가 로그에 새는 일, 헤더의 줄바꿈으로 가짜 로그 줄이 생기는 일, 긴 값이 로그를 채우는 일.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuthTokenServiceTest {

	private static final String ALLOWED_ORIGIN = "https://allowed.example";

	private final AuthTokenService authTokenService = new AuthTokenService(
			mock(RefreshTokenStore.class), mock(UserLoginService.class), mock(JwtTokenProvider.class),
			mock(DemoLoginProperties.class), ALLOWED_ORIGIN + "/");

	@Test
	void 허용_주소와_같은_Origin은_통과한다() {
		// when & then
		assertThatCode(() -> authTokenService.verifyOrigin(ALLOWED_ORIGIN)).doesNotThrowAnyException();
	}

	@Test
	void 다른_Origin은_403이고_받은_값만_WARN으로_남긴다(CapturedOutput output) {
		// when & then
		assertThatThrownBy(() -> authTokenService.verifyOrigin("https://evil.example"))
				.isInstanceOf(ForbiddenException.class);
		assertThat(output.getAll()).contains("요청 출처가 허용 주소와 다릅니다: origin=https://evil.example");
		assertThat(output.getAll()).doesNotContain(ALLOWED_ORIGIN);
	}

	@Test
	void Origin이_없으면_403이고_없음으로_남긴다(CapturedOutput output) {
		// when & then
		assertThatThrownBy(() -> authTokenService.verifyOrigin(null)).isInstanceOf(ForbiddenException.class);
		assertThat(output.getAll()).contains("origin=없음");
	}

	@Test
	void 로그용_Origin은_줄바꿈과_제어문자를_지운다() {
		// when
		String result = AuthTokenService.sanitizeOriginForLog("https://evil.example\r\n2026 ERROR fake\t\u0000");

		// then
		assertThat(result).isEqualTo("https://evil.example2026 ERROR fake");
	}

	@Test
	void 로그용_Origin은_100자로_자른다() {
		// given
		String longOrigin = "a".repeat(250);

		// when
		String result = AuthTokenService.sanitizeOriginForLog(longOrigin);

		// then
		assertThat(result).hasSize(100);
	}

	@Test
	void 로그용_Origin이_null이면_없음이다() {
		// when & then
		assertThat(AuthTokenService.sanitizeOriginForLog(null)).isEqualTo("없음");
	}
}
