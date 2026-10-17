package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.rule.KrProviders;
import com.ottnavi.catalog.service.model.UnitProviders;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 판정 Step의 Processor: 시청 단위 한 건의 KR 제공처를 TMDB에서 받아 판정 입력으로 바꾼다.
 * 드라마 시즌은 시즌 단위 KR 정보가 비어 있을 때만 작품(시리즈) 단위 제공처로 대신 판정한다(PRD 5.4 / FR-02).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityProcessor implements ItemProcessor<AvailabilityTarget, UnitProviders>, StepExecutionListener {

    private final TmdbClient tmdbClient;

    // 작품 단위 조회 결과 임시 저장: 같은 시리즈의 시즌은 id 순으로 이어서 읽히므로 바로 직전 시리즈 한 건만 들고 있는다
    private int cachedTvId;
    private KrProviders cachedTvProviders; // null이면 아직 저장한 값이 없다

    private int fallbackUnitCount; // 작품 단위로 대신 판정한 시즌 수(Step 로그용)
    private int fallbackCallCount; // 작품 단위 제공처를 실제로 호출한 횟수(Step 로그용)

    /** Step이 시작될 때 임시 저장과 집계를 비운다(빈이 Job 실행 사이에 재사용되기 때문) */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        cachedTvProviders = null;
        fallbackUnitCount = 0;
        fallbackCallCount = 0;
    }

    /** Step이 끝날 때 작품 단위 대체 판정이 쓰인 횟수를 남긴다 */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("시즌 판정에서 작품 단위 제공처로 대체한 시즌 수={}, 작품 단위 호출 수={}", fallbackUnitCount, fallbackCallCount);
        return null;
    }

    /** 제공처 조회가 404면 null을 돌려 이 단위만 건너뛴다. 기존 제공 상태는 그대로 둔다(상태를 지어내지 않는다) */
    @Override
    public UnitProviders process(AvailabilityTarget target) {
        try {
            // 1. 영화는 영화 제공처를 그대로 쓴다
            if (target.unitType() == UnitType.MOVIE) {
                TmdbWatchProvidersResponse movie = tmdbClient.getMovieWatchProviders(target.tmdbId());
                return new UnitProviders(target.watchUnitId(), DetailMapper.toKrProviders(movie));
            }
            // 2. 시즌은 먼저 시즌 단위 제공처로 판정한다
            TmdbWatchProvidersResponse season = tmdbClient.getTvSeasonWatchProviders(target.tmdbId(), target.seasonNumber());
            KrProviders providers = DetailMapper.toKrProviders(season);
            // 3. 시즌 단위에 KR 정보가 있으면 그것만 쓴다(작품 단위는 부르지 않는다)
            if (providers.krDataPresent()) {
                return new UnitProviders(target.watchUnitId(), providers);
            }
            // 4. 시즌 단위 KR 정보가 비어 있으면 작품 단위 제공처로 대신 판정한다
            fallbackUnitCount++;
            return new UnitProviders(target.watchUnitId(), seriesProviders(target.tmdbId()));
        } catch (TmdbNotFoundException e) {
            // 5. TMDB에 제공처 정보가 없으면(404) 이 단위만 건너뛴다
            log.warn("TMDB에 제공처 정보가 없어 이 단위를 건너뛴다: watchUnitId={}, tmdbId={}, seasonNumber={}",
                    target.watchUnitId(), target.tmdbId(), target.seasonNumber());
            return null;
        }
    }

    /** 작품 단위 KR 제공처를 돌려준다. 직전과 같은 시리즈면 호출하지 않고, 작품 단위가 404면 KR 데이터 없음으로 본다 */
    private KrProviders seriesProviders(int tvId) {
        // 1. 직전 시리즈와 같으면 저장해 둔 값을 쓴다
        if (cachedTvProviders != null && cachedTvId == tvId) {
            return cachedTvProviders;
        }
        // 2. 작품 단위 제공처를 호출한다(404면 이 작품에도 KR 정보가 없는 것으로 본다)
        KrProviders providers;
        try {
            fallbackCallCount++;
            providers = DetailMapper.toKrProviders(tmdbClient.getTvWatchProviders(tvId));
        } catch (TmdbNotFoundException e) {
            providers = KrProviders.noKrData();
        }
        // 3. 다음 시즌을 위해 저장해 둔다
        cachedTvId = tvId;
        cachedTvProviders = providers;
        return providers;
    }
}
