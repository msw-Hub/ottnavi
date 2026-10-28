package com.ottnavi.user.domain.enums;

/** 로그인 방식(ERD users.oauth_provider). 회원은 GOOGLE, 데모 계정은 DEMO(서버 설정 아이디·비밀번호 로그인)다 */
public enum OAuthProvider {
	GOOGLE, // 구글 소셜 로그인
	DEMO    // 데모 로그인(소셜 로그인이 아니며 oauth_subject는 고정값 demo)
}
