package com.ottnavi.provider.api.dto.response;

import com.ottnavi.provider.domain.DataQuality;
import com.ottnavi.provider.domain.OttService;
import com.ottnavi.provider.domain.OttServiceCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OTT 서비스 1개 응답(계약 OttService). 필드는 모두 required이고 null이 오지 않는다.
 *
 * @param ottServiceId 서비스 내부 ID
 * @param code         서비스 코드
 * @param name         화면 표시 이름
 * @param logoPath     TMDB 이미지 상대 경로(URL 조립은 프론트)
 * @param displayOrder 화면 정렬 순서
 * @param dataQuality  데이터 충분도
 */
public record OttServiceResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "NETFLIX") OttServiceCode code,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "넷플릭스") String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "/rK1KljqmbvO9HQa1PBFLILWah72.png") String logoPath,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int displayOrder,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "NORMAL") DataQuality dataQuality) {

	/** 엔티티를 응답으로 바꾼다 */
	public static OttServiceResponse from(OttService ottService) {
		return new OttServiceResponse(
				ottService.getId(),
				ottService.getCode(),
				ottService.getName(),
				ottService.getLogoPath(),
				ottService.getDisplayOrder(),
				ottService.getDataQuality());
	}
}
