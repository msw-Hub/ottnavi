package com.ottnavi.collect.step.detail;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.rule.KrProviders;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitDetail;
import com.ottnavi.infra.tmdb.dto.TmdbCastMember;
import com.ottnavi.infra.tmdb.dto.TmdbCredits;
import com.ottnavi.infra.tmdb.dto.TmdbEpisode;
import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbProviderRef;
import com.ottnavi.infra.tmdb.dto.TmdbRegionProviders;
import com.ottnavi.infra.tmdb.dto.TmdbSeasonSummary;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** DetailMapper 단위 테스트(Task 044). TMDB 응답 객체를 Spring·DB 없이 규칙 입력으로 바꾸는 부분만 본다 */
class DetailMapperTest {

    private static final RuntimeDefaults DEFAULTS = new RuntimeDefaults(120, 45, 24);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static TmdbProviderRef ref(int id) {
        return new TmdbProviderRef(id, "P" + id, null, null);
    }

    @Test
    void 응답이_없거나_KR_키가_없으면_KR_데이터_없음이다() {
        assertThat(DetailMapper.toKrProviders(null)).isEqualTo(KrProviders.noKrData());
        assertThat(DetailMapper.toKrProviders(new TmdbWatchProvidersResponse(1, null))).isEqualTo(KrProviders.noKrData());
        assertThat(DetailMapper.toKrProviders(new TmdbWatchProvidersResponse(1, Map.of()))).isEqualTo(KrProviders.noKrData());
        // KR이 아닌 나라만 있는 경우도 같다
        TmdbRegionProviders us = new TmdbRegionProviders(null, List.of(ref(8)), null, null);
        assertThat(DetailMapper.toKrProviders(new TmdbWatchProvidersResponse(1, Map.of("US", us))))
                .isEqualTo(KrProviders.noKrData());
    }

    @Test
    void KR은_있는데_구독이_없으면_빈_집합이고_대여_구매는_넣지_않는다() {
        TmdbRegionProviders kr = new TmdbRegionProviders("link", null, List.of(ref(2)), List.of(ref(3)));

        KrProviders result = DetailMapper.toKrProviders(new TmdbWatchProvidersResponse(1, Map.of("KR", kr)));

        assertThat(result.krDataPresent()).isTrue();
        assertThat(result.flatrateProviderIds()).isEmpty();
    }

    @Test
    void 구독_제공처_번호만_모은다() {
        TmdbRegionProviders kr = new TmdbRegionProviders("link", List.of(ref(8), ref(337)), List.of(ref(2)), null);

        KrProviders result = DetailMapper.toKrProviders(new TmdbWatchProvidersResponse(1, Map.of("KR", kr)));

        assertThat(result.flatrateProviderIds()).containsExactlyInAnyOrder(8, 337);
    }

    @Test
    void 방영된_회차가_없는_시즌은_단위로_만들지_않는다() {
        TmdbSeasonSummary summary = new TmdbSeasonSummary(810002, 2, "시즌 2", "2027-03-01", 12, null);
        TmdbTvSeasonDetail season = new TmdbTvSeasonDetail(810002L, 2, "시즌 2", "2027-03-01", null, List.of(
                new TmdbEpisode(1, "1화", "2027-03-01", 60, "standard"), new TmdbEpisode(2, "2화", null, null, "standard")));

        assertThat(DetailMapper.toSeasonUnit(summary, season, TODAY, false, DEFAULTS)).isNull();
    }

    @Test
    void 시즌_단위의_회차_수는_시즌_요약이_아니라_시즌_상세의_회차_목록_개수다() {
        TmdbSeasonSummary summary = new TmdbSeasonSummary(810001, 1, "시즌 1", "2023-03-01", 12, "/s.jpg");
        TmdbTvSeasonDetail season = new TmdbTvSeasonDetail(810001L, 1, null, null, null, List.of(
                new TmdbEpisode(1, "1화", "2023-03-01", 60, "standard"), new TmdbEpisode(2, "2화", "2023-03-08", null, "standard")));

        UnitDetail unit = DetailMapper.toSeasonUnit(summary, season, TODAY, false, DEFAULTS);

        assertThat(unit.unitType()).isEqualTo(UnitType.SEASON);
        assertThat(unit.runtime().episodeCount()).isEqualTo(2);
        assertThat(unit.runtime().totalRuntimeMin()).isEqualTo(105); // 60 + 기본값 45
        assertThat(unit.name()).isEqualTo("시즌 1"); // 상세에 이름이 없으면 요약 이름
        assertThat(unit.posterPath()).isEqualTo("/s.jpg");
        assertThat(unit.airDate()).isEqualTo(LocalDate.of(2023, 3, 1)); // 상세에 날짜가 없으면 요약 날짜
    }

    @Test
    void 출연진은_이름이_없는_사람을_빼고_order순_상위_10명으로_자른다() {
        List<TmdbCastMember> members = new java.util.ArrayList<>();
        members.add(new TmdbCastMember(1, " ", "역", 0, null)); // 이름이 비어 있다
        for (int i = 1; i <= 12; i++) {
            members.add(new TmdbCastMember(100 + i, "배우 " + i, "역 " + i, 12 - i, null)); // order가 거꾸로
        }
        TmdbMovieDetail movie = new TmdbMovieDetail(900001, "제목", null, "줄거리", null, null, "2024-05-01", 100,
                null, "ko", 1.0, 1.0, new TmdbCredits(members));

        TitleDetail detail = DetailMapper.fromMovie(movie, null, DEFAULTS);

        assertThat(detail.cast()).hasSize(10);
        assertThat(detail.cast().get(0).name()).isEqualTo("배우 12"); // order 0
        assertThat(detail.cast()).noneMatch(c -> c.name().isBlank());
    }

    @Test
    void 영화는_MOVIE_단위_하나이고_제목이_없으면_원제를_쓴다() {
        TmdbMovieDetail movie = new TmdbMovieDetail(900001, "", "Original", null, null, null, "", null, null, null,
                null, null, null);

        TitleDetail detail = DetailMapper.fromMovie(movie, "English", DEFAULTS);

        assertThat(detail.title()).isEqualTo("Original");
        assertThat(detail.overview()).isEqualTo("English");
        assertThat(detail.overviewLang()).isEqualTo("en");
        assertThat(detail.releaseDate()).isNull(); // 빈 문자열은 미정
        assertThat(detail.units()).hasSize(1);
        assertThat(detail.units().get(0).unitType()).isEqualTo(UnitType.MOVIE);
        assertThat(detail.units().get(0).runtime().totalRuntimeMin()).isEqualTo(120); // runtime이 없어 기본값
    }
}
