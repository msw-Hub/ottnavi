package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.TitleCast;
import com.ottnavi.catalog.domain.entity.TitleGenre;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.repository.TitleCastRepository;
import com.ottnavi.catalog.domain.repository.TitleGenreRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.AvailabilityAggregation;
import com.ottnavi.catalog.domain.rule.TitleDetailRules;
import com.ottnavi.catalog.exception.TitleNotFoundException;
import com.ottnavi.catalog.service.model.CastView;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.TitleDetailView;
import com.ottnavi.catalog.service.model.TitleSeasonView;
import com.ottnavi.catalog.service.model.UnitAvailabilities;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작품 상세 조회(Task 051). DB에 저장된 작품만 조회하고, 없으면 TITLE_NOT_FOUND로 응답한다.
 * 결과는 titleDetail 캐시(24시간)에 저장하고, 수집 Job이 끝나면 캐시를 비운다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleDetailQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TitleRepository titleRepository;
    private final WatchUnitRepository watchUnitRepository;
    private final TitleCastRepository titleCastRepository;
    private final TitleGenreRepository titleGenreRepository;
    private final TitleAvailabilityLoader availabilityLoader;
    private final OttServiceQueryService ottServiceQueryService;

    /**
     * 작품 상세를 조회한다. 캐시 키는 "{mediaType}:{tmdbId}"(예: "movie:496243")다.
     * Redis 오류는 CacheErrorHandler가 무시하므로 캐시가 죽어도 DB로 응답한다.
     */
    @Cacheable(cacheNames = RedisConfig.TITLE_DETAIL, key = "#mediaType.toApiValue() + ':' + #tmdbId")
    public TitleDetailView getDetail(MediaType mediaType, int tmdbId) {
        // 1. 작품 조회. 없으면 404
        Optional<Title> found = titleRepository.findByMediaTypeAndTmdbId(mediaType, tmdbId);
        if (found.isEmpty()) {
            throw new TitleNotFoundException("작품을 찾을 수 없습니다: " + mediaType.toApiValue() + "/" + tmdbId);
        }
        Title title = found.get();

        // 2. 시청 단위(영화 1개 또는 드라마 시즌들)와 그 제공 상태를 한 번씩 조회
        List<WatchUnit> units = watchUnitRepository.findByTitleIdIn(List.of(title.getId()));
        List<Long> unitIds = new ArrayList<>();
        for (WatchUnit unit : units) {
            unitIds.add(unit.getId());
        }
        UnitAvailabilities availabilities = availabilityLoader.load(unitIds);

        // 3. 서비스 ID 목록(서비스 ID 순)
        List<Long> serviceIds = new ArrayList<>();
        for (OttService service : ottServiceQueryService.listOttServices()) {
            serviceIds.add(service.getId());
        }
        Collections.sort(serviceIds);

        // 4. 시청 단위를 영화 단위와 시즌 목록으로 나누고, 작품 단위 상태·확인 시각을 모은다
        WatchUnit movieUnit = null;
        List<TitleSeasonView> seasons = new ArrayList<>();
        List<Map<Long, AvailabilityStatus>> unitRows = new ArrayList<>();
        List<Instant> checkedAts = new ArrayList<>();
        for (WatchUnit unit : units) {
            Map<Long, AvailabilityStatus> rows = availabilities.rowsOf(unit.getId());
            unitRows.add(rows);
            checkedAts.addAll(availabilities.checkedAtOf(unit.getId()));
            if (unit.getUnitType() == UnitType.MOVIE) {
                movieUnit = unit;
            } else {
                seasons.add(toSeason(unit, toServiceStatuses(AvailabilityAggregation.statusByService(serviceIds, rows))));
            }
        }
        // 작품 전체 상태: 서비스별로 시즌 중 하나라도 있음이면 있음, 아니면 모름이 있으면 모름, 전부 없음이면 없음
        List<ServiceStatus> titleStatuses =
                toServiceStatuses(AvailabilityAggregation.mergeByService(serviceIds, unitRows));

        // 5. 영화 러닝타임(드라마는 null·false), 출연진 상위 10명, 장르 이름
        Integer runtimeMin = null;
        boolean isRuntimeEstimated = false;
        if (movieUnit != null) {
            runtimeMin = movieUnit.getTotalRuntimeMin();
            isRuntimeEstimated = movieUnit.isRuntimeEstimated();
        }
        List<CastView> cast = loadCast(title.getId());
        List<String> genres = loadGenres(title.getId());

        // 6. 데이터 기준일: 제공 상태 기록이 없을 때만 전체에서 가장 최근 확인 시각을 조회한다
        Instant latestOverall = null;
        if (checkedAts.isEmpty()) {
            latestOverall = availabilityLoader.latestCheckedAt();
        }
        LocalDate dataAsOf = AvailabilityAggregation.dataAsOf(checkedAts, latestOverall, LocalDate.now(KST));

        // 7. 원제가 비어 있으면 제목으로 대체(계약상 originalTitle은 비어 있지 않은 문자열)
        String originalTitle = title.getOriginalTitle();
        if (originalTitle == null || originalTitle.isBlank()) {
            originalTitle = title.getTitle();
        }

        return new TitleDetailView(
                title.getMediaType(),
                title.getTmdbId(),
                title.getTitle(),
                originalTitle,
                title.getPosterPath(),
                title.getOverview(),
                title.getOverviewLang(),
                genres,
                title.getReleaseDate(),
                runtimeMin,
                isRuntimeEstimated,
                cast,
                titleStatuses,
                seasons,
                dataAsOf);
    }

    private TitleSeasonView toSeason(WatchUnit unit, List<ServiceStatus> statuses) {
        // 시즌 이름이 비어 있으면 "시즌 N"으로 대체
        String name = unit.getName();
        if (name == null || name.isBlank()) {
            name = "시즌 " + unit.getSeasonNumber();
        }
        Integer airYear = null;
        if (unit.getAirDate() != null) {
            airYear = unit.getAirDate().getYear();
        }
        return new TitleSeasonView(
                unit.getSeasonNumber(),
                name,
                unit.getEpisodeCount(),
                airYear,
                unit.getTotalRuntimeMin(),
                unit.isRuntimeEstimated(),
                statuses);
    }

    /** 출연 순서대로 상위 10명만 돌려준다 */
    private List<CastView> loadCast(Long titleId) {
        List<CastView> cast = new ArrayList<>();
        for (TitleCast row : titleCastRepository.findByTitleIdOrderByCastOrder(titleId)) {
            if (cast.size() >= TitleDetailRules.MAX_CAST) {
                break;
            }
            cast.add(new CastView(row.getName(), row.getCharacterName()));
        }
        return cast;
    }

    private List<String> loadGenres(Long titleId) {
        List<String> genres = new ArrayList<>();
        for (TitleGenre row : titleGenreRepository.findWithGenreByTitleId(titleId)) {
            genres.add(row.getGenre().getName());
        }
        return genres;
    }

    private List<ServiceStatus> toServiceStatuses(Map<Long, AvailabilityStatus> statuses) {
        List<ServiceStatus> result = new ArrayList<>();
        for (Map.Entry<Long, AvailabilityStatus> entry : statuses.entrySet()) {
            result.add(new ServiceStatus(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
