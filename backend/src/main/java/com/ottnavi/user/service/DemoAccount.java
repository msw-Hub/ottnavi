package com.ottnavi.user.service;

/**
 * 데모 계정 행(users)에 넣는 고정값. 이름·이메일을 바꿀 때는 이 클래스만 고친다.
 * 이메일은 실제로 메일이 가지 않는 예약 도메인(.invalid, RFC 2606)을 써서 구글 계정 이메일과 겹치지 않게 한다.
 */
public final class DemoAccount {

	public static final String SUBJECT = "demo";                  // users.oauth_subject(로그인 방식 DEMO와 함께 유니크)
	public static final String EMAIL = "demo@ottnavi.invalid";    // users.email
	public static final String NAME = "데모 계정";                 // users.name(화면 표시 이름)

	private DemoAccount() {
	}
}
