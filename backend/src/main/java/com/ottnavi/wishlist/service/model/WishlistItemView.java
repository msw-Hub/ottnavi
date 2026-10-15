package com.ottnavi.wishlist.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import java.time.Instant;
import java.util.List;

/** 찜 목록의 한 줄(계약 WishlistItem). 시청 단위 요약에 찜 정보와 "지금 볼 수 있는지"를 더한 값이다 */
public record WishlistItemView(
		long wishlistItemId,
		MediaType mediaType,
		int tmdbId,
		String title,
		String posterPath,
		Integer seasonNumber,
		String seasonName,
		WishlistPriority priority,
		int requiredMin,
		boolean isRuntimeEstimated,
		List<ServiceStatus> availabilities,
		boolean isWatchableNow,   // 내가 구독·무료 이용 중인 서비스에서 이미 볼 수 있는지
		Instant watchedAt) {      // "다 봤음" 시각. 안 봤으면 null
}
