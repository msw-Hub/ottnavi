package com.ottnavi.home.dto.response;

import com.ottnavi.catalog.dto.response.TitleSearchItemResponse;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.catalog.service.model.TitleSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 탐색 결과 한 페이지(계약 TitleExplorePage = PageMeta + 데이터 기준일).
 *
 * @param content       이 페이지의 작품들(인기도 높은 순, 없으면 빈 배열)
 * @param page          현재 페이지(0부터)
 * @param size          페이지 크기
 * @param totalElements 전체 건수
 * @param totalPages    전체 페이지 수
 * @param dataAsOf      제공 상태의 데이터 기준일(KST)
 */
public record TitleExplorePageResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TitleSearchItemResponse> content,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "135") long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") int totalPages,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-18") LocalDate dataAsOf) {

	/** 탐색 결과를 응답으로 바꾼다 */
	public static TitleExplorePageResponse from(TitleSearchResult result) {
		List<TitleSearchItemResponse> content = new ArrayList<>();
		for (TitleSearchItemView item : result.content()) {
			content.add(TitleSearchItemResponse.from(item));
		}
		return new TitleExplorePageResponse(content, result.page(), result.size(), result.totalElements(),
				result.totalPages(), result.dataAsOf());
	}
}
