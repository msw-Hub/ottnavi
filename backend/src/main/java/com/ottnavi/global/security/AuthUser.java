package com.ottnavi.global.security;

import com.ottnavi.user.domain.enums.UserRole;

/** 인증된 요청의 사용자(Authentication의 principal). JWT의 sub·role을 담는다 */
public record AuthUser(
		Long userId,    // users.id
		UserRole role) {
}
