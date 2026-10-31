package com.ottnavi.infra.analytics;

import com.ottnavi.infra.analytics.dto.GaRunReportRequest;
import com.ottnavi.infra.analytics.dto.GaRunReportResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Google Analytics Data API(v1beta) 호출 목록.
 * name = "ga"는 application.yml의 spring.cloud.openfeign.client.config.ga 키와 같아야 타임아웃·로그 수준이 적용된다.
 * 인증 헤더·재시도 없음·오류 해석은 GaFeignConfig가 붙인다.
 */
@FeignClient(
		name = "ga",
		url = "${app.analytics.base-url:https://analyticsdata.googleapis.com}",
		configuration = GaFeignConfig.class
)
public interface GaDataApi {

	/** 보고서 한 건을 요청한다. POST /v1beta/properties/{propertyId}:runReport */
	@PostMapping("/v1beta/properties/{propertyId}:runReport")
	GaRunReportResponse runReport(@PathVariable("propertyId") String propertyId, @RequestBody GaRunReportRequest request);
}
