package com.ottnavi.user.service.model;

import com.ottnavi.user.domain.enums.UserRole;

/**
 * 로그인을 마친 사용자 식별 값.
 *
 * @param userId users.id
 * @param role   권한
 */
public record LoginUser(Long userId, UserRole role) {
}
