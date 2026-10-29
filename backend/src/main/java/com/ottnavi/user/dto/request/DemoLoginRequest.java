package com.ottnavi.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 데모 계정 로그인 요청(계약 DemoLoginRequest).
 * Bean Validation(@NotBlank 등)을 일부러 쓰지 않는다. 데모 로그인이 꺼져 있을 때 빈 값이 400으로 응답되면
 * 경로가 있다는 사실이 드러나기 때문이다. 빈 값은 서비스가 "틀린 값"(401)으로 처리한다.
 *
 * @param loginId  데모 아이디
 * @param password 데모 비밀번호
 */
public record DemoLoginRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password) {

	/** 로그·오류 출력에 입력값이 나가지 않게 가린다(아이디 칸에 비밀번호를 잘못 넣는 경우도 있어 둘 다 가린다) */
	@Override
	public String toString() {
		return "DemoLoginRequest[loginId=****, password=****]";
	}
}
