package com.ottnavi.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. 이름이 응답의 errorCode 값이며 docs/api/error-codes.md와 일치해야 한다(ErrorCodeDocumentTest가 검사).
 * message는 ProblemDetail의 detail 기본값이다. 화면 문구는 프론트 errorMessages.ts가 따로 매핑한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."), // 본문 파싱 불가 등. 406·415 같은 기타 4xx는 원래 상태로 응답
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
	EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다."),

	// 작품
	TITLE_NOT_FOUND(HttpStatus.NOT_FOUND, "작품을 찾을 수 없습니다."),

	// 찜
	WISHLIST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "찜은 20개까지 담을 수 있습니다."), // "다 봤음"이 아닌 찜 기준
	WISHLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "찜 항목을 찾을 수 없습니다."),

	// 수집(관리자)
	BATCH_ALREADY_RUNNING(HttpStatus.CONFLICT, "수집이 이미 실행 중입니다."),
	BATCH_ALREADY_COMPLETED(HttpStatus.CONFLICT, "해당 날짜의 수집은 이미 완료되었습니다."),
	BATCH_RECOVERY_REQUIRED(HttpStatus.CONFLICT, "중단된 실행이 있어 먼저 복구해야 합니다."),
	BATCH_NOT_STOPPED(HttpStatus.CONFLICT, "중단된 실행만 복구할 수 있습니다.");

	private final HttpStatus status; // 기본 HTTP 상태
	private final String message;    // detail 기본 문구
}
