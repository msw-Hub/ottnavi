package com.ottnavi.llmdraft.service;

import com.ottnavi.catalog.service.TitleOverviewService;
import com.ottnavi.catalog.service.model.OverviewSourceView;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.catalog.service.model.TranslatedRevertBatch;
import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.service.model.ApplyItemResult;
import com.ottnavi.llmdraft.service.model.ApplyOutcome;
import com.ottnavi.llmdraft.service.model.ApplySkipReason;
import com.ottnavi.llmdraft.service.model.DraftGrade;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 초안 반영·삭제와 영어로 되돌리기. 줄거리를 바꾸는 반영·되돌리기는 수집·재계산·정리·인기 수집이 실행 중이면 거절한다
 * (수집이 같은 줄거리를 쓰고 있을 때 덮어쓰기가 엇갈리지 않게). 이 클래스에 트랜잭션을 걸지 않는다:
 * DB 갱신은 catalog 서비스가 작품 한 건씩 짧은 트랜잭션으로 하고, 캐시 삭제는 그 커밋 뒤에 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftApplyService {

    /** 대량 되돌리기가 한 번에 읽고 갱신하는 작품 수(한 트랜잭션의 크기) */
    private static final int REVERT_BATCH_SIZE = 500;

    private final DraftRepository draftRepository;
    private final TitleOverviewService titleOverviewService;
    private final ConflictingJobChecker conflictingJobChecker;

    /**
     * 초안을 작품 줄거리에 반영한다. draftIds를 주면 그 초안만(주의 초안도 반영하는 승인 경로), allPassed면 성공(PASSED) 초안 전부를
     * 대상으로 한다(주의 초안은 대상에서 빠져 결과에도 나오지 않는다). 실패 초안은 draftIds로 지정해도 GUARD_FAILED로 건너뛴다.
     * 건별 결과를 돌려주며 한 건이 건너뛰어져도 나머지는 계속한다.
     * 반영은 줄거리가 아직 영어이고 번역한 원문과 같을 때만 되고, 반영한 초안은 Redis에서 지운다.
     */
    public List<ApplyItemResult> apply(List<String> draftIds, boolean allPassed) {
        // 1. 수집 등이 실행 중이면 시작하지 않는다
        ensureNoRunningJob();

        // 2. 대상 초안 ID를 정한다
        List<String> targetIds = new ArrayList<>();
        if (allPassed) {
            for (DraftRecord draft : draftRepository.findAll()) {
                if (draft.grade() == DraftGrade.PASSED) {
                    targetIds.add(draft.draftId());
                }
            }
        } else {
            Set<String> unique = new LinkedHashSet<>(draftIds);
            targetIds.addAll(unique);
        }

        // 3. 초안마다 반영한다
        List<ApplyItemResult> results = new ArrayList<>();
        for (String draftId : targetIds) {
            results.add(applyOne(draftId));
        }
        log.info("번역 초안을 반영했다: 대상={}, 반영={}", results.size(), countApplied(results));
        return results;
    }

    /** 초안 한 건을 삭제한다. 없으면 LLM_DRAFT_NOT_FOUND(404)다 */
    public void deleteDraft(String draftId) {
        if (!draftRepository.delete(draftId)) {
            throw new LlmDraftException(ErrorCode.LLM_DRAFT_NOT_FOUND);
        }
    }

    /**
     * 관리자가 반영한 번역을 비워 영어로 되돌린다(overview, overview_lang, overview_translated_at → null).
     * 다음 수집(매일 DAILY·매주 WEEKLY)이 TMDB 영어 줄거리를 다시 채우므로, 그때까지 최대 일주일 줄거리가 비어 있을 수 있다.
     * 없는 작품은 404, 번역이 아니면(TMDB가 준 한국어 포함) LLM_NOT_REVERTIBLE(409), 수집 등이 실행 중이면 LLM_APPLY_CONFLICT(409)다.
     */
    public void revertToEnglish(Long titleId) {
        // 1. 수집 등이 실행 중이면 거절한다
        ensureNoRunningJob();

        // 2. 작품과 줄거리가 관리자 번역인지 확인한다
        Optional<OverviewSourceView> found = titleOverviewService.findSource(titleId);
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("작품을 찾을 수 없습니다: titleId=" + titleId);
        }
        if (!found.get().isTranslated()) {
            throw new LlmDraftException(ErrorCode.LLM_NOT_REVERTIBLE);
        }

        // 3. 줄거리를 비우고(조건부 갱신) 커밋 뒤에 상세 캐시를 지운다
        Optional<TitleKey> cleared = titleOverviewService.clearKoreanOverview(titleId);
        if (cleared.isEmpty()) {
            // 확인한 뒤 그 사이 수집이 줄거리를 바꾼 경우
            throw new LlmDraftException(ErrorCode.LLM_NOT_REVERTIBLE);
        }
        titleOverviewService.evictDetailCache(cleared.get());
        log.info("줄거리를 영어로 되돌렸다(다음 수집이 다시 채운다): titleId={}", titleId);
    }

    /**
     * 관리자가 반영한 번역을 한꺼번에 영어로 되돌린다(overview, overview_lang, overview_translated_at → null). 되돌린 작품 수를 돌려준다.
     * translatedBefore가 있으면 그 시각보다 먼저 반영한 번역만, null이면 번역 전부가 대상이다. TMDB가 준 한국어는 건드리지 않는다.
     * 다음 수집이 TMDB 영어 줄거리를 다시 채운다. 수집 등이 실행 중이면 LLM_APPLY_CONFLICT(409)다.
     * 작품을 REVERT_BATCH_SIZE건씩 짧은 트랜잭션으로 되돌리고, 묶음마다 커밋 뒤에 상세 캐시를 지운다(캐시 실패는 경고만).
     */
    public int revertTranslatedOverviews(Instant translatedBefore) {
        // 1. 수집 등이 실행 중이면 거절한다
        ensureNoRunningJob();

        // 2. 대상이 바닥날 때까지 한 묶음씩 되돌린다(되돌린 행은 조건에서 빠져 다음 읽기에 나오지 않는다)
        int revertedCount = 0;
        while (true) {
            TranslatedRevertBatch batch = titleOverviewService.revertTranslatedBatch(translatedBefore, REVERT_BATCH_SIZE);
            if (batch.keys().isEmpty()) {
                break;
            }
            // 3. 커밋 뒤에 묶음의 상세 캐시를 하나씩 지운다
            for (TitleKey key : batch.keys()) {
                titleOverviewService.evictDetailCache(key);
            }
            revertedCount += batch.revertedCount();
            // 4. 읽은 작품이 하나도 바뀌지 않았다면(동시 변경) 같은 묶음을 계속 읽지 않도록 멈춘다
            if (batch.revertedCount() == 0) {
                break;
            }
        }
        log.info("관리자 번역 줄거리를 영어로 되돌렸다(다음 수집이 다시 채운다): translatedBefore={}, 되돌린 작품 수={}",
                translatedBefore, revertedCount);
        return revertedCount;
    }

    /**
     * 대량 되돌리기를 실제로 하지 않고 되돌려질 작품 수만 센다(미리보기, DB를 바꾸지 않는다). 실제 되돌리기와 같은 대상 조건을 쓴다.
     * 실제 실행처럼 수집 등이 실행 중이면 LLM_APPLY_CONFLICT(409)다(지금 실행하면 막힐 것을 미리 알려 준다).
     */
    public long countTranslatedOverviewsToRevert(Instant translatedBefore) {
        // 1. 수집 등이 실행 중이면 거절한다
        ensureNoRunningJob();

        // 2. 대상 수만 센다
        long count = titleOverviewService.countTranslatedForRevert(translatedBefore);
        log.info("번역 줄거리 대량 되돌리기를 미리 셌다(바꾸지 않음): translatedBefore={}, 대상 작품 수={}", translatedBefore, count);
        return count;
    }

    private ApplyItemResult applyOne(String draftId) {
        // 1. 초안이 없으면(보관 기간이 지났거나 이미 반영·삭제됨) 건너뛴다
        Optional<DraftRecord> found = draftRepository.findById(draftId);
        if (found.isEmpty()) {
            return new ApplyItemResult(draftId, null, ApplyOutcome.SKIPPED, ApplySkipReason.DRAFT_NOT_FOUND);
        }
        DraftRecord draft = found.get();

        // 2. 자동 검사에서 실패한 초안은 반영하지 않는다(주의 초안은 지정했을 때 반영한다)
        if (!draft.isGuardPassed()) {
            return new ApplyItemResult(draftId, draft.titleId(), ApplyOutcome.SKIPPED, ApplySkipReason.GUARD_FAILED);
        }

        // 3. 조건부 갱신: 아직 영어이고 번역한 원문과 같을 때만 바뀐다.
        //    초안은 최대 7일 보관되는 사이 수집이 줄거리를 바꿀 수 있으므로, 바뀌었으면(빈 값) 덮어쓰지 않고 건너뛴다.
        //    건너뛴 초안은 지우지 않고 남겨 둔다(옛 원문의 번역이라 반영할 수 없으니 필요하면 새 원문으로 다시 번역한다)
        Optional<TitleKey> applied = titleOverviewService.applyKoreanOverview(draft.titleId(), draft.original(),
                draft.translated());
        if (applied.isEmpty()) {
            return new ApplyItemResult(draftId, draft.titleId(), ApplyOutcome.SKIPPED, ApplySkipReason.ORIGINAL_CHANGED);
        }

        // 4. 커밋 뒤에 상세 캐시 1개를 지우고, 반영한 초안을 Redis에서 지운다
        titleOverviewService.evictDetailCache(applied.get());
        draftRepository.delete(draftId);
        return new ApplyItemResult(draftId, draft.titleId(), ApplyOutcome.APPLIED, null);
    }

    private void ensureNoRunningJob() {
        if (conflictingJobChecker.isAnyRunning()) {
            throw new LlmDraftException(ErrorCode.LLM_APPLY_CONFLICT);
        }
    }

    private int countApplied(List<ApplyItemResult> results) {
        int count = 0;
        for (ApplyItemResult result : results) {
            if (result.outcome() == ApplyOutcome.APPLIED) {
                count++;
            }
        }
        return count;
    }
}
