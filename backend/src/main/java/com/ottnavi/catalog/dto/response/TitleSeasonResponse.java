package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.TitleSeasonView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 드라마의 시즌 하나(계약 TitleSeason).
 *
 * @param seasonNumber       시즌 번호
 * @param name               시즌 이름
 * @param episodeCount       회차 수
 * @param airYear            방영 연도. 모르면 null
 * @param totalRuntimeMin    시즌 총 시청 시간(분)
 * @param isRuntimeEstimated 러닝타임을 기본값으로 추정했는지
 * @param availabilities     시즌별 서비스 제공 상태(7개)
 */
public record TitleSeasonResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int seasonNumber,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "시즌 1") String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9") int episodeCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2021") Integer airYear,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "520") int totalRuntimeMin,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isRuntimeEstimated,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceAvailabilityResponse> availabilities) {

	/** 시즌 값을 응답으로 바꾼다 */
	public static TitleSeasonResponse from(TitleSeasonView season) {
		List<ServiceAvailabilityResponse> availabilities = new ArrayList<>();
		for (ServiceStatus status : season.availabilities()) {
			availabilities.add(ServiceAvailabilityResponse.from(status));
		}
		return new TitleSeasonResponse(
				season.seasonNumber(),
				season.name(),
				season.episodeCount(),
				season.airYear(),
				season.totalRuntimeMin(),
				season.isRuntimeEstimated(),
				availabilities);
	}
}
