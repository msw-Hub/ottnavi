package com.ottnavi.user.dto.response;

import com.ottnavi.user.domain.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 새 Access Token(계약 AccessToken). Refresh Token은 쿠키로만 오가며 본문에 담지 않는다.
 *
 * @param accessToken Access Token(JWT)
 * @param role        사용자 권한. 화면이 관리자 메뉴 표시 여부를 정하는 데 쓴다
 */
public record AccessTokenResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRole role) {
}
