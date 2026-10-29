package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 작품 상세 소규모 규칙 단위 테스트(Task 042): 시즌 0 제외, 줄거리 대체, 출연진 상위 10명, 날짜 변환 */
class TitleDetailRulesTest {

    private static CastCandidate cast(long id, Integer order) {
        return new CastCandidate(id, "배우" + id, null, null, order);
    }

    @Test
    void 시즌_0은_수집_대상에서_뺀다() {
        List<Integer> result = TitleDetailRules.collectableSeasonNumbers(List.of(0, 1, 2));

        assertThat(result).containsExactly(1, 2);
    }

    @Test
    void 시즌_목록이_null이면_빈_목록이다() {
        assertThat(TitleDetailRules.collectableSeasonNumbers(null)).isEmpty();
    }

    @Test
    void 한국어_줄거리가_있으면_그대로_쓰고_언어는_ko이다() {
        OverviewPick pick = TitleDetailRules.pickOverview("한국어 줄거리", "English overview");

        assertThat(pick).isEqualTo(new OverviewPick("한국어 줄거리", "ko"));
    }

    @Test
    void 한국어_줄거리가_null_빈값_공백이면_영어로_대체하고_언어는_en이다() {
        assertThat(TitleDetailRules.pickOverview(null, "English")).isEqualTo(new OverviewPick("English", "en"));
        assertThat(TitleDetailRules.pickOverview("", "English")).isEqualTo(new OverviewPick("English", "en"));
        assertThat(TitleDetailRules.pickOverview("   ", "English")).isEqualTo(new OverviewPick("English", "en"));
    }

    @Test
    void 줄거리가_둘_다_없으면_내용도_언어도_null이다() {
        assertThat(TitleDetailRules.pickOverview("", null)).isEqualTo(new OverviewPick(null, null));
    }

    @Test
    void 기존이_번역이고_새_응답이_영어면_기존_번역과_번역_표시를_유지한다() {
        OverviewMerge merge = TitleDetailRules.mergeOverview("관리자 번역", "ko", true, "English overview", "en");

        assertThat(merge).isEqualTo(new OverviewMerge("관리자 번역", "ko", true));
    }

    @Test
    void 기존이_번역이고_새_응답이_한국어면_새_한국어로_교체하고_번역_표시를_비운다() {
        OverviewMerge merge = TitleDetailRules.mergeOverview("관리자 번역", "ko", true, "TMDB 한국어", "ko");

        assertThat(merge).isEqualTo(new OverviewMerge("TMDB 한국어", "ko", false));
    }

    @Test
    void 기존이_TMDB_한국어이고_새_응답이_영어면_새_영어로_교체한다() {
        OverviewMerge merge = TitleDetailRules.mergeOverview("TMDB 한국어", "ko", false, "English overview", "en");

        assertThat(merge).isEqualTo(new OverviewMerge("English overview", "en", false));
    }

    @Test
    void 기존이_TMDB_한국어이고_새_응답도_한국어면_새_값으로_교체한다() {
        OverviewMerge merge = TitleDetailRules.mergeOverview("옛 한국어", "ko", false, "새 한국어", "ko");

        assertThat(merge).isEqualTo(new OverviewMerge("새 한국어", "ko", false));
    }

    @Test
    void 기존이_영어이고_새_응답도_영어면_새_값으로_교체한다() {
        OverviewMerge merge = TitleDetailRules.mergeOverview("Old", "en", false, "New", "en");

        assertThat(merge).isEqualTo(new OverviewMerge("New", "en", false));
    }

    @Test
    void 기존이_영어이고_새_응답이_한국어면_새_한국어로_교체한다() {
        OverviewMerge merge = TitleDetailRules.mergeOverview("Old", "en", false, "새 한국어", "ko");

        assertThat(merge).isEqualTo(new OverviewMerge("새 한국어", "ko", false));
    }

    @Test
    void 새_줄거리가_null이면_기존_줄거리와_언어와_번역_표시를_유지한다() {
        assertThat(TitleDetailRules.mergeOverview("번역", "ko", true, null, null))
                .isEqualTo(new OverviewMerge("번역", "ko", true));
        assertThat(TitleDetailRules.mergeOverview("TMDB 한국어", "ko", false, null, null))
                .isEqualTo(new OverviewMerge("TMDB 한국어", "ko", false));
        assertThat(TitleDetailRules.mergeOverview("Old", "en", false, null, null))
                .isEqualTo(new OverviewMerge("Old", "en", false));
    }

    @Test
    void 기존_줄거리가_없으면_새_값을_쓴다() {
        assertThat(TitleDetailRules.mergeOverview(null, null, false, "English", "en"))
                .isEqualTo(new OverviewMerge("English", "en", false));
        assertThat(TitleDetailRules.mergeOverview(null, null, false, "한국어", "ko"))
                .isEqualTo(new OverviewMerge("한국어", "ko", false));
    }

    @Test
    void 출연진은_상위_10명만_남긴다() {
        List<CastCandidate> cast = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            cast.add(cast(100 + i, i));
        }

        List<CastCandidate> top = TitleDetailRules.topCast(cast);

        assertThat(top).hasSize(10);
        assertThat(top.get(0).personTmdbId()).isEqualTo(100);
        assertThat(top.get(9).personTmdbId()).isEqualTo(109);
    }

    @Test
    void 출연진은_order_순으로_정렬하고_order가_없으면_뒤로_보낸다() {
        List<CastCandidate> top = TitleDetailRules.topCast(List.of(cast(1, null), cast(2, 5), cast(3, 0)));

        assertThat(top).extracting(CastCandidate::personTmdbId).containsExactly(3L, 2L, 1L);
    }

    @Test
    void 출연진이_10명_미만이거나_null이어도_문제없다() {
        assertThat(TitleDetailRules.topCast(List.of(cast(1, 0)))).hasSize(1);
        assertThat(TitleDetailRules.topCast(null)).isEmpty();
    }

    @Test
    void 날짜_문자열을_변환하고_빈_값이나_잘못된_형식은_null이다() {
        assertThat(TitleDetailRules.parseDate("2023-03-01")).isEqualTo(LocalDate.of(2023, 3, 1));
        assertThat(TitleDetailRules.parseDate("")).isNull();
        assertThat(TitleDetailRules.parseDate(null)).isNull();
        assertThat(TitleDetailRules.parseDate("2023-00-00")).isNull();
    }
}
