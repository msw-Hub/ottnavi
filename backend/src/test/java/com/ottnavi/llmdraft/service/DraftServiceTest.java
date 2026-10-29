package com.ottnavi.llmdraft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.TitleOverviewService;
import com.ottnavi.catalog.service.model.OverviewSourceView;
import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.infra.llm.GeminiClient;
import com.ottnavi.infra.llm.LlmProperties;
import com.ottnavi.infra.llm.exception.LlmCallFailedException;
import com.ottnavi.infra.llm.exception.LlmDisabledException;
import com.ottnavi.infra.llm.exception.LlmUpstreamBusyException;
import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.llmdraft.domain.rule.DraftTextGuard;
import com.ottnavi.llmdraft.domain.rule.GuardLimits;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.repository.LlmQuotaStore;
import com.ottnavi.llmdraft.repository.QuotaDecision;
import com.ottnavi.llmdraft.service.model.DraftGrade;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 번역 초안 한 건 생성: 키 없음·한도 초과·쿨다운·정상·실패·주의 저장·호출 실패 (협력 객체는 목) */
class DraftServiceTest {

    private static final String ORIGINAL = "A detective hunts a killer in a quiet seaside town.";
    private static final String TRANSLATED = "한 형사가 조용한 해안 마을에서 살인범을 쫓는다.";

    private final TitleOverviewService titleOverviewService = mock(TitleOverviewService.class);
    private final GeminiClient geminiClient = mock(GeminiClient.class);
    private final DraftRepository draftRepository = mock(DraftRepository.class);
    private final LlmQuotaStore quotaStore = mock(LlmQuotaStore.class);
    private final LlmDraftProperties draftProperties = new LlmDraftProperties(
            new LlmDraftProperties.Limits(12, 400, 60), new LlmDraftProperties.Draft(Duration.ofDays(7), 500),
            new LlmDraftProperties.Bulk(100, Duration.ZERO, Duration.ZERO, 3, 2, 1),
            new LlmDraftProperties.Guard(0.6, 0.3, 2.0, 1000, 1, 0.8, 0.1, 0.4));
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final DraftService service = new DraftService(titleOverviewService, geminiClient,
            new DraftTextGuard(new GuardLimits(0.6, 0.3, 2.0, 1000)), draftRepository, quotaStore, draftProperties,
            new LlmProperties("test-model", 90, 4096, ""), clock);

    /** createDraft를 부르고 LlmDraftException이 나면 그 오류 코드를 돌려준다(없으면 null) */
    private ErrorCode errorCodeOfCreate(Long titleId) {
        try {
            service.createDraft(titleId);
        } catch (LlmDraftException e) {
            return e.getErrorCode();
        }
        return null;
    }

    private void givenEnglishTitle() {
        when(titleOverviewService.findSource(1L)).thenReturn(Optional.of(
                new OverviewSourceView(1L, MediaType.MOVIE, 100, "살인의 추억", ORIGINAL, "en", false)));
        when(geminiClient.isEnabled()).thenReturn(true);
        when(quotaStore.tryAcquire()).thenReturn(QuotaDecision.ACQUIRED);
    }

    @Test
    void 정상이면_번역해_검사_통과_초안을_저장하고_쿨다운을_시작한다() {
        // given
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn(TRANSLATED);

        // when
        DraftRecord draft = service.createDraft(1L);

        // then
        assertThat(draft.isGuardPassed()).isTrue();
        assertThat(draft.guardFailures()).isEmpty();
        assertThat(draft.translated()).isEqualTo(TRANSLATED);
        assertThat(draft.original()).isEqualTo(ORIGINAL);
        assertThat(draft.model()).isEqualTo("test-model");
        assertThat(draft.attempts()).isEqualTo(1);
        assertThat(draft.warnings()).isEmpty();
        assertThat(draft.grade()).isEqualTo(DraftGrade.PASSED);
        assertThat(draft.mediaType()).isEqualTo("movie");
        verify(draftRepository).save(draft);
        verify(quotaStore).startCooldown(1L);
    }

