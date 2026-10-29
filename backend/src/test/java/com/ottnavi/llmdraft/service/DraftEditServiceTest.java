package com.ottnavi.llmdraft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.llmdraft.domain.rule.DraftTextGuard;
import com.ottnavi.llmdraft.domain.rule.GuardLimits;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.service.model.DraftGrade;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 초안 수정: 자동 검사 재판정, 수정 표시, 실패가 남아도 저장, 없는 초안 (저장소는 목, 검사는 실제 규칙) */
class DraftEditServiceTest {

    private static final String ORIGINAL = "In 1999, a hacker learns the truth about the world.";

    private final DraftRepository draftRepository = mock(DraftRepository.class);
    private final DraftTextGuard guard = new DraftTextGuard(new GuardLimits(0.6, 0.3, 2.0, 1000));
    private final DraftEditService service = new DraftEditService(draftRepository, guard);

    /** 영어가 그대로 돌아와 실패(HANGUL_RATIO_LOW 등)인 초안 */
    private DraftRecord failedDraft() {
        return new DraftRecord("d1", 7L, "movie", "매트릭스", ORIGINAL, "In 1999, a hacker learns the truth.", false,
                List.of("HANGUL_RATIO_LOW", "NUMBER_MISSING"), List.of(), "m", Instant.parse("2026-10-29T01:00:00Z"), 3);
    }

    private DraftRecord savedDraft() {
        ArgumentCaptor<DraftRecord> captor = ArgumentCaptor.forClass(DraftRecord.class);
        verify(draftRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 고친_글이_검사를_통과하면_성공으로_다시_판정하고_수정_표시를_남긴다() {
        // given
        when(draftRepository.findById("d1")).thenReturn(Optional.of(failedDraft()));

        // when
        DraftRecord edited = service.edit("d1", "1999년, 한 해커가 세상의 진실을 알게 된다.");

        // then: 실패 이유가 사라지고 성공이며, 원문·작품·시도 횟수·만든 시각은 그대로다
        assertThat(edited.grade()).isEqualTo(DraftGrade.PASSED);
        assertThat(edited.isGuardPassed()).isTrue();
        assertThat(edited.guardFailures()).isEmpty();
        assertThat(edited.isEdited()).isTrue();
        assertThat(edited.translated()).isEqualTo("1999년, 한 해커가 세상의 진실을 알게 된다.");
        assertThat(edited.original()).isEqualTo(ORIGINAL);
        assertThat(edited.attempts()).isEqualTo(3);
        assertThat(edited.createdAt()).isEqualTo(Instant.parse("2026-10-29T01:00:00Z"));
        assertThat(savedDraft()).isEqualTo(edited);
    }

    @Test
    void 고쳐도_실패가_남으면_저장은_되고_실패_등급과_이유를_돌려준다() {
        // given
        when(draftRepository.findById("d1")).thenReturn(Optional.of(failedDraft()));

        // when: 숫자(1999)가 빠진 글
        DraftRecord edited = service.edit("d1", "한 해커가 세상의 진실을 알게 된다.");

        // then
        assertThat(edited.grade()).isEqualTo(DraftGrade.FAILED);
        assertThat(edited.guardFailures()).containsExactly("NUMBER_MISSING");
        assertThat(edited.isEdited()).isTrue();
        assertThat(savedDraft().isEdited()).isTrue();
    }

    @Test
    void 없는_초안을_고치면_LLM_DRAFT_NOT_FOUND이고_저장하지_않는다() {
        // given
        when(draftRepository.findById("none")).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> service.edit("none", "번역"))
                .isInstanceOfSatisfying(LlmDraftException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LLM_DRAFT_NOT_FOUND));
        verify(draftRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
