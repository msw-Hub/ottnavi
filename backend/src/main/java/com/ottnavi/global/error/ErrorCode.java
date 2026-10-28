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

	// OTT 서비스
	OTT_SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "서비스를 찾을 수 없습니다."), // 독점 목록의 없는 서비스 ID(Task 078)

	// 찜
	WISHLIST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "찜은 20개까지 담을 수 있습니다."), // "다 봤음"이 아닌 찜 기준
	WISHLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "찜 항목을 찾을 수 없습니다."),

	// 플랜
	PLAN_DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "계산된 플랜을 찾을 수 없습니다. 요금 확인이나 찜 목록 화면에서 다시 계산해 주세요."),
	PLAN_DRAFT_STALE(HttpStatus.CONFLICT, "데이터가 갱신되어 플랜을 다시 계산하고 있습니다."),

	// 수집(관리자)
	BATCH_ALREADY_RUNNING(HttpStatus.CONFLICT, "수집이 이미 실행 중입니다."),
	BATCH_ALREADY_COMPLETED(HttpStatus.CONFLICT, "해당 날짜의 수집은 이미 완료되었습니다."),
	BATCH_RECOVERY_REQUIRED(HttpStatus.CONFLICT, "중단된 실행이 있어 먼저 복구해야 합니다."),
	BATCH_NOT_STOPPED(HttpStatus.CONFLICT, "중단된 실행만 복구할 수 있습니다."),

	// 월 구독요금(관리자)
	PRICE_START_DATE_NOT_AFTER_LAST(HttpStatus.CONFLICT, "적용 시작일은 마지막 월 구독요금의 시작일보다 늦어야 합니다."),
	PRICE_START_DATE_DUPLICATE(HttpStatus.CONFLICT, "같은 적용 시작일의 월 구독요금이 이미 있습니다."),
	PRICE_FIRST_START_DATE_IN_FUTURE(HttpStatus.CONFLICT, "첫 월 구독요금의 적용 시작일은 오늘 또는 그 이전이어야 합니다."),
	PRICE_NOT_CURRENT(HttpStatus.CONFLICT, "지금 적용 중인 월 구독요금만 확인할 수 있습니다."),
	PRICE_NOT_LATEST(HttpStatus.CONFLICT, "가장 최근 월 구독요금만 수정하거나 삭제할 수 있습니다."),
	PRICE_LAST_ROW_CANNOT_DELETE(HttpStatus.CONFLICT, "월 구독요금이 하나뿐이라 삭제할 수 없습니다."),

		// 내 서비스 요금
		SERVICE_PRICE_NOT_FOUND(HttpStatus.NOT_FOUND, "이 서비스의 요금 정보를 찾을 수 없습니다."); // 없는 상품 또는 판매 종료 상품

	private final HttpStatus status; // 기본 HTTP 상태
	private final String message;    // detail 기본 문구
}
