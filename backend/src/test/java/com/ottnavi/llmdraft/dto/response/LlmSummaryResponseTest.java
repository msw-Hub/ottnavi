package com.ottnavi.llmdraft.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.service.model.OverviewTranslationStats;
import com.ottnavi.llmdraft.service.model.CandidatePageView;
import com.ottnavi.llmdraft.service.model.QuotaUsage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 번역 현황 요약의 번역 통계 변환 */
class LlmSummaryResponseTest {

    private CandidatePageView view(OverviewTranslationStats stats) {
        return new CandidatePageView(List.of(), 0, 20, 0, 0, 0, new QuotaUsage(0, 400, 12), "test-model", true, stats);
    }

    @Test
    void 번역이_없으면_번역_작품_수는_0이고_가장_오래된_시각은_null이다() {
        // given
        CandidatePageView view = view(new OverviewTranslationStats(0, null));

        // when
        LlmSummaryResponse summary = LlmSummaryResponse.from(view);

        // then
        assertThat(summary.translatedCount()).isZero();
        assertThat(summary.oldestTranslatedAt()).isNull();
    }

    @Test
    void 번역이_있으면_개수와_가장_오래된_시각을_KST로_돌려준다() {
        // given
        CandidatePageView view = view(new OverviewTranslationStats(37, Instant.parse("2026-10-29T01:00:00Z")));

        // when
        LlmSummaryResponse summary = LlmSummaryResponse.from(view);

        // then
        assertThat(summary.translatedCount()).isEqualTo(37);
        assertThat(summary.oldestTranslatedAt()).hasToString("2026-10-29T10:00+09:00");
    }
}
