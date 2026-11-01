package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.KrProviders;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.service.model.FetchedTitle;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitDetail;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 작품 하나의 상세와 시청 단위별 KR 제공처를 TMDB에서 모두 받아 저장용 묶음(FetchedTitle)으로 만든다.
 * 수집 Job의 상세·판정 Step Processor와 같은 규칙을 쓴다(상세 변환은 MovieDetailProcessor·TvDetailProcessor, 제공처는 AvailabilityProcessor.fetchProviders).
 * TMDB 호출은 모두 여기서 끝낸다: 호출하는 쪽은 트랜잭션 밖에서 부르고, 저장은 받은 값으로 TitleFillService가 한 트랜잭션으로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleTitleFetcher {

    private final TmdbClient tmdbClient;
    private final RuntimeDefaults runtimeDefaults;
    private final MeterRegistry meterRegistry;
    private final Clock clock; // KST 기준 오늘(ClockConfig). 드라마 시즌 방영 여부의 기준일이다

    /**
     * 작품 하나를 TMDB에서 받아 돌려준다. TMDB에 상세가 없으면(404) null이다(호출 쪽이 이 작품만 건너뛴다).
     * 드라마는 시즌 하나라도 호출이 실패하면 예외가 올라가고 작품 전체가 저장되지 않는다(TmdbException).
     */
    public FetchedTitle fetch(MediaType mediaType, int tmdbId) {
        // 1. 작품 상세를 받는다(드라마는 시즌별 상세까지). 404면 건너뛴다
        DetailTarget target = new DetailTarget(0L, mediaType, tmdbId); // titleId는 로그용이라 아직 없는 작품은 0
        TitleDetail detail;
        if (mediaType == MediaType.MOVIE) {
            detail = new MovieDetailProcessor(tmdbClient, runtimeDefaults).process(target);
        } else {
            LocalDate today = LocalDate.now(clock);
            detail = new TvDetailProcessor(tmdbClient, runtimeDefaults, meterRegistry, today).process(target);
        }
        if (detail == null) {
            return null;
        }

        // 2. 시청 단위(영화 1개 또는 방영된 시즌들)마다 KR 제공처를 받는다
        // 작품마다 새로 만든다: 직전 시리즈의 작품 단위 제공처를 들고 있는 상태가 다른 작품과 섞이지 않게 한다
        AvailabilityProcessor availabilityProcessor = new AvailabilityProcessor(tmdbClient);
        Map<Integer, KrProviders> providersBySeason = new HashMap<>(); // 영화의 키는 null
        for (UnitDetail unit : detail.units()) {
            KrProviders providers;
            try {
                providers = availabilityProcessor.fetchProviders(unit.unitType(), tmdbId, unit.seasonNumber());
            } catch (TmdbNotFoundException e) {
                // 3. 제공처 정보가 없으면(404) 이 단위는 KR 데이터 없음(모름)으로 저장한다. 작품을 통째로 버리지 않는다
                log.warn("TMDB에 제공처 정보가 없어 KR 데이터 없음으로 처리한다: mediaType={}, tmdbId={}, seasonNumber={}",
                        mediaType, tmdbId, unit.seasonNumber());
                providers = KrProviders.noKrData();
            }
            providersBySeason.put(unit.seasonNumber(), providers);
        }
        return new FetchedTitle(detail, providersBySeason);
    }
}
