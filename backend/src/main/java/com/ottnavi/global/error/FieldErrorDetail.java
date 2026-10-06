package com.ottnavi.global.error;

/**
 * VALIDATION_FAILED 응답의 fieldErrors 항목. 계약(openapi.yaml)의 FieldError 스키마와 같은 형태다.
 *
 * @param field   오류가 난 요청 필드명(쿼리·경로 파라미터는 파라미터명)
 * @param message 필드 오류 메시지
 */
public record FieldErrorDetail(String field, String message) {
}
