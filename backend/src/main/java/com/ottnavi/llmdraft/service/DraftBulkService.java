package com.ottnavi.llmdraft.service;

import com.ottnavi.catalog.service.TitleOverviewService;
import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.infra.llm.GeminiClient;
import com.ottnavi.infra.llm.exception.LlmDisabledException;
import com.ottnavi.infra.llm.exception.LlmUpstreamBusyException;
import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.BulkRunStatusRepository;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.service.model.BulkRunState;
import com.ottnavi.llmdraft.service.model.BulkRunStatus;
import com.ottnavi.llmdraft.service.model.BulkStopReason;
import com.ottnavi.llmdraft.service.model.DraftGrade;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 일괄 번역: 여러 작품의 초안을 서버 안에서 한 줄로 차례로 만든다(관리자 전용, 자동 실행 없음).
 * 전용 스레드 하나가 호출 사이에 app.llm.bulk.call-interval만큼 쉬며 처리하고, 진행 상황은 Redis에 남긴다.
 * 한 번에 실행 중인 일괄은 1개다(메모리 플래그로 판정. 서버는 한 대 전제이고, 서버가 재시작되면 플래그와 함께 사라진다).
 * 한도 초과·일일 상한·키 없음·초안 가득 참이면 즉시 멈추고, 호출 실패가 연속으로 이어져도 멈춘다.
 * 자동 검사 결과가 실패·주의인 작품은 설정 횟수만큼 자동으로 다시 번역한다(단건 번역은 재시도하지 않는다).
 * 이 클래스에 트랜잭션을 걸지 않는다(Gemini 호출이 들어 있다).
 */
@Slf4j
@Service
public class DraftBulkService {

    private static final int CANDIDATE_SCAN_PAGE_SIZE = 200;   // 후보를 훑을 때 한 번에 읽는 작품 수
    private static final int CANDIDATE_SCAN_MAX_PAGES = 10;    // 이미 초안이 있는 작품을 건너뛰며 훑는 최대 쪽 수(무한 루프 방지)

