package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.rule.CastCandidate;
import com.ottnavi.catalog.domain.rule.EpisodeRuntime;
import com.ottnavi.catalog.domain.rule.KrProviders;
import com.ottnavi.catalog.domain.rule.OverviewPick;
import com.ottnavi.catalog.domain.rule.RuntimeCalculator;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import com.ottnavi.catalog.domain.rule.TitleDetailRules;
import com.ottnavi.catalog.service.model.GenreInfo;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitDetail;
import com.ottnavi.infra.tmdb.dto.TmdbCastMember;
import com.ottnavi.infra.tmdb.dto.TmdbCredits;
import com.ottnavi.infra.tmdb.dto.TmdbEpisode;
import com.ottnavi.infra.tmdb.dto.TmdbGenre;
import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbProviderRef;
import com.ottnavi.infra.tmdb.dto.TmdbRegionProviders;
import com.ottnavi.infra.tmdb.dto.TmdbSeasonSummary;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** TMDB 상세·시즌·제공처 응답 -> 규칙 입력 값과 저장용 값(TitleDetail 등) 변환. 응답 DTO를 이 밖으로 넘기지 않는다 */
final class DetailMapper {

    private static final String REGION = "KR";
    private static final int MAX_TITLE_LENGTH = 300; // title.title, original_title 컬럼 길이
    private static final int MAX_PATH_LENGTH = 200; // poster_path, backdrop_path, profile_path 컬럼 길이
    private static final int MAX_LANGUAGE_LENGTH = 10; // original_language 컬럼 길이
    private static final int MAX_STATUS_LENGTH = 30; // tv_status 컬럼 길이
    private static final int MAX_PERSON_NAME_LENGTH = 200; // title_cast.name 컬럼 길이
    private static final int MAX_CHARACTER_LENGTH = 300; // title_cast.character_name 컬럼 길이

    private DetailMapper() {
    }

    /** 영화 상세 -> 저장용 값. englishOverview는 한국어 줄거리가 비었을 때만 받아 온 영어 줄거리(없으면 null) */
    static TitleDetail fromMovie(TmdbMovieDetail movie, String englishOverview, RuntimeDefaults defaults) {
        OverviewPick overview = TitleDetailRules.pickOverview(movie.overview(), englishOverview);
        // 영화는 MOVIE 단위 1개. 이름·포스터·방영일은 title 값을 쓰므로 비운다(ERD watch_unit)
        UnitDetail unit = new UnitDetail(UnitType.MOVIE, null, null, null, null, null,
                RuntimeCalculator.forMovie(movie.runtime(), defaults));
        return new TitleDetail(MediaType.MOVIE, Math.toIntExact(movie.id()), chooseTitle(movie.title(), movie.originalTitle()),
                cut(movie.originalTitle(), MAX_TITLE_LENGTH), overview.overview(), overview.overviewLang(),
                TitleDetailRules.parseDate(movie.releaseDate()), cut(movie.posterPath(), MAX_PATH_LENGTH),
                cut(movie.backdropPath(), MAX_PATH_LENGTH), cut(movie.originalLanguage(), MAX_LANGUAGE_LENGTH), null,
                decimal(movie.popularity(), 3), decimal(movie.voteAverage(), 2), toGenres(movie.genres()),
                toCast(movie.credits()), List.of(unit));
    }

    /** 드라마 상세 + 이미 만든 시즌 단위들 -> 저장용 값 */
    static TitleDetail fromTv(TmdbTvDetail tv, String englishOverview, List<UnitDetail> units) {
        OverviewPick overview = TitleDetailRules.pickOverview(tv.overview(), englishOverview);
        return new TitleDetail(MediaType.TV, Math.toIntExact(tv.id()), chooseTitle(tv.name(), tv.originalName()),
                cut(tv.originalName(), MAX_TITLE_LENGTH), overview.overview(), overview.overviewLang(),
                TitleDetailRules.parseDate(tv.firstAirDate()), cut(tv.posterPath(), MAX_PATH_LENGTH),
                cut(tv.backdropPath(), MAX_PATH_LENGTH), cut(tv.originalLanguage(), MAX_LANGUAGE_LENGTH),
                cut(tv.status(), MAX_STATUS_LENGTH), decimal(tv.popularity(), 3), decimal(tv.voteAverage(), 2),
                toGenres(tv.genres()), toCast(tv.credits()), units);
    }

