package com.ottnavi.user.service.model;

import com.ottnavi.user.domain.enums.UserRole;

/**
 * 재발급 결과.
 *
 * @param accessToken     새 Access Token
 * @param role            사용자 권한(DB에서 다시 읽은 값)
 * @param newRefreshToken 새 Refresh Token 원문. 유예(GRACE) 응답이면 null이라 쿠키를 갱신하지 않는다
 */
public record TokenIssue(String accessToken, UserRole role, String newRefreshToken) {
}
