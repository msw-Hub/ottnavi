package com.ottnavi.wishlist.dto.response;

import com.ottnavi.catalog.dto.response.ServiceAvailabilityResponse;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.service.model.WishlistItemView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 찜 목록의 한 줄(계약 WishlistItem). 드라마는 시즌 단위다.
 *
 * @param wishlistItemId     찜 항목 ID
 * @param mediaType          작품 구분(movie/tv)
 * @param tmdbId             TMDB ID
 * @param title              한국어 제목
 * @param posterPath         TMDB 포스터 상대 경로. 없으면 null
 * @param seasonNumber       드라마 시즌 번호. 영화는 null
 * @param seasonName         시즌 이름. 영화는 null
 * @param priority           우선순위
 * @param requiredMin        다 보는 데 필요한 시간(분)
 * @param isRuntimeEstimated requiredMin이 추정값인지
 * @param availabilities     서비스별 제공 상태(7개)
 * @param isWatchableNow     내가 구독·무료 이용 중인 서비스에서 이미 볼 수 있는지
 * @param watchedAt          "다 봤음" 시각. 안 봤으면 null
 */
public record WishlistItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") long wishlistItemId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"movie", "tv"}, example = "tv") String mediaType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "93405") long tmdbId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "오징어 게임") String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String posterPath,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1") Integer seasonNumber,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "시즌 1") String seasonName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MUST") WishlistPriority priority,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "500") int requiredMin,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isRuntimeEstimated,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceAvailabilityResponse> availabilities,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isWatchableNow,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant watchedAt) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static WishlistItemResponse from(WishlistItemView view) {
		List<ServiceAvailabilityResponse> availabilities = new ArrayList<>();
		for (ServiceStatus status : view.availabilities()) {
			availabilities.add(ServiceAvailabilityResponse.from(status));
		}
		return new WishlistItemResponse(
				view.wishlistItemId(),
				view.mediaType().toApiValue(),
				view.tmdbId(),
				view.title(),
				view.posterPath(),
				view.seasonNumber(),
				view.seasonName(),
				view.priority(),
				view.requiredMin(),
				view.isRuntimeEstimated(),
				availabilities,
				view.isWatchableNow(),
				view.watchedAt());
	}
}
