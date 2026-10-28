package com.ottnavi.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 데모 로그인 설정의 켜짐·꺼짐·기동 실패 판정을 확인한다(스프링 컨텍스트·Docker 불필요).
 * 막으려는 사고: 값 하나만 넣은 설정 실수가 조용히 꺼진 채 지나가는 일, 짧은 비밀번호로 공개 계정이 켜지는 일,
 * 오류 메시지·toString에 비밀번호가 찍히는 일.
 */
class DemoLoginPropertiesTest {

	private static final String LOGIN_ID = "test-demo-id";
	private static final String PASSWORD = "test-only-dummy-password-16+"; // 테스트 전용 더미 값

	@Test
	void 둘_다_비어_있으면_꺼짐이다() {
		// given & when
		DemoLoginProperties properties = new DemoLoginProperties("", "");

		// then
		assertThat(properties.isEnabled()).isFalse();
	}

	@Test
	void 둘_다_null이어도_꺼짐이다() {
		// given & when
		DemoLoginProperties properties = new DemoLoginProperties(null, null);

		// then
		assertThat(properties.isEnabled()).isFalse();
	}

	@Test
	void 둘_다_있고_비밀번호가_16자_이상이면_켜짐이다() {
		// given & when
		DemoLoginProperties properties = new DemoLoginProperties(LOGIN_ID, PASSWORD);

		// then
		assertThat(properties.isEnabled()).isTrue();
	}

	@Test
	void 아이디만_있으면_기동이_실패한다() {
		assertThatThrownBy(() -> new DemoLoginProperties(LOGIN_ID, ""))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 비밀번호만_있으면_기동이_실패하고_메시지에_값이_없다() {
		assertThatThrownBy(() -> new DemoLoginProperties(null, PASSWORD))
				.isInstanceOf(IllegalStateException.class)
				.message().doesNotContain(PASSWORD);
	}

	@Test
	void 비밀번호가_16자_미만이면_기동이_실패하고_메시지에_값이_없다() {
		// given: 15자
		String shortPassword = "short-pass-1234";

		// when & then
		assertThatThrownBy(() -> new DemoLoginProperties(LOGIN_ID, shortPassword))
				.isInstanceOf(IllegalStateException.class)
				.message().doesNotContain(shortPassword);
	}

	@Test
	void toString에_아이디와_비밀번호가_나오지_않는다() {
		// given
		DemoLoginProperties properties = new DemoLoginProperties(LOGIN_ID, PASSWORD);

		// when
		String text = properties.toString();

		// then
		assertThat(text).doesNotContain(PASSWORD);
		assertThat(text).doesNotContain(LOGIN_ID);
	}
}
