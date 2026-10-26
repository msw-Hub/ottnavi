package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.rule.AvailabilityAggregation;
import com.ottnavi.catalog.domain.rule.ExclusiveRules;
import com.ottnavi.catalog.domain.rule.ExclusiveSourceRow;
import com.ottnavi.catalog.domain.rule.ExclusiveTitleGroup;
import com.ottnavi.catalog.service.model.ExclusiveTitleItem;
import com.ottnavi.catalog.service.model.ExclusiveTitleList;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스별 독점 목록을 DB의 제공 상태에서 계산한다(Task 078, FR-04).
 * 판정 규칙은 {@link ExclusiveRules}가 맡고, 이 서비스는 읽기·정렬·화면용 값 조립만 한다.
 * 결과 보관(Redis)은 {@link ExclusiveTitleCacheService}가 맡는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExclusiveTitleCalculator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AvailabilityRepository availabilityRepository;
    private final TitleRepository titleRepository;
    private final OttServiceQueryService ottServiceQueryService;

    /** 7개 서비스 모두의 독점 목록을 한 번에 계산한다. 키는 서비스 ID이고 서비스 7곳이 모두 들어 있다 */
    public Map<Long, ExclusiveTitleList> calculateAll() {
        // 1. 서비스 목록과 제공 상태 전체를 한 번씩 읽는다
        List<OttService> services = ottServiceQueryService.listOttServices();
        List<Long> serviceIds = new ArrayList<>();
        for (OttService service : services) {
            serviceIds.add(service.getId());
        }
        List<ExclusiveSourceRow> rows = availabilityRepository.findAllExclusiveSourceRows();

        // 2. 판정: 서비스별 독점 작품 묶음
        Map<Long, List<ExclusiveTitleGroup>> groupsByService = ExclusiveRules.findExclusiveTitles(rows, serviceIds);

        // 3. 독점 작품들의 정보를 한 번에 읽는다
        Set<Long> titleIds = new HashSet<>();
        for (List<ExclusiveTitleGroup> groups : groupsByService.values()) {
            for (ExclusiveTitleGroup group : groups) {
                titleIds.add(group.titleId());
            }
        }
        Map<Long, Title> titlesById = new HashMap<>();
        for (Title title : titleRepository.findAllById(titleIds)) {
            titlesById.put(title.getId(), title);
        }

        // 4. 데이터 기준일: 판정에 쓴 제공 상태 중 가장 오래된 확인 시각의 날짜(없으면 오늘)
        LocalDate dataAsOf = dataAsOf(rows);

        // 5. 서비스마다 목록을 만든다. 데이터 부족 서비스(쿠팡플레이)도 같은 규칙으로 판정한다.
        //    쿠팡플레이 영화는 모두 모름(구조적 공백)이라 있음이 아니므로 규칙상 독점 영화가 나오지 않고, 드라마 시즌만 판정된다(2026-10-19 사용자 결정)
        Map<Long, ExclusiveTitleList> result = new LinkedHashMap<>();
        for (OttService service : services) {
            List<ExclusiveTitleGroup> groups = groupsByService.get(service.getId());
            if (groups == null) {
                groups = List.of();
            }
            result.put(service.getId(),
                    new ExclusiveTitleList(service.getId(), true, dataAsOf, toItems(groups, titlesById)));
        }
        return result;
    }

    /** 묶음을 화면용 작품 줄로 바꾸고 인기도 높은 순(동률은 작품 ID 순, 인기도 없는 작품은 뒤)으로 정렬한다 */
    private List<ExclusiveTitleItem> toItems(List<ExclusiveTitleGroup> groups, Map<Long, Title> titlesById) {
        // 1. 작품 정보가 있는 묶음만 남긴다(판정 뒤 작품이 지워졌으면 뺀다)
        List<ExclusiveTitleGroup> sorted = new ArrayList<>();
        for (ExclusiveTitleGroup group : groups) {
            if (titlesById.containsKey(group.titleId())) {
                sorted.add(group);
            }
        }

        // 2. 인기도 높은 순으로 정렬한다
        Collections.sort(sorted, new Comparator<ExclusiveTitleGroup>() {
            @Override
            public int compare(ExclusiveTitleGroup a, ExclusiveTitleGroup b) {
                BigDecimal popularityA = titlesById.get(a.titleId()).getPopularity();
                BigDecimal popularityB = titlesById.get(b.titleId()).getPopularity();
                if (popularityA == null && popularityB != null) {
                    return 1;
                }
                if (popularityA != null && popularityB == null) {
                    return -1;
                }
                if (popularityA != null) {
                    int byPopularity = popularityB.compareTo(popularityA);
                    if (byPopularity != 0) {
                        return byPopularity;
                    }
                }
                return Long.compare(a.titleId(), b.titleId());
            }
        });

        // 3. 화면용 줄로 바꾼다
        List<ExclusiveTitleItem> items = new ArrayList<>();
        for (ExclusiveTitleGroup group : sorted) {
            Title title = titlesById.get(group.titleId());
            Integer releaseYear = null;
            if (title.getReleaseDate() != null) {
                releaseYear = title.getReleaseDate().getYear();
            }
            items.add(new ExclusiveTitleItem(title.getMediaType(), title.getTmdbId(), title.getTitle(), releaseYear,
                    title.getPosterPath(), group.seasonNumbers(), group.comparedServiceCount(),
                    group.unknownElsewhereCount()));
        }
        return items;
    }

    private LocalDate dataAsOf(List<ExclusiveSourceRow> rows) {
        Instant oldest = null;
        for (ExclusiveSourceRow row : rows) {
            if (oldest == null || row.checkedAt().isBefore(oldest)) {
                oldest = row.checkedAt();
            }
        }
        List<Instant> checkedAts = new ArrayList<>();
        if (oldest != null) {
            checkedAts.add(oldest);
        }
        return AvailabilityAggregation.dataAsOf(checkedAts, null, LocalDate.now(KST));
    }
}
