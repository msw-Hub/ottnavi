package com.ottnavi.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 페이지네이션 응답의 공통 틀(계약 PageMeta + content). Spring Page를 그대로 직렬화하지 않는다.
 *
 * @param content       이 페이지의 항목
 * @param page          현재 페이지(0부터)
 * @param size          페이지 크기
 * @param totalElements 전체 건수
 * @param totalPages    전체 페이지 수
 */
public record PageResponse<T>(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> content,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int totalPages) {

	/**
	 * 전체 페이지 수를 계산해 만든다. 예: 전체 41건, 크기 20 → 3쪽.
	 * size가 1 미만이면 0으로 나누지 않도록 0쪽으로 본다(컨트롤러 검증이 막지만 방어).
	 */
	public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
		// 1. 방어: 크기가 1 미만이면 쪽 수를 계산하지 않는다
		if (size < 1) {
			return new PageResponse<>(content, page, size, totalElements, 0);
		}

		// 2. 올림 나눗셈으로 쪽 수를 구한다
		int totalPages = (int) ((totalElements + size - 1) / size);
		return new PageResponse<>(content, page, size, totalElements, totalPages);
	}
}
