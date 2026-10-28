package com.ottnavi.user.domain.enums;

/** 사용자 권한(ERD users.role). 배치 호출 전용 권한(BATCH)은 사용자 행이 아니라 배치 토큰 필터가 요청에만 붙인다 */
public enum UserRole {
	USER,   // 일반 사용자
	ADMIN,  // 관리자
	DEMO    // 데모 계정: 회원 기능은 쓰고 관리자 화면은 조회만, 탈퇴·알림 설정 변경은 막는다(SecurityConfig)
}
