package com.ottnavi.provider.service.model;

import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.enums.ProviderTier;
import java.time.Instant;

/**
 * 관리자 화면용 TMDB 제공처 한 곳(서버 안 값 묶음).
 *
 * @param providerId   TMDB 제공처 ID(그대로 PK)
 * @param tmdbName     TMDB 원본 이름
 * @param ottServiceId 연결된 서비스 ID. 미매핑·관련 없음이면 null
 * @param tier         요금 tier
 * @param firstSeenAt  처음 발견된 시각
 * @param mappingState 매핑 상태
 */
public record AdminProviderView(
		Long providerId,
		String tmdbName,
		Long ottServiceId,
		ProviderTier tier,
		Instant firstSeenAt,
		MappingState mappingState) {

	/** 엔티티를 값 묶음으로 바꾼다 */
	public static AdminProviderView from(TmdbProvider provider) {
		Long ottServiceId = null;
		OttService ottService = provider.getOttService();
		if (ottService != null) {
			ottServiceId = ottService.getId();
		}
		return new AdminProviderView(
				provider.getProviderId().longValue(),
				provider.getTmdbName(),
				ottServiceId,
				provider.getTier(),
				provider.getFirstSeenAt(),
				provider.getMappingState());
	}
}