    /**
     * 시즌 상세 -> 시즌 단위. 방영된 회차가 0개면 null(저장하지 않는다, 확정 결정 1).
     * 회차 수는 시즌 상세의 회차 목록 개수다(시즌 요약의 episode_count는 쓰지 않는다, 확정 결정 2).
     *
     * @param today 방영 여부 기준일(Job 파라미터 targetDate)
     */
    static UnitDetail toSeasonUnit(TmdbSeasonSummary summary, TmdbTvSeasonDetail season, LocalDate today,
                                   boolean animation, RuntimeDefaults defaults) {
        List<TmdbEpisode> episodes = season.episodes();
        if (episodes == null) {
            episodes = List.of();
        }
        List<EpisodeRuntime> runtimes = new ArrayList<>();
        for (TmdbEpisode episode : episodes) {
            runtimes.add(new EpisodeRuntime(TitleDetailRules.parseDate(episode.airDate()), episode.runtime()));
        }
        RuntimeResult runtime = RuntimeCalculator.forSeason(runtimes, today, animation, defaults);
        if (runtime.airedEpisodeCount() == 0) {
            return null;
        }
        String name = firstText(season.name(), summary.name());
        LocalDate airDate = TitleDetailRules.parseDate(season.airDate());
        if (name == null) {
            name = "시즌 " + summary.seasonNumber(); // 이름이 없으면 시즌 번호로 대신한다
        }
        if (airDate == null) {
            airDate = TitleDetailRules.parseDate(summary.airDate()); // 시즌 상세에 방영일이 없으면 요약의 값을 쓴다
        }
        return new UnitDetail(UnitType.SEASON, summary.seasonNumber(), Math.toIntExact(summary.id()),
                cut(name, MAX_TITLE_LENGTH),
                cut(firstText(season.posterPath(), summary.posterPath()), MAX_PATH_LENGTH),
                airDate, runtime);
    }

    /**
     * 제공처 응답 -> 판정 입력. KR 키가 없으면 noKrData, KR은 있는데 flatrate가 없으면 빈 집합(대여·구매만 있는 경우)이다.
     * flatrate(구독)만 넣는다(PRD FR-02).
     */
    static KrProviders toKrProviders(TmdbWatchProvidersResponse response) {
        if (response == null || response.results() == null || !response.results().containsKey(REGION)) {
            return KrProviders.noKrData();
        }
        TmdbRegionProviders kr = response.results().get(REGION);
        if (kr == null || kr.flatrate() == null) {
            return KrProviders.of(Set.of());
        }
        Set<Integer> ids = new HashSet<>();
        for (TmdbProviderRef ref : kr.flatrate()) {
            ids.add(ref.providerId());
        }
        return KrProviders.of(ids);
    }

    /** 장르 목록 -> 저장용 값. id 목록(애니메이션 판별용)은 호출하는 쪽이 따로 뽑는다 */
    static List<GenreInfo> toGenres(List<TmdbGenre> genres) {
        if (genres == null) {
            return List.of();
        }
        List<GenreInfo> result = new ArrayList<>();
        for (TmdbGenre genre : genres) {
            result.add(new GenreInfo(genre.id(), genre.name()));
        }
        return result;
    }

    /** 장르 id 목록 */
    static List<Integer> genreIds(List<TmdbGenre> genres) {
        if (genres == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (TmdbGenre genre : genres) {
            ids.add(genre.id());
        }
        return ids;
    }

    /** 출연진 -> 이름이 있는 사람만 order순 상위 10명 */
    private static List<CastCandidate> toCast(TmdbCredits credits) {
        if (credits == null || credits.cast() == null) {
            return List.of();
        }
        List<CastCandidate> candidates = new ArrayList<>();
        for (TmdbCastMember member : credits.cast()) {
            // title_cast.name이 NOT NULL이라 이름이 없는 사람은 건너뛴다
            if (member == null || member.name() == null || member.name().isBlank()) {
                continue;
            }
            candidates.add(toCandidate(member));
        }
        return TitleDetailRules.topCast(candidates);
    }

    private static CastCandidate toCandidate(TmdbCastMember member) {
        return new CastCandidate(member.id(), cut(member.name(), MAX_PERSON_NAME_LENGTH),
                cut(member.character(), MAX_CHARACTER_LENGTH), cut(member.profilePath(), MAX_PATH_LENGTH),
                member.order());
    }

    /** 한국어 제목이 없으면 원제를 쓴다. 둘 다 없으면 null(기존 제목을 유지한다) */
    private static String chooseTitle(String title, String originalTitle) {
        return cut(firstText(title, originalTitle), MAX_TITLE_LENGTH);
    }

    private static String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /** Double을 NUMERIC 자릿수(scale)에 맞춘 BigDecimal로 바꾼다. 비어 있으면 null */
    private static BigDecimal decimal(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
