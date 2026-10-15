package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.AvailabilityAggregation;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import com.ottnavi.catalog.service.model.TitleSearchResult;
import com.ottnavi.catalog.service.model.UnitAvailabilities;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작품 검색(Task 050). DB에 저장된 작품만 대상으로 제목·원제 부분 일치로 찾고 인기도 순으로 돌려준다.
 * 외부(TMDB) 호출은 하지 않는다. 단건 수집은 후속 Task다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleSearchService {

    private static final char ESCAPE_CHAR = '\\'; // LIKE 이스케이프 문자
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TitleRepository titleRepository;
    private final WatchUnitRepository watchUnitRepository;
    private final TitleAvailabilityLoader availabilityLoader;
    private final OttServiceQueryService ottServiceQueryService;

    /**
     * 검색어로 작품을 한 페이지 찾는다. 검색어는 앞뒤 공백을 뺀 뒤 1자 이상이어야 한다.
     * 예: q=" 기생 " → 제목 또는 원제에 "기생"이 들어간 작품, 인기도 높은 순.
     */
    public TitleSearchResult search(String q, int page, int size) {
        // 1. 검색어 검증: 앞뒤 공백을 빼고 비면 오류
        String keyword = "";
        if (q != null) {
            keyword = q.trim();
        }
        if (keyword.isEmpty()) {
            throw new FieldValidationException("q", "검색어를 입력해 주세요.");
        }

        // 2. 제목·원제 부분 일치 조회(인기도 내림차순)
        String pattern = "%" + escapeLike(keyword) + "%";
        Page<Title> titles = titleRepository.searchByTitle(pattern, ESCAPE_CHAR, PageRequest.of(page, size));

        // 3. 이 페이지 작품들의 시청 단위(영화 1편 또는 드라마 시즌 1개)와 서비스별 제공 상태를 IN 조건으로 한 번씩만 조회
        List<Long> titleIds = new ArrayList<>();
        for (Title title : titles.getContent()) {
            titleIds.add(title.getId());
        }
        List<WatchUnit> units = new ArrayList<>();
        if (!titleIds.isEmpty()) {
            units = watchUnitRepository.findByTitleIdIn(titleIds);
        }
        List<Long> unitIds = new ArrayList<>();
        for (WatchUnit unit : units) {
            unitIds.add(unit.getId());
        }
        UnitAvailabilities availabilities = availabilityLoader.load(unitIds);

        // 4. 서비스 ID 목록(서비스 ID 순)과 작품별 시청 단위 묶음 준비
        List<Long> serviceIds = serviceIds();
        Map<Long, List<WatchUnit>> unitsByTitle = groupByTitle(units);

        // 5. 작품마다 서비스별 상태를 합쳐 항목을 만들고, 기준일 계산용 확인 시각을 모은다
        List<TitleSearchItemView> content = new ArrayList<>();
        List<Instant> checkedAts = new ArrayList<>();
        for (Title title : titles.getContent()) {
            List<WatchUnit> titleUnits = unitsByTitle.get(title.getId());
            if (titleUnits == null) {
                titleUnits = List.of();
            }
            List<Map<Long, AvailabilityStatus>> unitRows = new ArrayList<>();
            for (WatchUnit unit : titleUnits) {
                unitRows.add(availabilities.rowsOf(unit.getId()));
                checkedAts.addAll(availabilities.checkedAtOf(unit.getId()));
            }
            Map<Long, AvailabilityStatus> merged = AvailabilityAggregation.mergeByService(serviceIds, unitRows);
            content.add(new TitleSearchItemView(
                    title.getMediaType(),
                    title.getTmdbId(),
                    title.getTitle(),
                    releaseYear(title.getReleaseDate()),
                    title.getPosterPath(),
                    toServiceStatuses(merged)));
        }

        // 6. 데이터 기준일: 이 페이지에 제공 상태 기록이 없을 때(빈 페이지 포함)만 전체에서 가장 최근 확인 시각을 조회한다
        Instant latestOverall = null;
        if (checkedAts.isEmpty()) {
            latestOverall = availabilityLoader.latestCheckedAt();
        }
        LocalDate dataAsOf = AvailabilityAggregation.dataAsOf(checkedAts, latestOverall, LocalDate.now(KST));

        return new TitleSearchResult(content, page, size, titles.getTotalElements(), titles.getTotalPages(), dataAsOf);
    }

    /** LIKE 패턴의 특수문자(\, %, _)를 문자 그대로 찾도록 이스케이프한다. 예: "50%" → "50\%" */
    static String escapeLike(String keyword) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(c);
        }
        return escaped.toString();
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
