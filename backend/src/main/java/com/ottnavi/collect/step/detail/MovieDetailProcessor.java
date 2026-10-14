package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * 영화 상세 Step의 Processor: 작품 한 건의 TMDB 상세를 받아 저장용 값으로 바꾼다.
 * TMDB 호출은 chunk 트랜잭션 안에서 일어난다(수집 배치만 인정된 예외, TECH T-2).
 */
@Slf4j
@RequiredArgsConstructor
public class MovieDetailProcessor implements ItemProcessor<DetailTarget, TitleDetail> {

    static final String LANGUAGE_KO = "ko-KR";
    static final String LANGUAGE_EN = "en-US";
    private static final String APPEND_CREDITS = "credits";

    private final TmdbClient tmdbClient;
    private final RuntimeDefaults defaults;

    /** TMDB에 없는 작품(404)이면 null을 돌려 이 작품만 건너뛴다. 기존 시청 단위·제공 상태는 건드리지 않는다 */
    @Override
    public TitleDetail process(DetailTarget target) {
        try {
            // 1. 한국어 상세를 받는다(없으면 건너뛴다)
            TmdbMovieDetail ko = tmdbClient.getMovie(target.tmdbId(), LANGUAGE_KO, APPEND_CREDITS);
            if (ko == null) {
                return null;
            }
            // 2. 한국어 줄거리가 비었을 때만 영어 상세에서 줄거리를 가져온다
            // 한국어 줄거리가 비었을 때만 영어로 한 번 더 부른다(모든 작품을 이중 호출하지 않는다)
            String englishOverview = null;
            if (ko.overview() == null || ko.overview().isBlank()) {
                TmdbMovieDetail en = tmdbClient.getMovie(target.tmdbId(), LANGUAGE_EN, null);
                if (en != null) {
                    englishOverview = en.overview();
                }
            }
            // 3. 저장용 값으로 바꿔 돌려준다
            return DetailMapper.fromMovie(ko, englishOverview, defaults);
        } catch (TmdbNotFoundException e) {
            // 4. TMDB에 없으면(404) 이 작품만 건너뛴다
            log.warn("TMDB에 없는 영화라 건너뛴다: titleId={}, tmdbId={}", target.titleId(), target.tmdbId());
            return null;
        }
    }
}
