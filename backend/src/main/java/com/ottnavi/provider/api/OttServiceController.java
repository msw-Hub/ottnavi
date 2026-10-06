package com.ottnavi.provider.api;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.provider.api.dto.response.OttServiceResponse;
import com.ottnavi.provider.application.OttServiceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OTT 서비스 공개 API. 문서 어노테이션이 적어 별도 Interface로 나누지 않는다 */
@Slf4j
@Tag(name = "product")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/ott-services")
public class OttServiceController {

	private final OttServiceQueryService ottServiceQueryService;

	/** 국내 OTT 7개 서비스 목록을 정렬 순서대로 반환한다 */
	@Operation(operationId = "listOttServices", summary = "OTT 서비스 목록 조회")
	@GetMapping
	public CommonResponse<List<OttServiceResponse>> listOttServices() {
		return CommonResponse.success(ottServiceQueryService.listOttServices());
	}
}
