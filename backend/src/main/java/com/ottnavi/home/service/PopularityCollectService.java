package com.ottnavi.home.service;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.home.job.PopularityProperties;
import com.ottnavi.home.service.model.PopularityCollectSummary;
import com.ottnavi.home.service.model.PopularityItem;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.exception.TmdbException;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.enums.OttServiceCode;
import com.ottnavi.provider.service.OttServiceQueryService;
import com.ottnavi.provider.service.ProviderCollectService;
import com.ottnavi.provider.service.model.DiscoverTarget;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 서비스별 인기 순위 스냅샷 수집(Task 107): 서비스 7곳 × 영화·드라마별로 TMDB discover(인기순) 한 쪽을 받아 오늘 날짜로 저장한다.
 * 클래스에 @Transactional을 붙이지 않는다. TMDB 호출은 트랜잭션 밖이어야 하고, 저장은 PopularitySnapshotService가 자기 트랜잭션을 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopularityCollectService {

	private final PopularityProperties properties;
	private final TmdbClient tmdbClient;
	private final ProviderCollectService providerCollectService;
	private final OttServiceQueryService ottServiceQueryService;
	private final PopularitySnapshotService snapshotService;
	private final Clock clock;

	/**
	 * 오늘(KST) 날짜의 스냅샷을 서비스·매체별로 새로 받아 저장한다. 같은 날 다시 실행하면 같은 자리를 덮어쓴다.
	 * 한 자리(서비스 × 매체)의 TMDB 호출이 실패하면 warn 로그만 남기고 그 자리는 건너뛴다(이전 스냅샷은 그대로 남는다).
	 */
	public PopularityCollectSummary collect() {
		// 1. 스냅샷 날짜와 서비스 코드별 서비스 정보, 서비스별 TMDB 제공처 묶음을 준비한다
		LocalDate snapshotDate = LocalDate.now(clock);
		Map<OttServiceCode, OttService> servicesByCode = new HashMap<>();
		for (OttService service : ottServiceQueryService.listOttServices()) {
			servicesByCode.put(service.getCode(), service);
		}
		List<DiscoverTarget> targets = providerCollectService.findDiscoverTargets();

		int slots = 0;
		int failedSlots = 0;
		int savedRows = 0;
		for (DiscoverTarget target : targets) {
			// 2. 제공처 묶음이 가리키는 서비스를 찾고, 못 찾으면 건너뛴다
			OttService service = servicesByCode.get(target.serviceCode());
			if (service == null) {
				log.warn("인기 수집 대상 서비스를 찾지 못했다: serviceCode={}", target.serviceCode());
				continue;
			}

			// 3. 영화·드라마를 각각 받아 저장한다
			for (MediaType mediaType : MediaType.values()) {
				slots++;
				try {
					List<PopularityItem> items = fetchTop(target.withWatchProviders(), mediaType);
					savedRows += snapshotService.replace(snapshotDate, service, mediaType, items);
				} catch (TmdbException e) {
					// 4. TMDB 호출 실패는 그 자리만 건너뛴다(예상 가능한 실패)
					failedSlots++;
					log.warn("인기 순위 호출이 실패해 건너뛴다: serviceCode={}, mediaType={}, error={}", target.serviceCode(),
							mediaType, e.toString());
				}
			}
		}
		log.info("인기 순위 수집 종료: 날짜={}, 대상={}곳, 실패={}곳, 저장={}행", snapshotDate, slots, failedSlots, savedRows);
		return new PopularityCollectSummary(snapshotDate, slots, failedSlots, savedRows);
	}

	/** 보관 일수를 넘긴 스냅샷을 지우고 지운 행 수를 돌려준다. 오늘을 포함해 최근 retentionDays개 날짜만 남는다 */
	public int purgeOld() {
		LocalDate cutoffDate = LocalDate.now(clock).minusDays(properties.retentionDays() - 1L);
		int deleted = snapshotService.purgeBefore(cutoffDate);
		log.info("오래된 인기 스냅샷을 지웠다: 기준 날짜 이전={}, 삭제={}행", cutoffDate, deleted);
		return deleted;
	}

	/** TMDB에서 인기순 한 쪽을 받아 상위 topN개를 인기 작품 목록으로 바꾼다(트랜잭션 밖 호출) */
	private List<PopularityItem> fetchTop(String withWatchProviders, MediaType mediaType) {
		if (mediaType == MediaType.MOVIE) {
			return PopularityItemMapper.fromMovies(tmdbClient.discoverPopularMovies(withWatchProviders, 1), properties.topN());
		}
		return PopularityItemMapper.fromTvs(tmdbClient.discoverPopularTvs(withWatchProviders, 1), properties.topN());
	}
}
