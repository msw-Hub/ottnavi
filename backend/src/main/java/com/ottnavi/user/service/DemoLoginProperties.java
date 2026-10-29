package com.ottnavi.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데모 계정 로그인 설정(app.demo-login.*). 값은 환경 변수 DEMO_LOGIN_ID·DEMO_LOGIN_PASSWORD로만 넣는다.
 *
 * <pre>
 * 둘 다 비어 있음            → 꺼짐(데모 로그인 API가 404)
 * 하나만 있음                → 기동 실패(설정 실수를 켜진 척 넘기지 않는다)
 * 비밀번호가 16자 미만       → 기동 실패(공개된 계정이라 맞혀 보기 어렵게 한다)
 * 둘 다 있고 비밀번호 16자 이상 → 켜짐
 * </pre>
 *
 * 오류 메시지와 toString에는 비밀번호 값을 넣지 않는다.
 *
 * @param loginId  데모 아이디
 * @param password 데모 비밀번호
 */
@ConfigurationProperties(prefix = "app.demo-login")
public record DemoLoginProperties(
		String loginId,     // 데모 아이디
		String password) {  // 데모 비밀번호(16자 이상)

	static final int MIN_PASSWORD_LENGTH = 16; // 비밀번호 최소 길이

	public DemoLoginProperties {
		// 1. 빈 문자열과 공백만 있는 값은 "없음"으로 본다
		boolean hasLoginId = loginId != null && !loginId.isBlank();
		boolean hasPassword = password != null && !password.isBlank();
		// 2. 하나만 있으면 설정 실수라 기동을 멈춘다
		if (hasLoginId != hasPassword) {
			throw new IllegalStateException(
					"app.demo-login의 login-id(DEMO_LOGIN_ID)와 password(DEMO_LOGIN_PASSWORD)는 둘 다 넣거나 둘 다 비워야 합니다.");
		}
		// 3. 켜는 경우 비밀번호 길이를 확인한다(값은 메시지에 넣지 않는다)
		if (hasPassword && password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(
					"app.demo-login.password(DEMO_LOGIN_PASSWORD)는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
		}
	}

	/** 아이디·비밀번호가 모두 있으면 데모 로그인이 켜진 것이다 */
	public boolean isEnabled() {
		return loginId != null && !loginId.isBlank() && password != null && !password.isBlank();
	}

	/** 로그·오류 출력에 아이디·비밀번호가 나가지 않게 가린다(둘이 한 쌍으로 로그인 정보라 아이디도 가린다) */
	@Override
	public String toString() {
		return "DemoLoginProperties[loginId=****, password=****, enabled=" + isEnabled() + "]";
	}
}
