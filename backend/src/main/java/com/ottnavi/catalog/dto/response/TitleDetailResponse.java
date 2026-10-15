package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.service.model.CastView;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.TitleDetailView;
import com.ottnavi.catalog.service.model.TitleSeasonView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 작품 상세(계약 TitleDetail).
 *
 * @param mediaType          작품 구분(movie/tv)
 * @param tmdbId             TMDB ID
 * @param title              한국어 제목
 * @param originalTitle      원제
 * @param posterPath         포스터 상대 경로. 없으면 null
 * @param overview           줄거리. 없으면 null
 * @param overviewLang       줄거리 언어(ko/en). overview가 null이면 null
 * @param genres             장르 이름들
 * @param releaseDate        개봉일(영화) 또는 첫 방영일(드라마). 모르면 null
 * @param runtimeMin         영화 러닝타임(분). 드라마는 null
 * @param isRuntimeEstimated runtimeMin이 추정값인지. 드라마는 false
 * @param cast               주요 출연진(상위 10명)
 * @param availabilities     작품 전체 서비스별 제공 상태(7개)
 * @param seasons            드라마의 시즌들(시즌 번호 순). 영화는 빈 배열
 * @param dataAsOf           제공 상태의 데이터 기준일
 */
public record TitleDetailResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "tv") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "93405") long tmdbId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "오징어 게임") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "오징어 게임") String originalTitle,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String posterPath,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String overview,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"ko", "en"}) String overviewLang,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> genres,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2021-09-17") LocalDate releaseDate,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer runtimeMin,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isRuntimeEstimated,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CastMemberResponse> cast,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceAvailabilityResponse> availabilities,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TitleSeasonResponse> seasons,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-08") LocalDate dataAsOf) {

	/** 상세 조회 결과를 응답으로 바꾼다 */
	public static TitleDetailResponse from(TitleDetailView view) {
		List<CastMemberResponse> cast = new ArrayList<>();
		for (CastView member : view.cast()) {
			cast.add(CastMemberResponse.from(member));
		}
		List<ServiceAvailabilityResponse> availabilities = new ArrayList<>();
		for (ServiceStatus status : view.availabilities()) {
			availabilities.add(ServiceAvailabilityResponse.from(status));
		}
		List<TitleSeasonResponse> seasons = new ArrayList<>();
		for (TitleSeasonView season : view.seasons()) {
			seasons.add(TitleSeasonResponse.from(season));
		}
		return new TitleDetailResponse(
				view.mediaType().toApiValue(),
				view.tmdbId(),
				view.title(),
				view.originalTitle(),
				view.posterPath(),
				view.overview(),
				view.overviewLang(),
				view.genres(),
				view.releaseDate(),
				view.runtimeMin(),
				view.isRuntimeEstimated(),
				cast,
				availabilities,
				seasons,
				view.dataAsOf());
	}
}
