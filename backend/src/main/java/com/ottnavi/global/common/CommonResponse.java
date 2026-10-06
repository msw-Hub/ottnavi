package com.ottnavi.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 성공 응답 공통 래퍼. 오류 응답은 이 래퍼를 쓰지 않고 ProblemDetail로 보낸다.
 *
 * @param success 성공 여부(항상 true)
 * @param data    응답 본문
 */
public record CommonResponse<T>(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean success,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) T data) {

	/** 성공 응답으로 감싼다 */
	public static <T> CommonResponse<T> success(T data) {
		return new CommonResponse<>(true, data);
	}
}
