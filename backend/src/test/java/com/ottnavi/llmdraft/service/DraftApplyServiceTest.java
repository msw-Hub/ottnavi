package com.ottnavi.llmdraft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.TitleOverviewService;
import com.ottnavi.catalog.service.model.OverviewSourceView;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.catalog.service.model.TranslatedRevertBatch;
import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.DraftRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 영어로 되돌리기(단건·대량): 번역 확인, 묶음 반복, 캐시 삭제 호출, 수집 중 거절 (협력 객체는 목) */
class DraftApplyServiceTest {

    private final DraftRepository draftRepository = mock(DraftRepository.class);
    private final TitleOverviewService titleOverviewService = mock(TitleOverviewService.class);
    private final ConflictingJobChecker conflictingJobChecker = mock(ConflictingJobChecker.class);

    private final DraftApplyService service = new DraftApplyService(draftRepository, titleOverviewService,
            conflictingJobChecker);

    @Test
    void 대량_되돌리기는_묶음이_빌_때까지_반복하고_묶음마다_상세_캐시를_하나씩_지운다() {
        // given: 두 묶음(2건, 1건) 뒤에 빈 묶음
        Instant before = Instant.parse("2026-04-29T00:00:00Z");
        TitleKey a = new TitleKey(MediaType.MOVIE, 1);
        TitleKey b = new TitleKey(MediaType.MOVIE, 2);
        TitleKey c = new TitleKey(MediaType.TV, 3);
        when(titleOverviewService.revertTranslatedBatch(any(), anyInt())).thenReturn(
                new TranslatedRevertBatch(List.of(a, b), 2),
                new TranslatedRevertBatch(List.of(c), 1),
                new TranslatedRevertBatch(List.of(), 0));

        // when
        int reverted = service.revertTranslatedOverviews(before);

        // then
        assertThat(reverted).isEqualTo(3);
        verify(titleOverviewService, times(3)).revertTranslatedBatch(before, 500);
        verify(titleOverviewService).evictDetailCache(a);
        verify(titleOverviewService).evictDetailCache(b);
        verify(titleOverviewService).evictDetailCache(c);
    }

    @Test
    void 미리보기는_대상_수만_세고_줄거리와_캐시를_바꾸지_않는다() {
        // given
        Instant before = Instant.parse("2026-04-29T00:00:00Z");
        when(titleOverviewService.countTranslatedForRevert(before)).thenReturn(37L);

        // when
        long count = service.countTranslatedOverviewsToRevert(before);

        // then: 되돌리는 메서드(revertTranslatedBatch)와 캐시 삭제는 한 번도 부르지 않는다
        assertThat(count).isEqualTo(37L);
        verify(titleOverviewService, never()).revertTranslatedBatch(any(), anyInt());
        verify(titleOverviewService, never()).clearKoreanOverview(any());
        verify(titleOverviewService, never()).evictDetailCache(any());
    }

    @Test
    void 미리보기도_수집이_실행_중이면_LLM_APPLY_CONFLICT이고_세지_않는다() {
        // given
        when(conflictingJobChecker.isAnyRunning()).thenReturn(true);

        // when, then
        assertThatThrownBy(() -> service.countTranslatedOverviewsToRevert(null))
                .isInstanceOfSatisfying(LlmDraftException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LLM_APPLY_CONFLICT));
        verify(titleOverviewService, never()).countTranslatedForRevert(any());
    }

    @Test
    void 대량_되돌리기의_대상이_없으면_0이고_캐시를_지우지_않는다() {
        // given
        when(titleOverviewService.revertTranslatedBatch(any(), anyInt()))
                .thenReturn(new TranslatedRevertBatch(List.of(), 0));

        // when
        int reverted = service.revertTranslatedOverviews(null);

        // then
        assertThat(reverted).isZero();
        verify(titleOverviewService, never()).evictDetailCache(any());
    }

    @Test
    void 읽은_작품이_하나도_바뀌지_않으면_같은_묶음을_계속_읽지_않고_멈춘다() {
        // given: 읽기는 되지만 그 사이 모두 번역이 아니게 돼 갱신이 0건
        when(titleOverviewService.revertTranslatedBatch(any(), anyInt()))
                .thenReturn(new TranslatedRevertBatch(List.of(new TitleKey(MediaType.MOVIE, 1)), 0));

        // when
        int reverted = service.revertTranslatedOverviews(null);

        // then
        assertThat(reverted).isZero();
        verify(titleOverviewService, times(1)).revertTranslatedBatch(any(), anyInt());
    }

    @Test
    void 수집이_실행_중이면_대량_되돌리기는_LLM_APPLY_CONFLICT이고_아무것도_바꾸지_않는다() {
        // given
        when(conflictingJobChecker.isAnyRunning()).thenReturn(true);

        // when, then
        assertThatThrownBy(() -> service.revertTranslatedOverviews(null))
                .isInstanceOfSatisfying(LlmDraftException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LLM_APPLY_CONFLICT));
        verify(titleOverviewService, never()).revertTranslatedBatch(any(), anyInt());
    }

    @Test
    void 단건_되돌리기는_번역이_아니면_LLM_NOT_REVERTIBLE이다() {
        // given: TMDB가 준 한국어(언어는 ko지만 번역 표시가 없음)
        when(titleOverviewService.findSource(1L)).thenReturn(Optional.of(
                new OverviewSourceView(1L, MediaType.MOVIE, 100, "살인의 추억", "TMDB 한국어", "ko", false)));

        // when, then
        assertThatThrownBy(() -> service.revertToEnglish(1L))
                .isInstanceOfSatisfying(LlmDraftException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LLM_NOT_REVERTIBLE));
        verify(titleOverviewService, never()).clearKoreanOverview(1L);
    }
}
