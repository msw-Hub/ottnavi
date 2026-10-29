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

	// 데모 로그인
	DEMO_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

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

	// 알림
	ONBOARDING_REQUIRED(HttpStatus.CONFLICT, "내 설정을 먼저 저장해 주세요."), // 내 설정(온보딩)을 저장한 적이 없어 알림 설정을 저장할 수 없음

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
		SERVICE_PRICE_NOT_FOUND(HttpStatus.NOT_FOUND, "이 서비스의 요금 정보를 찾을 수 없습니다."), // 없는 상품 또는 판매 종료 상품

	// 줄거리 번역(관리자, LLM)
	LLM_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "번역 기능이 꺼져 있습니다. 서버에 Gemini 키가 설정되지 않았습니다."),
	LLM_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "번역 호출이 분당 상한을 넘었습니다. 잠시 뒤 다시 시도해 주세요."), // 서버 분당 상한 또는 구글 429
	LLM_TITLE_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "같은 작품은 잠시 뒤에 다시 번역할 수 있습니다."),
	LLM_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 번역 호출 상한을 모두 사용했습니다."),
	LLM_CALL_FAILED(HttpStatus.BAD_GATEWAY, "번역 호출에 실패했습니다."),
	LLM_UPSTREAM_BUSY(HttpStatus.BAD_GATEWAY, "구글 서버가 혼잡해 번역하지 못했습니다. 잠시 뒤 다시 시도해 주세요."), // 구글 503(high demand). 요청 안에서 재시도하지 않는다
	LLM_DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "번역 초안을 찾을 수 없습니다. 보관 기간이 지났거나 이미 반영·삭제됐을 수 있습니다."),
	LLM_BULK_RUNNING(HttpStatus.CONFLICT, "일괄 번역이 이미 실행 중입니다."),
	LLM_NOT_TRANSLATABLE(HttpStatus.CONFLICT, "영어 줄거리가 있는 작품만 번역할 수 있습니다."),
	LLM_DRAFT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "대기 중인 초안이 가득 찼습니다. 반영하거나 삭제한 뒤 다시 시도해 주세요."),
	LLM_APPLY_CONFLICT(HttpStatus.CONFLICT, "수집·재계산·정리가 실행 중이라 줄거리를 바꿀 수 없습니다."),
	LLM_NOT_REVERTIBLE(HttpStatus.CONFLICT, "관리자가 반영한 번역 줄거리만 영어로 되돌릴 수 있습니다.");

	private final HttpStatus status; // 기본 HTTP 상태
	private final String message;    // detail 기본 문구
}
