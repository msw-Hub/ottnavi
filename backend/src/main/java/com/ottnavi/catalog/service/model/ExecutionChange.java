package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;

/**
 * 수집 실행에서 제공 상태가 바뀐 한 건(시청 단위 × 서비스). 서비스 안에서만 쓰는 값 묶음이다.
 *
 * @param mediaType    작품 종류
 * @param tmdbId       TMDB 작품 ID
 * @param title        한국어 제목
 * @param seasonNumber 드라마 시즌 번호. 영화는 null
 * @param ottServiceId 상태가 바뀐 서비스 ID
 * @param beforeStatus 이전 상태. null이면 이번에 처음 확인됨
 * @param afterStatus  바뀐 뒤 상태
 */
public record ExecutionChange(
		MediaType mediaType,
		long tmdbId,
		String title,
		Integer seasonNumber,
		long ottServiceId,
		AvailabilityStatus beforeStatus,
		AvailabilityStatus afterStatus) {
}
