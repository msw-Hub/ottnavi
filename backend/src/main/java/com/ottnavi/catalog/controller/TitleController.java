package com.ottnavi.catalog.controller;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.dto.response.TitleDetailResponse;
import com.ottnavi.catalog.dto.response.TitleSearchPageResponse;
import com.ottnavi.catalog.service.TitleDetailQueryService;
import com.ottnavi.catalog.service.TitleSearchService;
import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.error.FieldValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 작품 공개 API(검색·상세). 비로그인으로 쓰며 요청 제한이 적용된다 */
@Slf4j
@Tag(name = "catalog")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/titles")
public class TitleController {

	private final TitleSearchService titleSearchService;
	private final TitleDetailQueryService titleDetailQueryService;

	/** 제목·원제로 DB에 저장된 작품을 검색한다(인기도 순) */
	@Operation(operationId = "searchTitles", summary = "작품 검색")
	@GetMapping
	public CommonResponse<TitleSearchPageResponse> searchTitles(
			@RequestParam String q,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return CommonResponse.success(TitleSearchPageResponse.from(titleSearchService.search(q, page, size)));
	}

	/** 작품 상세와 서비스별 제공 상태를 돌려준다. 없는 작품이면 404 TITLE_NOT_FOUND */
	@Operation(operationId = "getTitleDetail", summary = "작품 상세 조회")
	@GetMapping("/{mediaType}/{tmdbId}")
	public CommonResponse<TitleDetailResponse> getTitleDetail(
			@Parameter(schema = @Schema(type = "string", allowableValues = {"movie", "tv"})) @PathVariable String mediaType,
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 2147483647, message = "2147483647 이하여야 합니다.") @PathVariable Long tmdbId) {
		return CommonResponse.success(TitleDetailResponse.from(
				titleDetailQueryService.getDetail(parseMediaType(mediaType), tmdbId.intValue())));
	}

	/** 경로의 작품 구분을 enum으로 바꾼다. API는 소문자(movie/tv)만 받는다 */
	private MediaType parseMediaType(String value) {
		if ("movie".equals(value)) {
			return MediaType.MOVIE;
		}
		if ("tv".equals(value)) {
			return MediaType.TV;
		}
		throw new FieldValidationException("mediaType", "movie 또는 tv여야 합니다.");
	}
}
