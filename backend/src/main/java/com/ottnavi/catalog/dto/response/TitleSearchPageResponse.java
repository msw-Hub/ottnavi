package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.catalog.service.model.TitleSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 검색 결과 한 페이지(계약 TitleSearchPage = PageMeta + 수집 실패 여부·데이터 기준일).
 *
 * @param content                이 페이지의 작품들(없으면 빈 배열)
 * @param page                   현재 페이지(0부터)
 * @param size                   페이지 크기
 * @param totalElements          전체 건수
 * @param totalPages             전체 페이지 수
 * @param isExternalCollectFailed 외부(TMDB) 수집 실패 여부. 단건 수집을 도입하기 전까지 항상 false
 * @param dataAsOf               제공 상태의 데이터 기준일
 */
public record TitleSearchPageResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TitleSearchItemResponse> content,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "135") long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") int totalPages,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isExternalCollectFailed,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-08") LocalDate dataAsOf) {

	/** 검색 결과를 응답으로 바꾼다. MVP는 DB 조회만 하므로 외부 수집 실패는 항상 false다 */
	public static TitleSearchPageResponse from(TitleSearchResult result) {
		List<TitleSearchItemResponse> content = new ArrayList<>();
		for (TitleSearchItemView item : result.content()) {
			content.add(TitleSearchItemResponse.from(item));
		}
		return new TitleSearchPageResponse(
				content,
				result.page(),
				result.size(),
				result.totalElements(),
				result.totalPages(),
				false,
				result.dataAsOf());
	}
}
