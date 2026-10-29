package com.ottnavi.llmdraft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.service.TitleOverviewService;
import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.infra.llm.GeminiClient;
import com.ottnavi.infra.llm.exception.LlmCallFailedException;
import com.ottnavi.infra.llm.exception.LlmDisabledException;
import com.ottnavi.infra.llm.exception.LlmRateLimitedException;
import com.ottnavi.infra.llm.exception.LlmUpstreamBusyException;
import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.BulkRunStatusRepository;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.service.model.BulkRunState;
import com.ottnavi.llmdraft.service.model.BulkRunStatus;
import com.ottnavi.llmdraft.service.model.BulkStopReason;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/** 일괄 번역: 처리 건수 집계, 실패·주의 자동 재시도와 최선 결과 저장, 중단 사유별 즉시 중단, 호출 실패 연속 판정, 한 번에 하나 (협력 객체는 목) */
class DraftBulkServiceTest {

    private final DraftService draftService = mock(DraftService.class);
    private final DraftRepository draftRepository = mock(DraftRepository.class);
    private final BulkRunStatusRepository bulkRunStatusRepository = mock(BulkRunStatusRepository.class);
    private final TitleOverviewService titleOverviewService = mock(TitleOverviewService.class);
    private final GeminiClient geminiClient = mock(GeminiClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LlmDraftProperties properties = new LlmDraftProperties(
            new LlmDraftProperties.Limits(12, 400, 60), new LlmDraftProperties.Draft(Duration.ofDays(7), 500),
            new LlmDraftProperties.Bulk(100, Duration.ZERO, Duration.ZERO, 3, 2, 1),
            new LlmDraftProperties.Guard(0.6, 0.3, 2.0, 1000, 1, 0.8, 0.1, 0.4));

    private final DraftBulkService service = new DraftBulkService(draftService, draftRepository,
            bulkRunStatusRepository, titleOverviewService, geminiClient, properties, clock);

    private BulkRunStatus initial(int total) {
        return new BulkRunStatus("bulk-1", BulkRunState.RUNNING, total, 0, 0, 0, null, clock.instant(), null, 0);
    }

    /** 성공(true) 또는 실패(false) 등급 초안. 실패는 자동 재시도 대상이다 */
    private DraftRecord draft(boolean passed) {
        return new DraftRecord("d", 1L, "movie", "t", "o", "n", passed, List.of(), List.of(), "m", clock.instant(), 1);
    }

    private DraftRecord passedDraft(String draftId) {
        return new DraftRecord(draftId, 1L, "movie", "t", "o", "n", true, List.of(), List.of(), "m", clock.instant(), 1);
    }

    /** 실패는 없고 경고만 있는 주의 등급 초안 */
    private DraftRecord warningDraft(String draftId) {
        return new DraftRecord(draftId, 1L, "movie", "t", "o", "n", true, List.of(), List.of("SUSPICIOUS_SPACING"), "m",
                clock.instant(), 1);
    }

    private DraftRecord failedDraft(String draftId) {
        return new DraftRecord(draftId, 1L, "movie", "t", "o", "n", false, List.of("NUMBER_MISSING"), List.of(), "m",
                clock.instant(), 1);
    }

    /** runBulk가 저장한 초안(작품 하나를 처리했다면 한 번만 저장돼야 한다) */
    private DraftRecord savedDraft() {
        ArgumentCaptor<DraftRecord> captor = ArgumentCaptor.forClass(DraftRecord.class);
        verify(draftRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    /** runBulk가 마지막으로 저장한 진행 상황 */
    private BulkRunStatus lastSaved() {
        ArgumentCaptor<BulkRunStatus> captor = ArgumentCaptor.forClass(BulkRunStatus.class);
        verify(bulkRunStatusRepository, atLeastOnce()).save(captor.capture());
        List<BulkRunStatus> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    @Test
    void 끝까지_처리하면_완료이고_검사에서_걸린_초안을_센다() {
        // given
        when(draftService.translate(1L, true)).thenReturn(draft(true));
        when(draftService.translate(2L, true)).thenReturn(draft(false));
        when(draftService.translate(2L, false)).thenReturn(draft(false));   // 실패는 자동으로 다시 번역한다

        // when
        service.runBulk(initial(2), List.of(1L, 2L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.COMPLETED);
        assertThat(status.doneCount()).isEqualTo(2);
        assertThat(status.guardFailedCount()).isEqualTo(1);
        assertThat(status.failedCount()).isEqualTo(0);
        assertThat(status.stopReason()).isNull();
        assertThat(status.finishedAt()).isNotNull();
    }

    @Test
    void 키가_없으면_즉시_DISABLED로_멈추고_다음_작품을_처리하지_않는다() {
        // given
        when(draftService.translate(1L, true)).thenThrow(new LlmDisabledException("키 없음"));

        // when
        service.runBulk(initial(3), List.of(1L, 2L, 3L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.STOPPED);
        assertThat(status.stopReason()).isEqualTo(BulkStopReason.DISABLED);
        verify(draftService, times(1)).translate(anyLong(), eq(true));
    }

    @Test
    void 구글_429면_즉시_RATE_LIMITED로_멈춘다() {
        // given: 첫 작품은 성공, 둘째에서 429
        when(draftService.translate(1L, true)).thenReturn(draft(true));
        when(draftService.translate(2L, true)).thenThrow(new LlmRateLimitedException("429"));

        // when
        service.runBulk(initial(3), List.of(1L, 2L, 3L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.stopReason()).isEqualTo(BulkStopReason.RATE_LIMITED);
        assertThat(status.doneCount()).isEqualTo(1);
        verify(draftService, times(2)).translate(anyLong(), eq(true));
    }

    @Test
    void 하루_상한을_넘으면_DAILY_LIMIT으로_멈춘다() {
        // given
        when(draftService.translate(1L, true)).thenThrow(new LlmDraftException(ErrorCode.LLM_DAILY_LIMIT_EXCEEDED));

        // when
        service.runBulk(initial(2), List.of(1L, 2L));

        // then
        assertThat(lastSaved().stopReason()).isEqualTo(BulkStopReason.DAILY_LIMIT);
    }

    @Test
    void 대기_초안이_가득_차면_DRAFT_LIMIT으로_멈춘다() {
        // given
        when(draftService.translate(1L, true)).thenThrow(new LlmDraftException(ErrorCode.LLM_DRAFT_LIMIT_EXCEEDED));

        // when
        service.runBulk(initial(2), List.of(1L, 2L));

        // then
        assertThat(lastSaved().stopReason()).isEqualTo(BulkStopReason.DRAFT_LIMIT);
    }

    @Test
    void 호출_실패가_3번_연속이면_CALL_FAILED_REPEATED로_멈춘다() {
        // given
        when(draftService.translate(anyLong(), eq(true))).thenThrow(new LlmCallFailedException("실패"));

        // when
        service.runBulk(initial(5), List.of(1L, 2L, 3L, 4L, 5L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.stopReason()).isEqualTo(BulkStopReason.CALL_FAILED_REPEATED);
        assertThat(status.failedCount()).isEqualTo(3);
        verify(draftService, times(3)).translate(anyLong(), eq(true));
    }

    @Test
    void 호출_실패_사이에_성공이_끼면_연속으로_세지_않아_끝까지_처리한다() {
        // given: 실패, 실패, 성공, 실패, 실패
        when(draftService.translate(1L, true)).thenThrow(new LlmCallFailedException("실패"));
        when(draftService.translate(2L, true)).thenThrow(new LlmCallFailedException("실패"));
        when(draftService.translate(3L, true)).thenReturn(draft(true));
        when(draftService.translate(4L, true)).thenThrow(new LlmCallFailedException("실패"));
        when(draftService.translate(5L, true)).thenThrow(new LlmCallFailedException("실패"));

        // when
        service.runBulk(initial(5), List.of(1L, 2L, 3L, 4L, 5L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.COMPLETED);
        assertThat(status.failedCount()).isEqualTo(4);
        assertThat(status.doneCount()).isEqualTo(1);
    }

    @Test
    void 혼잡하면_기다린_뒤_같은_작품을_한_번_다시_불러_성공하면_실패로_세지_않는다() {
        // given: 첫 호출은 혼잡, 재시도는 성공
        when(draftService.translate(1L, true)).thenThrow(new LlmUpstreamBusyException("혼잡"));
        when(draftService.translate(1L, false)).thenReturn(draft(true));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.COMPLETED);
        assertThat(status.doneCount()).isEqualTo(1);
        assertThat(status.failedCount()).isEqualTo(0);
        verify(draftService, times(1)).translate(1L, true);
        verify(draftService, times(1)).translate(1L, false);
    }

    @Test
    void 재시도도_혼잡이면_실패로_세고_연속_3번이면_CALL_FAILED_REPEATED로_멈춘다() {
        // given: 모든 작품이 두 번 다 혼잡
        when(draftService.translate(anyLong(), eq(true))).thenThrow(new LlmUpstreamBusyException("혼잡"));
        when(draftService.translate(anyLong(), eq(false))).thenThrow(new LlmUpstreamBusyException("혼잡"));

        // when
        service.runBulk(initial(5), List.of(1L, 2L, 3L, 4L, 5L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.stopReason()).isEqualTo(BulkStopReason.CALL_FAILED_REPEATED);
        assertThat(status.failedCount()).isEqualTo(3);
        verify(draftService, times(3)).translate(anyLong(), eq(true));
        verify(draftService, times(3)).translate(anyLong(), eq(false));
    }

    @Test
    void 재시도_직전_하루_상한에_걸리면_DAILY_LIMIT으로_멈춘다() {
        // given: 첫 호출은 혼잡, 재시도에서 하루 상한 초과
        when(draftService.translate(1L, true)).thenThrow(new LlmUpstreamBusyException("혼잡"));
        when(draftService.translate(1L, false)).thenThrow(new LlmDraftException(ErrorCode.LLM_DAILY_LIMIT_EXCEEDED));

        // when
        service.runBulk(initial(3), List.of(1L, 2L, 3L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.STOPPED);
        assertThat(status.stopReason()).isEqualTo(BulkStopReason.DAILY_LIMIT);
        verify(draftService, times(1)).translate(anyLong(), eq(true));
    }

    @Test
    void 영어_줄거리가_아닌_작품은_건너뛰고_다음_작품을_계속한다() {
        // given
        when(draftService.translate(1L, true)).thenThrow(new LlmDraftException(ErrorCode.LLM_NOT_TRANSLATABLE));
        when(draftService.translate(2L, true)).thenReturn(draft(true));

        // when
        service.runBulk(initial(2), List.of(1L, 2L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.COMPLETED);
        assertThat(status.failedCount()).isEqualTo(1);
        assertThat(status.doneCount()).isEqualTo(1);
    }

    @Test
    void 실패했다가_재시도에서_성공하면_성공_초안을_시도_2회로_저장한다() {
        // given: 첫 시도는 실패, 첫 재시도에서 성공
        when(draftService.translate(1L, true)).thenReturn(failedDraft("f1"));
        when(draftService.translate(1L, false)).thenReturn(passedDraft("p2"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("p2");
        assertThat(saved.attempts()).isEqualTo(2);
        BulkRunStatus status = lastSaved();
        assertThat(status.doneCount()).isEqualTo(1);
        assertThat(status.guardFailedCount()).isEqualTo(0);
        assertThat(status.warningCount()).isEqualTo(0);
        verify(draftService, times(1)).translate(1L, false);
    }

    @Test
    void 실패가_계속되면_재시도_2번까지_모두_쓰고_실패_초안을_시도_3회로_저장한다() {
        // given
        when(draftService.translate(1L, true)).thenReturn(failedDraft("f1"));
        when(draftService.translate(1L, false)).thenReturn(failedDraft("f2"), failedDraft("f3"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then: 같은 등급이면 마지막 시도를 저장한다
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("f3");
        assertThat(saved.attempts()).isEqualTo(3);
        BulkRunStatus status = lastSaved();
        assertThat(status.doneCount()).isEqualTo(1);
        assertThat(status.guardFailedCount()).isEqualTo(1);
        verify(draftService, times(2)).translate(1L, false);
    }

    @Test
    void 실패가_재시도에서_주의로_바뀌면_거기서_멈추고_주의로_센다() {
        // given: FAILED가 아니게 되면 멈춘다(주의여도 더 시도하지 않는다)
        when(draftService.translate(1L, true)).thenReturn(failedDraft("f1"));
        when(draftService.translate(1L, false)).thenReturn(warningDraft("w2"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("w2");
        assertThat(saved.attempts()).isEqualTo(2);
        BulkRunStatus status = lastSaved();
        assertThat(status.warningCount()).isEqualTo(1);
        assertThat(status.guardFailedCount()).isEqualTo(0);
        verify(draftService, times(1)).translate(1L, false);
    }

    @Test
    void 주의였다가_재시도에서_성공하면_성공_초안을_시도_2회로_저장한다() {
        // given
        when(draftService.translate(1L, true)).thenReturn(warningDraft("w1"));
        when(draftService.translate(1L, false)).thenReturn(passedDraft("p2"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("p2");
        assertThat(saved.attempts()).isEqualTo(2);
        assertThat(lastSaved().warningCount()).isEqualTo(0);
    }

    @Test
    void 주의였다가_재시도에서_실패하면_처음의_주의_초안을_저장한다() {
        // given
        when(draftService.translate(1L, true)).thenReturn(warningDraft("w1"));
        when(draftService.translate(1L, false)).thenReturn(failedDraft("f2"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then: 더 나쁜 재시도 결과를 버리고 최선(처음의 주의)을 저장한다. 시도 횟수는 2다
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("w1");
        assertThat(saved.attempts()).isEqualTo(2);
        BulkRunStatus status = lastSaved();
        assertThat(status.warningCount()).isEqualTo(1);
        assertThat(status.guardFailedCount()).isEqualTo(0);
    }

    @Test
    void 주의가_계속되면_재시도_1번만_하고_마지막_주의_초안을_시도_2회로_저장한다() {
        // given
        when(draftService.translate(1L, true)).thenReturn(warningDraft("w1"));
        when(draftService.translate(1L, false)).thenReturn(warningDraft("w2"), warningDraft("w3"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("w2");
        assertThat(saved.attempts()).isEqualTo(2);
        assertThat(lastSaved().warningCount()).isEqualTo(1);
        verify(draftService, times(1)).translate(1L, false);
    }

    @Test
    void 관리자가_고친_초안이_있는_작품은_번역하지도_덮어쓰지도_않고_건너뛴다() {
        // given: 1번 작품에는 관리자가 고친 초안이 있다
        DraftRecord edited = passedDraft("edited").withEditedTranslation("고친 글", true, List.of(), List.of());
        when(draftRepository.findByTitle(1L)).thenReturn(Optional.of(edited));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then: Gemini 호출도 저장도 없고, 일괄은 정상 완료한다
        verify(draftService, never()).translate(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(draftRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(lastSaved().state()).isEqualTo(BulkRunState.COMPLETED);
    }

    @Test
    void 고치지_않은_기존_초안은_일괄_번역이_새_초안으로_교체한다() {
        // given
        when(draftRepository.findByTitle(1L)).thenReturn(Optional.of(passedDraft("old")));
        when(draftService.translate(1L, true)).thenReturn(passedDraft("new"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        assertThat(savedDraft().draftId()).isEqualTo("new");
    }

    @Test
    void 번역하는_사이_관리자가_초안을_고치면_새_결과를_저장하지_않는다() {
        // given: 번역을 시작할 때는 초안이 없었는데 저장 직전에는 고친 초안이 생겼다
        DraftRecord edited = passedDraft("edited").withEditedTranslation("고친 글", true, List.of(), List.of());
        when(draftRepository.findByTitle(1L)).thenReturn(Optional.empty(), Optional.of(edited));
        when(draftService.translate(1L, true)).thenReturn(passedDraft("new"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        verify(draftRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 처음부터_성공이면_재시도하지_않고_시도_1회로_저장한다() {
        // given
        when(draftService.translate(1L, true)).thenReturn(passedDraft("p1"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        assertThat(savedDraft().attempts()).isEqualTo(1);
        verify(draftService, never()).translate(anyLong(), eq(false));
    }

    @Test
    void 재시도_중_하루_상한에_걸리면_그때까지의_최선_결과를_저장하고_DAILY_LIMIT으로_멈춘다() {
        // given: 첫 시도는 실패, 재시도에서 하루 상한 초과
        when(draftService.translate(1L, true)).thenReturn(failedDraft("f1"));
        when(draftService.translate(1L, false)).thenThrow(new LlmDraftException(ErrorCode.LLM_DAILY_LIMIT_EXCEEDED));

        // when
        service.runBulk(initial(3), List.of(1L, 2L, 3L));

        // then
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("f1");
        assertThat(saved.attempts()).isEqualTo(2);
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.STOPPED);
        assertThat(status.stopReason()).isEqualTo(BulkStopReason.DAILY_LIMIT);
        assertThat(status.doneCount()).isEqualTo(1);
        assertThat(status.guardFailedCount()).isEqualTo(1);
        verify(draftService, never()).translate(eq(2L), eq(true));
    }

    @Test
    void 재시도_중_호출이_실패하면_그_시도만_건너뛰고_최선_결과를_저장하며_연속_실패로_세지_않는다() {
        // given: 첫 시도는 주의, 재시도는 호출 실패
        when(draftService.translate(1L, true)).thenReturn(warningDraft("w1"));
        when(draftService.translate(1L, false)).thenThrow(new LlmCallFailedException("실패"));

        // when
        service.runBulk(initial(1), List.of(1L));

        // then
        DraftRecord saved = savedDraft();
        assertThat(saved.draftId()).isEqualTo("w1");
        assertThat(saved.attempts()).isEqualTo(2);
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.COMPLETED);
        assertThat(status.doneCount()).isEqualTo(1);
        assertThat(status.failedCount()).isEqualTo(0);
    }

    @Test
    void 작품마다_성공_주의_실패를_나눠_세고_진행_상황에_주의_수가_들어_있다() {
        // given: 1 성공, 2 주의(계속), 3 실패(계속)
        when(draftService.translate(1L, true)).thenReturn(passedDraft("p1"));
        when(draftService.translate(2L, true)).thenReturn(warningDraft("w2"));
        when(draftService.translate(2L, false)).thenReturn(warningDraft("w2b"));
        when(draftService.translate(3L, true)).thenReturn(failedDraft("f3"));
        when(draftService.translate(3L, false)).thenReturn(failedDraft("f3b"));

        // when
        service.runBulk(initial(3), List.of(1L, 2L, 3L));

        // then
        BulkRunStatus status = lastSaved();
        assertThat(status.state()).isEqualTo(BulkRunState.COMPLETED);
        assertThat(status.doneCount()).isEqualTo(3);
        assertThat(status.warningCount()).isEqualTo(1);
        assertThat(status.guardFailedCount()).isEqualTo(1);
        assertThat(status.failedCount()).isEqualTo(0);
    }

    @Test
    void 일괄이_실행_중이면_두_번째_시작은_LLM_BULK_RUNNING이다() throws Exception {
        // given: 첫 작품 번역이 풀어 줄 때까지 멈춰 있다
        CountDownLatch release = new CountDownLatch(1);
        when(geminiClient.isEnabled()).thenReturn(true);
        when(draftService.translate(anyLong(), eq(true))).thenAnswer(new Answer<DraftRecord>() {
            @Override
            public DraftRecord answer(InvocationOnMock invocation) throws Throwable {
                release.await(5, TimeUnit.SECONDS);
                return draft(true);
            }
        });
        service.startByTitleIds(List.of(1L));

        // when, then
        assertThat(service.isRunning()).isTrue();
        assertThatThrownBy(() -> service.startByTitleIds(List.of(2L)))
                .isInstanceOf(LlmDraftException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LLM_BULK_RUNNING);

        release.countDown();
        for (int i = 0; i < 50 && service.isRunning(); i++) {
            Thread.sleep(100);
        }
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void 키가_없으면_일괄을_시작하지_않고_LlmDisabledException이다() {
        // given
        when(geminiClient.isEnabled()).thenReturn(false);

        // when, then
        assertThatThrownBy(() -> service.startByTitleIds(List.of(1L))).isInstanceOf(LlmDisabledException.class);
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void 작품_목록이_비었거나_최대치를_넘으면_VALIDATION_FAILED이다() {
        // given
        List<Long> tooMany = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            tooMany.add(i);
        }

        // when, then
        assertThatThrownBy(() -> service.startByTitleIds(List.of())).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.startByTitleIds(tooMany)).isInstanceOf(FieldValidationException.class);
    }

    @Test
    void count로_시작하면_이미_초안이_있는_작품은_건너뛰고_그다음_작품으로_채운다() {
        // given: 후보 정렬 1, 2, 3 중 1에는 이미 초안이 있다
        when(geminiClient.isEnabled()).thenReturn(true);
        when(titleOverviewService.findCandidateTitleIds(0, 200)).thenReturn(List.of(1L, 2L, 3L));
        when(draftRepository.existsByTitle(1L)).thenReturn(true);
        when(draftService.translate(anyLong(), eq(true))).thenReturn(draft(true));

        // when
        BulkRunStatus started = service.startByCount(2);

        // then
        assertThat(started.total()).isEqualTo(2);
        assertThat(started.state()).isEqualTo(BulkRunState.RUNNING);
    }

    @Test
    void 후보가_하나도_없으면_실행하지_않고_바로_완료_상태이다() {
        // given
        when(geminiClient.isEnabled()).thenReturn(true);
        when(titleOverviewService.findCandidateTitleIds(0, 200)).thenReturn(List.of());

        // when
        BulkRunStatus started = service.startByCount(10);

        // then
        assertThat(started.state()).isEqualTo(BulkRunState.COMPLETED);
        assertThat(started.total()).isEqualTo(0);
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void 저장된_상태가_실행_중인데_이_서버에서_돌고_있지_않으면_서버_재시작으로_중단된_것으로_본다() {
        // given
        when(bulkRunStatusRepository.find()).thenReturn(Optional.of(initial(10)));

        // when
        Optional<BulkRunStatus> status = service.findLatestStatus();

        // then
        assertThat(status).isPresent();
        assertThat(status.get().state()).isEqualTo(BulkRunState.STOPPED);
        assertThat(status.get().stopReason()).isEqualTo(BulkStopReason.SERVER_RESTARTED);
    }
}
