package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.AvailabilityAggregation;
import com.ottnavi.catalog.exception.TitleNotFoundException;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.UnitAvailabilities;
import com.ottnavi.catalog.service.model.WatchUnitSummaries;
import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시청 단위(영화 1편 또는 시즌 1개) 기준 조회(Task 056). 찜처럼 시청 단위를 저장하는 도메인이 쓴다.
 * DB에 저장된 작품만 다루며 TMDB를 호출하지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WatchUnitQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WatchUnitRepository watchUnitRepository;
    private final TitleAvailabilityLoader availabilityLoader;
    private final OttServiceQueryService ottServiceQueryService;

    /**
     * 작품(드라마는 시즌)의 시청 단위 ID를 찾는다. 영화는 seasonNumber가 null이고 드라마는 시즌 번호가 필요하다.
     * 작품이나 시즌이 DB에 없으면 TITLE_NOT_FOUND다.
     */
    public long getWatchUnitId(MediaType mediaType, int tmdbId, Integer seasonNumber) {
        // 1. 작품 구분에 따라 영화 단위 또는 시즌 단위를 찾는다
        Optional<Long> found;
        if (mediaType == MediaType.MOVIE) {
            found = watchUnitRepository.findMovieUnitId(mediaType, tmdbId);
        } else {
            found = watchUnitRepository.findSeasonUnitId(mediaType, tmdbId, seasonNumber);
        }

        // 2. 없으면 404
        if (found.isEmpty()) {
            throw new TitleNotFoundException("작품 또는 시즌을 찾을 수 없습니다: "
                    + mediaType.toApiValue() + "/" + tmdbId + "/" + seasonNumber);
        }
        return found.get();
    }

    /**
     * 시청 단위 여러 개의 요약(제목·시간·서비스별 제공 상태)과 데이터 기준일을 한 번에 만든다.
     * 쿼리는 시청 단위·제공 상태 각각 한 번이라 단위 수와 무관하다. ID가 비면 쿼리 없이 빈 결과와 오늘 날짜를 돌려준다.
     */
    public WatchUnitSummaries loadSummaries(Collection<Long> unitIds) {
        // 1. 대상이 없으면 쿼리하지 않는다
        Map<Long, WatchUnitSummary> byUnitId = new HashMap<>();
        if (unitIds.isEmpty()) {
            return new WatchUnitSummaries(byUnitId, LocalDate.now(KST));
        }

        // 2. 시청 단위(소속 작품 포함)와 제공 상태를 각각 한 번에 조회
        List<WatchUnit> units = watchUnitRepository.findWithTitleByIdIn(unitIds);
        UnitAvailabilities availabilities = availabilityLoader.load(unitIds);

        // 3. 서비스 ID 목록(서비스 ID 순)
        List<Long> serviceIds = new ArrayList<>();
        for (OttService service : ottServiceQueryService.listOttServices()) {
            serviceIds.add(service.getId());
        }
        Collections.sort(serviceIds);

        // 4. 시청 단위마다 요약을 만들고 판정 확인 시각을 모은다
        List<Instant> checkedAts = new ArrayList<>();
        for (WatchUnit unit : units) {
            checkedAts.addAll(availabilities.checkedAtOf(unit.getId()));
            Map<Long, AvailabilityStatus> statuses =
                    AvailabilityAggregation.statusByService(serviceIds, availabilities.rowsOf(unit.getId()));
            List<ServiceStatus> serviceStatuses = new ArrayList<>();
            for (Map.Entry<Long, AvailabilityStatus> entry : statuses.entrySet()) {
                serviceStatuses.add(new ServiceStatus(entry.getKey(), entry.getValue()));
            }
            byUnitId.put(unit.getId(), toSummary(unit, serviceStatuses));
        }

        // 5. 데이터 기준일: 판정 행이 하나도 없을 때만 전체 최신 확인 시각으로 대체한다(상세 조회와 같은 규칙)
        Instant latestOverall = null;
        if (checkedAts.isEmpty()) {
            latestOverall = availabilityLoader.latestCheckedAt();
        }
        LocalDate dataAsOf = AvailabilityAggregation.dataAsOf(checkedAts, latestOverall, LocalDate.now(KST));
        return new WatchUnitSummaries(byUnitId, dataAsOf);
    }

    private WatchUnitSummary toSummary(WatchUnit unit, List<ServiceStatus> serviceStatuses) {
        // 시즌만 이름을 채운다. 이름이 비어 있으면 "시즌 N"으로 대체
        String seasonName = null;
        if (unit.getSeasonNumber() != null) {
            seasonName = unit.getName();
            if (seasonName == null || seasonName.isBlank()) {
                seasonName = "시즌 " + unit.getSeasonNumber();
            }
        }
        return new WatchUnitSummary(
                unit.getId(),
                unit.getTitle().getMediaType(),
                unit.getTitle().getTmdbId(),
                unit.getTitle().getTitle(),
                unit.getTitle().getPosterPath(),
                unit.getSeasonNumber(),
                seasonName,
                unit.getTotalRuntimeMin(),
                unit.isRuntimeEstimated(),
                serviceStatuses);
    }
}
