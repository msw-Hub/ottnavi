package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.AvailabilityAggregation;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.TitleCards;
import com.ottnavi.catalog.service.model.TitleCardsById;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.catalog.service.model.UnitAvailabilities;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작품 목록을 카드(작품 정보 + 서비스별 제공 상태 7개)로 만든다. 검색·탐색·홈 목록이 함께 쓴다.
 * 시청 단위와 제공 상태는 목록 전체를 IN 조건으로 한 번씩만 읽어 작품마다 쿼리하는 N+1을 피한다.
 */
@Component
@RequiredArgsConstructor
public class TitleCardAssembler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TitleRepository titleRepository;
    private final WatchUnitRepository watchUnitRepository;
    private final TitleAvailabilityLoader availabilityLoader;
    private final OttServiceQueryService ottServiceQueryService;

    /** 작품들을 입력 순서 그대로 카드로 만든다. 서비스별 상태는 7개 서비스를 서비스 ID 순으로 모두 담는다 */
    @Transactional(readOnly = true)
    public TitleCards assemble(List<Title> titles) {
        // 1. 이 작품들의 시청 단위(영화 1편 또는 드라마 시즌 1개)를 IN 조건으로 한 번에 조회
        List<Long> titleIds = new ArrayList<>();
        for (Title title : titles) {
            titleIds.add(title.getId());
        }
        List<WatchUnit> units = new ArrayList<>();
        if (!titleIds.isEmpty()) {
            units = watchUnitRepository.findByTitleIdIn(titleIds);
        }

        // 2. 시청 단위들의 서비스별 제공 상태를 한 번에 조회
        List<Long> unitIds = new ArrayList<>();
        for (WatchUnit unit : units) {
            unitIds.add(unit.getId());
        }
        UnitAvailabilities availabilities = availabilityLoader.load(unitIds);

        // 3. 서비스 ID 목록(서비스 ID 순)과 작품별 시청 단위 묶음 준비
        List<Long> serviceIds = serviceIds();
        Map<Long, List<WatchUnit>> unitsByTitle = groupByTitle(units);

        // 4. 작품마다 서비스별 상태를 합쳐 카드를 만들고, 기준일 계산용 확인 시각을 모은다
        List<TitleSearchItemView> items = new ArrayList<>();
        List<Instant> checkedAts = new ArrayList<>();
        for (Title title : titles) {
            List<WatchUnit> titleUnits = unitsByTitle.get(title.getId());
            if (titleUnits == null) {
                titleUnits = List.of();
            }
            List<Map<Long, AvailabilityStatus>> unitRows = new ArrayList<>();
            for (WatchUnit unit : titleUnits) {
                unitRows.add(availabilities.rowsOf(unit.getId()));
                checkedAts.addAll(availabilities.checkedAtOf(unit.getId()));
            }
            // 서비스별로 시즌 상태를 합친다: 시즌 중 하나라도 있음이면 있음, 아니면 모름이 있으면 모름, 전부 없음이면 없음
            Map<Long, AvailabilityStatus> merged = AvailabilityAggregation.mergeByService(serviceIds, unitRows);
            items.add(new TitleSearchItemView(title.getMediaType(), title.getTmdbId(), title.getTitle(),
                    releaseYear(title.getReleaseDate()), title.getPosterPath(), toServiceStatuses(merged)));
        }

        // 5. 데이터 기준일: 이 목록에 제공 상태 기록이 없을 때(빈 목록 포함)만 전체에서 가장 최근 확인 시각을 조회한다
        Instant latestOverall = null;
        if (checkedAts.isEmpty()) {
            latestOverall = availabilityLoader.latestCheckedAt();
        }
        LocalDate dataAsOf = AvailabilityAggregation.dataAsOf(checkedAts, latestOverall, LocalDate.now(KST));
        return new TitleCards(items, dataAsOf);
    }

    /** 작품 ID 목록의 카드를 작품 ID로 찾을 수 있게 돌려준다. DB에 없는 ID는 맵에 없다 */
    @Transactional(readOnly = true)
    public TitleCardsById assembleByIds(List<Long> titleIds) {
        // 1. ID로 작품을 한 번에 읽는다
        List<Title> titles = titleRepository.findAllById(titleIds);

        // 2. 카드로 만들고(입력 순서 = titles 순서) 작품 ID와 짝짓는다
        TitleCards cards = assemble(titles);
        Map<Long, TitleSearchItemView> byTitleId = new HashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            byTitleId.put(titles.get(i).getId(), cards.items().get(i));
        }
        return new TitleCardsById(byTitleId, cards.dataAsOf());
    }

    /**
     * 외부 기준 키(작품 구분 + TMDB ID)로 DB에 있는 작품의 카드를 찾는다. DB에 없는 키는 결과 맵에 들어 있지 않다.
     * 영화와 드라마를 각각 IN 조건 한 번으로 조회한다.
     */
    @Transactional(readOnly = true)
    public Map<TitleKey, TitleSearchItemView> findCardsByKeys(Collection<TitleKey> keys) {
        // 1. 작품 구분별 TMDB ID 모음
        Set<Integer> movieIds = new HashSet<>();
        Set<Integer> tvIds = new HashSet<>();
        for (TitleKey key : keys) {
            if (key.mediaType() == MediaType.MOVIE) {
                movieIds.add(key.tmdbId());
            } else {
                tvIds.add(key.tmdbId());
            }
        }

        // 2. 구분마다 IN 조건으로 작품을 조회
        List<Title> titles = new ArrayList<>();
        if (!movieIds.isEmpty()) {
            titles.addAll(titleRepository.findByMediaTypeAndTmdbIdIn(MediaType.MOVIE, movieIds));
        }
        if (!tvIds.isEmpty()) {
            titles.addAll(titleRepository.findByMediaTypeAndTmdbIdIn(MediaType.TV, tvIds));
        }

        // 3. 카드로 만들어 키로 찾을 수 있게 맵에 담는다
        Map<TitleKey, TitleSearchItemView> cards = new HashMap<>();
        TitleCards assembled = assemble(titles);
        for (TitleSearchItemView item : assembled.items()) {
            cards.put(new TitleKey(item.mediaType(), item.tmdbId()), item);
        }
        return cards;
    }

    private List<Long> serviceIds() {
        List<Long> ids = new ArrayList<>();
        for (OttService service : ottServiceQueryService.listOttServices()) {
            ids.add(service.getId());
        }
        Collections.sort(ids); // 서비스 ID 순
        return ids;
    }

    private Map<Long, List<WatchUnit>> groupByTitle(List<WatchUnit> units) {
        Map<Long, List<WatchUnit>> grouped = new HashMap<>();
        for (WatchUnit unit : units) {
            Long titleId = unit.getTitle().getId();
            List<WatchUnit> group = grouped.get(titleId);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(titleId, group);
            }
            group.add(unit);
        }
        return grouped;
    }

    private Integer releaseYear(LocalDate releaseDate) {
        if (releaseDate == null) {
            return null;
        }
        return releaseDate.getYear();
    }

    private List<ServiceStatus> toServiceStatuses(Map<Long, AvailabilityStatus> statuses) {
        List<ServiceStatus> result = new ArrayList<>();
        for (Map.Entry<Long, AvailabilityStatus> entry : statuses.entrySet()) {
            result.add(new ServiceStatus(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
