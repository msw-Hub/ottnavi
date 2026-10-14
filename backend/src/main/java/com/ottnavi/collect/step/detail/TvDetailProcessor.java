package com.ottnavi.collect.step.detail;

import static com.ottnavi.collect.step.detail.MovieDetailProcessor.LANGUAGE_EN;
import static com.ottnavi.collect.step.detail.MovieDetailProcessor.LANGUAGE_KO;

import com.ottnavi.catalog.domain.rule.RuntimeCalculator;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.domain.rule.TitleDetailRules;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitDetail;
import com.ottnavi.collect.job.CollectJobParameters;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.dto.TmdbSeasonSummary;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * 드라마 상세 Step의 Processor: 드라마 상세와 시즌별 상세를 모두 받아 작품 한 건의 저장용 값을 만든다.
 * 한 시즌이라도 실패하면(404는 작품 건너뜀, 그 밖의 오류는 Step 실패) 이 작품은 저장되지 않아 부분 저장이 없다.
 */
@Slf4j
public class TvDetailProcessor implements ItemProcessor<DetailTarget, TitleDetail>, StepExecutionListener {

    private static final String APPEND_CREDITS = "credits";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TmdbClient tmdbClient;
    private final RuntimeDefaults defaults;
    private final DistributionSummary seasonCount; // 작품당 수집 대상 시즌 수 분포(R11-26 호출량 추정용)

    private LocalDate today; // 방영 여부 기준일. Step 시작 때 Job 파라미터 targetDate로 정한다

    public TvDetailProcessor(TmdbClient tmdbClient, RuntimeDefaults defaults, MeterRegistry meterRegistry) {
        this.tmdbClient = tmdbClient;
        this.defaults = defaults;
        this.seasonCount = DistributionSummary.builder("collect.tv.seasons")
                .description("드라마 한 작품에서 수집한 시즌 수(시즌 0 제외)")
                .register(meterRegistry);
    }

    /** Step이 시작될 때 기준일을 Job 파라미터에서 읽는다 */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        today = stepExecution.getJobParameters().getLocalDate(CollectJobParameters.TARGET_DATE);
    }

    @Override
    public TitleDetail process(DetailTarget target) {
        try {
            TmdbTvDetail ko = tmdbClient.getTv(target.tmdbId(), LANGUAGE_KO, APPEND_CREDITS);
            if (ko == null) {
                return null;
            }
            String englishOverview = null;
            if (ko.overview() == null || ko.overview().isBlank()) {
                TmdbTvDetail en = tmdbClient.getTv(target.tmdbId(), LANGUAGE_EN, null);
                if (en != null) {
                    englishOverview = en.overview();
                }
            }
            boolean animation = RuntimeCalculator.isAnimation(DetailMapper.genreIds(ko.genres()));
            return DetailMapper.fromTv(ko, englishOverview, collectSeasons(target, ko, animation));
        } catch (TmdbNotFoundException e) {
            log.warn("TMDB에 없는 드라마(또는 시즌)라 이 작품을 건너뛴다: titleId={}, tmdbId={}", target.titleId(), target.tmdbId());
            return null;
        }
    }

    /** 수집 대상 시즌(시즌 0 제외)마다 시즌 상세를 받아 방영된 회차가 있는 시즌만 단위로 만든다 */
    private List<UnitDetail> collectSeasons(DetailTarget target, TmdbTvDetail tv, boolean animation) {
        Map<Integer, TmdbSeasonSummary> summaries = new TreeMap<>(); // 시즌 번호순
        if (tv.seasons() != null) {
            for (TmdbSeasonSummary summary : tv.seasons()) {
                summaries.putIfAbsent(summary.seasonNumber(), summary);
            }
        }
        List<Integer> numbers = TitleDetailRules.collectableSeasonNumbers(new ArrayList<>(summaries.keySet()));
        seasonCount.record(numbers.size());

        LocalDate baseDate = today; // 파라미터 없이 단독으로 쓸 때의 대비
        if (baseDate == null) {
            baseDate = LocalDate.now(KST);
        }
        List<UnitDetail> units = new ArrayList<>();
        for (int number : numbers) {
            TmdbTvSeasonDetail season = tmdbClient.getTvSeason(target.tmdbId(), number, LANGUAGE_KO);
            if (season == null) {
                continue;
            }
            UnitDetail unit = DetailMapper.toSeasonUnit(summaries.get(number), season, baseDate, animation, defaults);
            if (unit != null) {
                units.add(unit);
            }
        }
        return units;
    }
}
