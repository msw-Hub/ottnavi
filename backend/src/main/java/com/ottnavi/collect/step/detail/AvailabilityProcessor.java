package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.service.model.UnitProviders;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/** 판정 Step의 Processor: 시청 단위 한 건의 KR 제공처를 TMDB에서 받아 판정 입력으로 바꾼다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityProcessor implements ItemProcessor<AvailabilityTarget, UnitProviders> {

    private final TmdbClient tmdbClient;

    /** 제공처 조회가 404면 null을 돌려 이 단위만 건너뛴다. 기존 제공 상태는 그대로 둔다(상태를 지어내지 않는다) */
    @Override
    public UnitProviders process(AvailabilityTarget target) {
        try {
            TmdbWatchProvidersResponse response;
            if (target.unitType() == UnitType.MOVIE) {
                response = tmdbClient.getMovieWatchProviders(target.tmdbId());
            } else {
                response = tmdbClient.getTvSeasonWatchProviders(target.tmdbId(), target.seasonNumber());
            }
            return new UnitProviders(target.watchUnitId(), DetailMapper.toKrProviders(response));
        } catch (TmdbNotFoundException e) {
            log.warn("TMDB에 제공처 정보가 없어 이 단위를 건너뛴다: watchUnitId={}, tmdbId={}, seasonNumber={}",
                    target.watchUnitId(), target.tmdbId(), target.seasonNumber());
            return null;
        }
    }
}
