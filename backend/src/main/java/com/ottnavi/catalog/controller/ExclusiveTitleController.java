package com.ottnavi.catalog.controller;

import com.ottnavi.catalog.dto.response.ExclusiveTitlePageResponse;
import com.ottnavi.catalog.service.ExclusiveTitleQueryService;
import com.ottnavi.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 서비스별 독점 목록 공개 API(Task 078). 비로그인으로 쓰며 요청 제한이 적용된다 */
@Tag(name = "catalog")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/ott-services/{ottServiceId}/exclusive-titles")
public class ExclusiveTitleController {

	private final ExclusiveTitleQueryService exclusiveTitleQueryService;

	/** 서비스 한 곳의 독점 작품을 인기도 순으로 한 페이지 돌려준다. 없는 서비스면 404 OTT_SERVICE_NOT_FOUND */
	@Operation(operationId = "listServiceExclusiveTitles", summary = "서비스별 독점 목록 조회")
	@GetMapping
	public CommonResponse<ExclusiveTitlePageResponse> listServiceExclusiveTitles(
			@PathVariable @Min(value = 1, message = "1 이상이어야 합니다.") Long ottServiceId,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return CommonResponse.success(ExclusiveTitlePageResponse.from(
				exclusiveTitleQueryService.getPage(ottServiceId, page, size)));
	}
}