    @Test
    void 번역문이_검사에서_걸려도_이유와_함께_초안을_저장한다() {
        // given: 영어가 그대로 돌아왔다
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn("A detective hunts a killer in a quiet town.");

        // when
        DraftRecord draft = service.createDraft(1L);

        // then
        assertThat(draft.isGuardPassed()).isFalse();
        assertThat(draft.guardFailures()).contains("HANGUL_RATIO_LOW");
        ArgumentCaptor<DraftRecord> captor = ArgumentCaptor.forClass(DraftRecord.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().isGuardPassed()).isFalse();
    }

    @Test
    void 키가_없으면_상한_카운터를_올리지_않고_LlmDisabledException이다() {
        // given
        when(titleOverviewService.findSource(1L)).thenReturn(Optional.of(
                new OverviewSourceView(1L, MediaType.MOVIE, 100, "살인의 추억", ORIGINAL, "en", false)));
        when(geminiClient.isEnabled()).thenReturn(false);

        // when, then
        assertThatThrownBy(() -> service.createDraft(1L)).isInstanceOf(LlmDisabledException.class);
        verify(quotaStore, never()).tryAcquire();
        verify(geminiClient, never()).translateToKorean(any());
    }

    @Test
    void 없는_작품이면_ResourceNotFoundException이다() {
        // given
        when(titleOverviewService.findSource(9L)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> service.createDraft(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 영어_줄거리가_아니면_LLM_NOT_TRANSLATABLE이다() {
        // given: 이미 한국어
        when(titleOverviewService.findSource(1L)).thenReturn(Optional.of(
                new OverviewSourceView(1L, MediaType.MOVIE, 100, "살인의 추억", TRANSLATED, "ko", false)));

        // when, then
        assertThat(errorCodeOfCreate(1L)).isEqualTo(ErrorCode.LLM_NOT_TRANSLATABLE);
        verify(geminiClient, never()).translateToKorean(any());
    }

    @Test
    void 같은_작품이_쿨다운_중이면_호출하지_않고_LLM_TITLE_COOLDOWN이다() {
        // given
        givenEnglishTitle();
        when(quotaStore.isCoolingDown(1L)).thenReturn(true);

        // when, then
        assertThat(errorCodeOfCreate(1L)).isEqualTo(ErrorCode.LLM_TITLE_COOLDOWN);
        verify(quotaStore, never()).tryAcquire();
    }

    @Test
    void 분당_상한을_넘으면_호출하지_않고_LLM_RATE_LIMITED이다() {
        // given
        givenEnglishTitle();
        when(quotaStore.tryAcquire()).thenReturn(QuotaDecision.MINUTE_EXCEEDED);

        // when, then
        assertThat(errorCodeOfCreate(1L)).isEqualTo(ErrorCode.LLM_RATE_LIMITED);
        verify(geminiClient, never()).translateToKorean(any());
        verify(quotaStore, never()).startCooldown(any());
    }

    @Test
    void 하루_상한을_넘으면_호출하지_않고_LLM_DAILY_LIMIT_EXCEEDED이다() {
        // given
        givenEnglishTitle();
        when(quotaStore.tryAcquire()).thenReturn(QuotaDecision.DAY_EXCEEDED);

        // when, then
        assertThat(errorCodeOfCreate(1L)).isEqualTo(ErrorCode.LLM_DAILY_LIMIT_EXCEEDED);
        verify(geminiClient, never()).translateToKorean(any());
    }

    @Test
    void 대기_초안이_가득_차면_카운터를_올리지_않고_LLM_DRAFT_LIMIT_EXCEEDED이다() {
        // given
        givenEnglishTitle();
        when(draftRepository.countIndexed()).thenReturn(500L);
        when(draftRepository.existsByTitle(1L)).thenReturn(false);
        DraftRecord any = new DraftRecord("d", 5L, "movie", "t", "o", "n", true, List.of(), List.of(), "m", clock.instant(), 1);
        when(draftRepository.findAll()).thenReturn(Collections.nCopies(500, any));

        // when, then
        assertThat(errorCodeOfCreate(1L)).isEqualTo(ErrorCode.LLM_DRAFT_LIMIT_EXCEEDED);
        verify(quotaStore, never()).tryAcquire();
    }

    @Test
    void 대기_초안이_가득_차도_같은_작품의_초안_교체는_허용한다() {
        // given
        givenEnglishTitle();
        when(draftRepository.countIndexed()).thenReturn(500L);
        when(draftRepository.existsByTitle(1L)).thenReturn(true);
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn(TRANSLATED);

        // when
        DraftRecord draft = service.createDraft(1L);

        // then
        assertThat(draft.isGuardPassed()).isTrue();
        verify(draftRepository).save(draft);
    }

    @Test
    void 구글_서버가_혼잡하면_재시도하지_않고_LlmUpstreamBusyException을_그대로_던진다() {
        // given
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenThrow(new LlmUpstreamBusyException("혼잡"));

        // when, then
        assertThatThrownBy(() -> service.createDraft(1L)).isInstanceOf(LlmUpstreamBusyException.class);
        verify(geminiClient, times(1)).translateToKorean(ORIGINAL);
        verify(quotaStore).tryAcquire();
        verify(draftRepository, never()).save(any());
    }

    @Test
    void translate는_쿨다운_검사를_건너뛰면_쿨다운_중이어도_호출하고_카운터를_올리지만_저장하지_않는다() {
        // given
        givenEnglishTitle();
        when(quotaStore.isCoolingDown(1L)).thenReturn(true);
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn(TRANSLATED);

        // when
        DraftRecord draft = service.translate(1L, false);

        // then
        assertThat(draft.translated()).isEqualTo(TRANSLATED);
        assertThat(draft.attempts()).isEqualTo(1);
        verify(quotaStore).tryAcquire();
        verify(draftRepository, never()).save(any());
    }

    @Test
    void translate를_여러_번_부르면_시도마다_분당_하루_카운터를_올린다() {
        // given
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn(TRANSLATED);

        // when: 첫 시도 뒤 쿨다운 검사 없이 재시도
        service.translate(1L, true);
        service.translate(1L, false);

        // then
        verify(quotaStore, times(2)).tryAcquire();
        verify(geminiClient, times(2)).translateToKorean(ORIGINAL);
    }

    @Test
    void 단건_생성은_재시도_없이_한_번만_호출하고_attempts_1로_저장한다() {
        // given: 영어가 그대로 돌아와 실패 등급이 되는 번역
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn("A detective hunts a killer in a quiet town.");

        // when
        DraftRecord draft = service.createDraft(1L);

        // then
        assertThat(draft.grade()).isEqualTo(DraftGrade.FAILED);
        assertThat(draft.attempts()).isEqualTo(1);
        verify(geminiClient, times(1)).translateToKorean(ORIGINAL);
        verify(draftRepository, times(1)).save(draft);
    }

    @Test
    void 실패는_없고_경고만_있으면_주의_초안으로_저장한다() {
        // given: 깨진 띄어쓰기("데 이고")만 있는 번역
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn("한 형사가 조용한 해안 마을에서 살인범을 데 이고 쫓는다.");

        // when
        DraftRecord draft = service.createDraft(1L);

        // then
        assertThat(draft.isGuardPassed()).isTrue();
        assertThat(draft.guardFailures()).isEmpty();
        assertThat(draft.warnings()).containsExactly("SUSPICIOUS_SPACING");
        assertThat(draft.grade()).isEqualTo(DraftGrade.WARNING);
        verify(draftRepository).save(draft);
    }

    @Test
    void 실패와_경고가_함께_있으면_실패_등급이다() {
        // given: 영어 그대로(실패)이고 문장도 "다."로 끝나지 않는다(경고)
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenReturn("A detective hunts a killer in a quiet town.");

        // when
        DraftRecord draft = service.createDraft(1L);

        // then
        assertThat(draft.isGuardPassed()).isFalse();
        assertThat(draft.warnings()).contains("STYLE_NOT_PLAIN");
        assertThat(draft.grade()).isEqualTo(DraftGrade.FAILED);
    }

    @Test
    void 호출이_실패하면_초안을_저장하지_않고_카운터는_올린_채_둔다() {
        // given
        givenEnglishTitle();
        when(geminiClient.translateToKorean(ORIGINAL)).thenThrow(new LlmCallFailedException("실패"));

        // when, then
        assertThatThrownBy(() -> service.createDraft(1L)).isInstanceOf(LlmCallFailedException.class);
        verify(quotaStore).tryAcquire();
        verify(draftRepository, never()).save(any());
    }
}