    private final DraftService draftService;
    private final DraftRepository draftRepository;
    private final BulkRunStatusRepository bulkRunStatusRepository;
    private final TitleOverviewService titleOverviewService;
    private final GeminiClient geminiClient;
    private final LlmDraftProperties properties;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    // 일괄 전용 스레드 하나. Executor 빈으로 두면 Boot의 기본 작업 실행기(@Async용)가 물러나므로 이 서비스 안에 둔다
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new BulkThreadFactory());

    public DraftBulkService(DraftService draftService, DraftRepository draftRepository,
                             BulkRunStatusRepository bulkRunStatusRepository, TitleOverviewService titleOverviewService,
                             GeminiClient geminiClient, LlmDraftProperties properties, Clock clock) {
        this.draftService = draftService;
        this.draftRepository = draftRepository;
        this.bulkRunStatusRepository = bulkRunStatusRepository;
        this.titleOverviewService = titleOverviewService;
        this.geminiClient = geminiClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 지정한 작품들의 일괄 번역을 시작한다(중복 제거, 순서 유지). 이미 초안이 있는 작품도 다시 번역해 교체한다.
     * 작품 수가 0이거나 최대치를 넘으면 VALIDATION_FAILED, 이미 실행 중이면 LLM_BULK_RUNNING, 키가 없으면 LLM_DISABLED다.
     */
    public BulkRunStatus startByTitleIds(List<Long> titleIds) {
        // 1. 중복을 없애고 개수를 확인한다
        Set<Long> unique = new LinkedHashSet<>(titleIds);
        if (unique.isEmpty()) {
            throw new FieldValidationException("titleIds", "작품을 1개 이상 지정해야 합니다.");
        }
        if (unique.size() > properties.bulk().maxSize()) {
            throw new FieldValidationException("titleIds", properties.bulk().maxSize() + "개 이하여야 합니다.");
        }
        return start(new ArrayList<>(unique));
    }

    /**
     * 번역 후보 정렬(찜 많은 순 → 인기순) 상위 count건의 일괄 번역을 시작한다. 이미 초안이 있는 작품은 건너뛰고 그다음 작품을 채운다.
     * 후보가 count보다 적으면 있는 만큼만 처리하고, 하나도 없으면 바로 완료 상태가 된다.
     */
    public BulkRunStatus startByCount(int count) {
        if (count < 1 || count > properties.bulk().maxSize()) {
            throw new FieldValidationException("count", "1 이상 " + properties.bulk().maxSize() + " 이하여야 합니다.");
        }
        // 1. 이미 실행 중이면 후보를 고르기 전에 거절한다
        if (running.get()) {
            throw new LlmDraftException(ErrorCode.LLM_BULK_RUNNING);
        }
        // 2. 후보를 정렬 순서대로 훑으며 초안이 없는 작품을 count개까지 모은다
        List<Long> picked = new ArrayList<>();
        for (int page = 0; page < CANDIDATE_SCAN_MAX_PAGES && picked.size() < count; page++) {
            List<Long> pageIds = titleOverviewService.findCandidateTitleIds(page, CANDIDATE_SCAN_PAGE_SIZE);
            if (pageIds.isEmpty()) {
                break;
            }
            for (Long titleId : pageIds) {
                if (picked.size() < count && !draftRepository.existsByTitle(titleId)) {
                    picked.add(titleId);
                }
            }
        }
        return start(picked);
    }

    /** 최근 일괄의 진행 상황. 실행 중이라고 기록돼 있는데 이 서버에서 돌고 있지 않으면 서버가 재시작된 것으로 보고 중단으로 돌려준다 */
    public Optional<BulkRunStatus> findLatestStatus() {
        Optional<BulkRunStatus> stored = bulkRunStatusRepository.find();
        if (stored.isPresent() && stored.get().state() == BulkRunState.RUNNING && !running.get()) {
            return Optional.of(stored.get().finished(BulkRunState.STOPPED, BulkStopReason.SERVER_RESTARTED,
                    stored.get().startedAt()));
        }
        return stored;
    }

    /** 일괄이 지금 이 서버에서 실행 중인지 */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 작품 목록을 차례로 번역한다. 전용 스레드가 부르며, 시험에서는 직접 불러 끝날 때까지 기다린다.
     * 작품마다 자동 검사 결과가 실패·주의면 설정 횟수만큼 다시 번역해 최선 결과 하나만 저장한다(translateWithRetries).
     * 끝나면(완료·중단·예외 모두) 실행 중 표시를 내린다.
     */
    public void runBulk(BulkRunStatus initial, List<Long> titleIds) {
        BulkRunStatus status = initial;
        int done = 0;
        int failed = 0;
        int guardFailed = 0;
        int warning = 0;
        int consecutiveFailures = 0;   // 호출 실패가 연속된 횟수(성공하면 0으로 되돌린다)
        try {
            for (int index = 0; index < titleIds.size(); index++) {
                // 1. 작품 한 건을 번역한다(필요하면 자동으로 다시 번역하고, 최선 결과를 저장한다)
                try {
                    TitleOutcome outcome = translateWithRetries(titleIds.get(index));
                    done++;
                    if (outcome.draft().grade() == DraftGrade.FAILED) {
                        guardFailed++;
                    }
                    if (outcome.draft().grade() == DraftGrade.WARNING) {
                        warning++;
                    }
                    consecutiveFailures = 0;
                    // 2. 재시도 중에 멈춰야 하는 오류가 났다면 최선 결과는 저장했으니 이제 중단한다
                    if (outcome.stopReason() != null) {
                        log.warn("일괄 번역을 중단한다(재시도 중): reason={}", outcome.stopReason());
                        status = status.withProgress(done, failed, guardFailed, warning)
                                .finished(BulkRunState.STOPPED, outcome.stopReason(), clock.instant());
                        bulkRunStatusRepository.save(status);
                        return;
                    }
                } catch (BusinessException e) {
                    // 3. 첫 시도에서 멈춰야 하는 오류(한도·키·가득 참)면 즉시 중단한다
                    BulkStopReason stopReason = stopReasonOf(e.getErrorCode());
                    if (stopReason != null) {
                        log.warn("일괄 번역을 중단한다: reason={}, errorCode={}", stopReason, e.getErrorCode());
                        status = status.withProgress(done, failed, guardFailed, warning)
                                .finished(BulkRunState.STOPPED, stopReason, clock.instant());
                        bulkRunStatusRepository.save(status);
                        return;
                    }
                    // 4. 한 건의 실패는 세고 다음 작품으로 간다. 호출 실패만 연속 실패로 센다
                    failed++;
                    if (e.getErrorCode() == ErrorCode.LLM_CALL_FAILED || e.getErrorCode() == ErrorCode.LLM_UPSTREAM_BUSY) {
                        consecutiveFailures++;
                    }
                    log.warn("일괄 번역 중 작품 하나를 건너뛴다: titleId={}, errorCode={}", titleIds.get(index), e.getErrorCode());
                } catch (RuntimeException e) {
                    failed++;
                    consecutiveFailures++;
                    log.error("일괄 번역 중 예상하지 못한 오류로 작품 하나를 건너뛴다: titleId={}", titleIds.get(index), e);
                }

                // 5. 호출 실패가 연속되면 외부 장애로 보고 멈춘다
                status = status.withProgress(done, failed, guardFailed, warning);
                if (consecutiveFailures >= properties.bulk().maxConsecutiveFailures()) {
                    log.warn("일괄 번역 호출 실패가 연속 {}회라 중단한다", consecutiveFailures);
                    status = status.finished(BulkRunState.STOPPED, BulkStopReason.CALL_FAILED_REPEATED, clock.instant());
                    bulkRunStatusRepository.save(status);
                    return;
                }
                bulkRunStatusRepository.save(status);

                // 6. 다음 호출 전에 쉰다(구글 분당 한도 보호). 마지막 작품 뒤에는 쉬지 않는다
                if (index < titleIds.size() - 1 && !pause()) {
                    status = status.finished(BulkRunState.STOPPED, BulkStopReason.SERVER_RESTARTED, clock.instant());
                    bulkRunStatusRepository.save(status);
                    return;
                }
            }
            // 7. 끝까지 처리했다
            status = status.finished(BulkRunState.COMPLETED, null, clock.instant());
            bulkRunStatusRepository.save(status);
            log.info("일괄 번역을 마쳤다: total={}, done={}, failed={}, guardFailed={}, warning={}", titleIds.size(), done,
                    failed, guardFailed, warning);
        } finally {
            running.set(false);
        }
    }

    /**
     * 작품 한 건을 번역하고, 결과가 실패(FAILED)나 주의(WARNING)면 자동으로 다시 번역해 최선 결과 하나를 저장한다.
     * 첫 시도 결과가 FAILED면 최대 app.llm.bulk.fail-retry-max번 더 시도하고 FAILED가 아니게 되면 멈춘다.
     * WARNING이면 최대 warning-retry-max번 더 시도하고 PASSED가 되면 멈춘다. 재시도는 같은 작품 쿨다운 검사를 건너뛴다.
     * 최선은 PASSED > WARNING > FAILED 순이고 같은 등급이면 나중 시도를 쓴다. 저장은 한 번만 하고 총 시도 횟수를 attempts에 남긴다.
     * 예: WARNING → (재시도) FAILED 이면 처음의 WARNING 초안을 저장한다.
     * 첫 시도의 실패는 예외로 올린다(저장할 결과가 없다). 재시도 중 멈춰야 하는 오류(키·분당·하루 상한·초안 가득 참)는
     * 예외로 올리지 않고 결과의 stopReason에 담아, 그때까지의 최선 결과를 저장한 뒤 호출하는 쪽이 중단하게 한다.
     * 재시도 중 호출 실패는 그 시도만 건너뛴다.
     */
    private TitleOutcome translateWithRetries(Long titleId) {
        // 0. 관리자가 직접 고친 초안이 있으면 호출 없이 그대로 두고 건너뛴다(덮어쓰지 않는다)
        Optional<DraftRecord> editedBefore = findEditedDraft(titleId);
        if (editedBefore.isPresent()) {
            log.info("관리자가 고친 초안이라 일괄 번역을 건너뛴다: titleId={}", titleId);
            return new TitleOutcome(editedBefore.get(), null);
        }

        // 1. 첫 시도(구글 혼잡이면 한 번 더 부른다). 여기서 나는 예외는 그대로 올린다
        DraftRecord best = translateWithBusyRetry(titleId);
        DraftGrade firstGrade = best.grade();
        int attempts = 1;
        BulkStopReason stopReason = null;

        // 2. 첫 결과의 등급에 따라 재시도 횟수를 정한다
        int retriesLeft = 0;
        if (firstGrade == DraftGrade.FAILED) {
            retriesLeft = properties.bulk().failRetryMax();
        }
        if (firstGrade == DraftGrade.WARNING) {
            retriesLeft = properties.bulk().warningRetryMax();
        }

        // 3. 목표(FAILED면 FAILED 탈출, WARNING이면 PASSED)에 닿거나 횟수를 다 쓸 때까지 다시 번역한다
        DraftRecord latest = best;
        while (retriesLeft > 0 && !isRetryGoalReached(firstGrade, latest.grade())) {
            retriesLeft--;
            // 재시도도 호출 간격을 둔다. 쉬는 중 스레드가 중단되면(서버 종료) 더 시도하지 않는다
            if (!pause()) {
                break;
            }
            attempts++;
            try {
                latest = draftService.translate(titleId, false);
            } catch (BusinessException e) {
                // 4. 멈춰야 하는 오류면 최선 결과를 저장하고 중단하게 알린다. 호출 실패 등은 그 시도만 건너뛴다
                stopReason = stopReasonOf(e.getErrorCode());
                if (stopReason != null) {
                    break;
                }
                log.warn("재시도 호출이 실패해 그 시도는 건너뛴다: titleId={}, errorCode={}", titleId, e.getErrorCode());
                continue;
            } catch (RuntimeException e) {
                log.error("재시도 중 예상하지 못한 오류가 나 더 시도하지 않는다: titleId={}", titleId, e);
                break;
            }
            // 5. 같은 등급이면 나중 시도를 쓰고, 더 나쁜 결과는 버린다
            if (!best.grade().isBetterThan(latest.grade())) {
                best = latest;
            }
        }

        // 6. 번역하는 사이 관리자가 초안을 고쳤다면 새 결과를 버리고 고친 초안을 지킨다
        Optional<DraftRecord> editedDuring = findEditedDraft(titleId);
        if (editedDuring.isPresent()) {
            log.info("번역하는 사이 관리자가 초안을 고쳐 새 결과를 저장하지 않는다: titleId={}", titleId);
            return new TitleOutcome(editedDuring.get(), stopReason);
        }

        // 7. 최선 결과를 총 시도 횟수와 함께 한 번만 저장한다
        DraftRecord saved = best.withAttempts(attempts);
        draftRepository.save(saved);
        log.info("일괄 번역 초안을 저장했다: titleId={}, grade={}, attempts={}, failures={}, warnings={}", titleId,
                saved.grade(), attempts, saved.guardFailures(), saved.warnings());
        return new TitleOutcome(saved, stopReason);
    }

    /** 작품의 초안이 있고 관리자가 고친 것이면 돌려준다(일괄 번역이 덮어쓰면 안 되는 초안) */
    private Optional<DraftRecord> findEditedDraft(Long titleId) {
        Optional<DraftRecord> found = draftRepository.findByTitle(titleId);
        if (found.isPresent() && found.get().isEdited()) {
            return found;
        }
        return Optional.empty();
    }

    /** 재시도를 멈춰도 되는 등급에 닿았는지: 처음이 FAILED면 FAILED가 아니게 됐을 때, WARNING이면 PASSED가 됐을 때 */
    private boolean isRetryGoalReached(DraftGrade firstGrade, DraftGrade latestGrade) {
        if (firstGrade == DraftGrade.FAILED) {
            return latestGrade != DraftGrade.FAILED;
        }
        return latestGrade == DraftGrade.PASSED;
    }

    /**
     * 첫 시도를 한다. 구글 서버 혼잡(LlmUpstreamBusyException)이면 app.llm.bulk.busy-retry-wait만큼 쉰 뒤
     * 같은 작품을 딱 한 번 다시 부른다. 재시도에서도 혼잡이면 그 예외를 그대로 올려 호출하는 쪽이 실패로 센다.
     * 재시도는 키·초안 가득 참·분당·하루 상한을 다시 확인하고 카운터도 올린다(DraftService.translate).
     * 쉬는 중 스레드가 중단되면(서버 종료) 재시도하지 않고 혼잡 예외를 올리며, 중단 표시가 남아 다음 대기에서 일괄이 멈춘다.
     */
    private DraftRecord translateWithBusyRetry(Long titleId) {
        try {
            return draftService.translate(titleId, true);
        } catch (LlmUpstreamBusyException e) {
            log.warn("구글 서버 혼잡으로 {}초 뒤 한 번 다시 시도한다: titleId={}",
                    properties.bulk().busyRetryWait().toSeconds(), titleId);
            if (!sleep(properties.bulk().busyRetryWait().toMillis())) {
                throw e;
            }
            return draftService.translate(titleId, false);
        }
    }

    /**
     * 작품 한 건 처리 결과.
     *
     * @param draft      저장한 최선 초안(attempts 반영)
     * @param stopReason 재시도 중 멈춰야 하는 오류가 나서 일괄을 중단해야 하면 그 사유, 아니면 null
     */
    private record TitleOutcome(DraftRecord draft, BulkStopReason stopReason) {
    }

    /** 일괄을 만들고 전용 스레드에 맡긴다. 작품 목록이 비어 있으면 실행하지 않고 완료 상태만 남긴다 */
    private BulkRunStatus start(List<Long> titleIds) {
        // 1. 키가 없으면 시작하지 않는다
        if (!geminiClient.isEnabled()) {
            throw new LlmDisabledException("Gemini 키(GEMINI_API_KEY)가 설정되지 않아 일괄 번역을 시작하지 않았다");
        }
        // 2. 한 번에 하나만: 이미 실행 중이면 거절한다
        if (!running.compareAndSet(false, true)) {
            throw new LlmDraftException(ErrorCode.LLM_BULK_RUNNING);
        }
        // 3. 진행 상황을 만들고 저장한다
        BulkRunStatus initial = new BulkRunStatus(UUID.randomUUID().toString(), BulkRunState.RUNNING, titleIds.size(), 0, 0, 0,
                null, clock.instant(), null, 0);
        if (titleIds.isEmpty()) {
            BulkRunStatus completed = initial.finished(BulkRunState.COMPLETED, null, clock.instant());
            bulkRunStatusRepository.save(completed);
            running.set(false);
            return completed;
        }
        try {
            bulkRunStatusRepository.save(initial);
            // 4. 전용 스레드에서 처리한다
            executor.execute(new BulkTask(this, initial, titleIds));
        } catch (RuntimeException e) {
            // 저장이나 실행 접수에 실패하면 실행 중 표시를 내려 다음 요청을 막지 않는다
            running.set(false);
            throw e;
        }
        log.info("일괄 번역을 시작했다: bulkRunId={}, total={}", initial.bulkRunId(), titleIds.size());
        return initial;
    }

    /** 오류 코드가 일괄을 멈춰야 하는 종류면 중단 사유를, 아니면 null을 돌려준다 */
    private BulkStopReason stopReasonOf(ErrorCode errorCode) {
        if (errorCode == ErrorCode.LLM_DISABLED) {
            return BulkStopReason.DISABLED;
        }
        if (errorCode == ErrorCode.LLM_RATE_LIMITED) {
            return BulkStopReason.RATE_LIMITED;
        }
        if (errorCode == ErrorCode.LLM_DAILY_LIMIT_EXCEEDED) {
            return BulkStopReason.DAILY_LIMIT;
        }
        if (errorCode == ErrorCode.LLM_DRAFT_LIMIT_EXCEEDED) {
            return BulkStopReason.DRAFT_LIMIT;
        }
        return null;
    }

    /** 호출 간격만큼 쉰다. 서버 종료로 스레드가 중단되면 false다 */
    private boolean pause() {
        return sleep(properties.bulk().callInterval().toMillis());
    }

    /** 지정한 시간만큼 쉰다. 0 이하면 쉬지 않는다. 스레드가 중단되면 중단 표시를 되살리고 false다 */
    private boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** 전용 스레드에서 일괄 하나를 실행하는 작업 */
    private static class BulkTask implements Runnable {

        private final DraftBulkService service;
        private final BulkRunStatus initial;
        private final List<Long> titleIds;

        BulkTask(DraftBulkService service, BulkRunStatus initial, List<Long> titleIds) {
            this.service = service;
            this.initial = initial;
            this.titleIds = titleIds;
        }

        @Override
        public void run() {
            service.runBulk(initial, titleIds);
        }
    }

    private static class BulkThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "llm-bulk");
            thread.setDaemon(true);
            return thread;
        }
    }
}
