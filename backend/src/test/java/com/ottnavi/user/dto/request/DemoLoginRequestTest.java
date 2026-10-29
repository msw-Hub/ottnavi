package com.ottnavi.user.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 데모 로그인 요청이 로그·오류 출력으로 새지 않는지 확인한다. 막으려는 사고: 요청 객체를 로그에 찍었을 때 비밀번호가 남는 일 */
class DemoLoginRequestTest {

	@Test
	void toString에_입력한_아이디와_비밀번호가_나오지_않는다() {
		// given
		DemoLoginRequest request = new DemoLoginRequest("typed-id", "typed-dummy-password");

		// when
		String text = request.toString();

		// then
		assertThat(text).doesNotContain("typed-dummy-password");
		assertThat(text).doesNotContain("typed-id");
	}
}
