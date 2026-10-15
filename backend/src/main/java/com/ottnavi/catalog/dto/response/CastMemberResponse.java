package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.service.model.CastView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 출연진 한 명(계약 CastMember).
 *
 * @param name      배우 이름
 * @param character 배역 이름. 모르면 null
 */
public record CastMemberResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "이정재") String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "성기훈") String character) {

	/** 출연진 값을 응답으로 바꾼다 */
	public static CastMemberResponse from(CastView cast) {
		return new CastMemberResponse(cast.name(), cast.character());
	}
}
