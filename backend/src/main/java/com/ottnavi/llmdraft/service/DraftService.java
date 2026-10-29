package com.ottnavi.llmdraft.service;

import com.ottnavi.catalog.service.TitleOverviewService;
import com.ottnavi.catalog.service.model.OverviewSourceView;
import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.infra.llm.GeminiClient;
import com.ottnavi.infra.llm.LlmProperties;
import com.ottnavi.infra.llm.exception.LlmDisabledException;
import com.ottnavi.infra.llm.exception.LlmUpstreamBusyException;
import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.llmdraft.domain.rule.DraftTextGuard;
import com.ottnavi.llmdraft.domain.rule.GuardFailure;
import com.ottnavi.llmdraft.domain.rule.GuardResult;
import com.ottnavi.llmdraft.domain.rule.GuardWarning;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.repository.LlmQuotaStore;
import com.ottnavi.llmdraft.repository.QuotaDecision;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 영어 줄거리 한 건의 한국어 번역 초안을 만든다. 클래스에 트랜잭션을 걸지 않는다:
 * 작품 읽기는 catalog 서비스가 짧게 열고 닫으며, Gemini 호출(외부 호출)은 트랜잭션 밖에서 한다(backend.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftService {

    private final TitleOverviewService titleOverviewService;
    private final GeminiClient geminiClient;
    private final DraftTextGuard draftTextGuard;
    private final DraftRepository draftRepository;
    private final LlmQuotaStore quotaStore;
    private final LlmDraftProperties draftProperties;
    private final LlmProperties llmProperties;
    private final Clock clock;

    /**
     * 작품의 영어 줄거리를 번역해 초안으로 저장한다(단건: 한 번만 시도하고 재시도하지 않는다. 다시 번역은 관리자가 누른다).
     * 같은 작품에 이미 초안이 있으면 새 초안으로 교체한다.
     * 번역문이 자동 검사에서 걸려도 초안은 저장한다(걸린 이유와 함께 FAILED로, 경고만 있으면 WARNING으로 보인다). 호출 실패는 예외다.
     *
     * @throws ResourceNotFoundException 없는 작품
     * @throws LlmDraftException         영어 줄거리가 아님(LLM_NOT_TRANSLATABLE), 쿨다운(LLM_TITLE_COOLDOWN),
     *                                   대기 초안 가득 참(LLM_DRAFT_LIMIT_EXCEEDED), 분당·하루 상한(LLM_RATE_LIMITED, LLM_DAILY_LIMIT_EXCEEDED)
     * @throws LlmDisabledException      Gemini 키가 없음
     * @throws LlmUpstreamBusyException  구글 서버가 혼잡함(502 LLM_UPSTREAM_BUSY). 요청 안에서 재시도하지 않는다
     */
    public DraftRecord createDraft(Long titleId) {
        DraftRecord draft = translate(titleId, true);
        draftRepository.save(draft);
        log.info("번역 초안을 저장했다: titleId={}, grade={}, failures={}, warnings={}", titleId, draft.grade(),
                draft.guardFailures(), draft.warnings());
        return draft;
    }

    /**
     * 작품의 영어 줄거리를 번역하고 자동 검사한 결과를 초안 기록으로 돌려준다. 저장하지는 않는다(attempts는 1).
     * 일괄 번역이 시도 여러 번 중 최선을 골라 한 번만 저장하려고 단건 생성에서 떼어 낸 단계다.
     * checkCooldown이 false면 같은 작품 쿨다운 검사만 건너뛴다: 방금 호출이 쿨다운을 시작했으므로 일괄이 같은 작품을
     * 곧바로 다시 부를 때(구글 혼잡 재시도, 검사 재시도)는 쿨다운에 항상 막히기 때문이다.
     * 키·초안 가득 참·분당·하루 상한 검사와 카운터 증가는 시도마다 그대로 한다(호출이 실제로 일어나므로).
     */
    public DraftRecord translate(Long titleId, boolean checkCooldown) {
        // 1. 작품을 읽는다. 없는 작품이거나 영어 줄거리가 아니면 번역하지 않는다
        Optional<OverviewSourceView> found = titleOverviewService.findSource(titleId);
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("작품을 찾을 수 없습니다: titleId=" + titleId);
        }
        OverviewSourceView source = found.get();
        if (!"en".equals(source.overviewLang()) || source.overview() == null || source.overview().isBlank()) {
            throw new LlmDraftException(ErrorCode.LLM_NOT_TRANSLATABLE);
        }

        // 2. 키가 없으면 상한 카운터를 올리지 않고 거절한다
        if (!geminiClient.isEnabled()) {
            throw new LlmDisabledException("Gemini 키(GEMINI_API_KEY)가 설정되지 않아 번역을 호출하지 않았다");
        }

        // 3. 같은 작품을 막 번역했으면 기다리게 한다
        if (checkCooldown && quotaStore.isCoolingDown(titleId)) {
            throw new LlmDraftException(ErrorCode.LLM_TITLE_COOLDOWN);
        }

        // 4. 대기 초안이 가득 찼는지 확인한다(같은 작품의 초안을 교체하는 것은 개수가 늘지 않으므로 통과)
        ensureDraftCapacity(titleId);

        // 5. 분당·하루 상한을 확인하고 카운터를 올린다. 이 시점부터 호출이 실패해도 카운터는 되돌리지 않는다
        QuotaDecision decision = quotaStore.tryAcquire();
        if (decision == QuotaDecision.DAY_EXCEEDED) {
            throw new LlmDraftException(ErrorCode.LLM_DAILY_LIMIT_EXCEEDED);
        }
        if (decision == QuotaDecision.MINUTE_EXCEEDED) {
            throw new LlmDraftException(ErrorCode.LLM_RATE_LIMITED);
        }
        quotaStore.startCooldown(titleId);

        // 6. Gemini로 번역한다(트랜잭션 밖). 실패는 LlmException(LLM_RATE_LIMITED, LLM_UPSTREAM_BUSY, LLM_CALL_FAILED)으로 올라간다
        String translated = geminiClient.translateToKorean(source.overview());

        // 7. 자동 검사하고 초안 기록을 만든다(저장은 부르는 쪽이 한다)
        GuardResult guard = draftTextGuard.check(source.overview(), translated);
        List<String> failures = new ArrayList<>();
        for (GuardFailure failure : guard.failures()) {
            failures.add(failure.name());
        }
        List<String> warnings = new ArrayList<>();
        for (GuardWarning warning : guard.warnings()) {
            warnings.add(warning.name());
        }
        return new DraftRecord(UUID.randomUUID().toString(), source.titleId(), source.mediaType().toApiValue(),
                source.title(), source.overview(), translated, guard.passed(), failures, warnings,
                llmProperties.model(), clock.instant(), 1);
    }

    private void ensureDraftCapacity(Long titleId) {
        int maxPending = draftProperties.draft().maxPending();
        // 색인 크기(근사값)가 상한 미만이면 확실히 여유가 있다. 상한 이상일 때만 만료된 항목을 걷어낸 정확한 수로 다시 센다
        if (draftRepository.countIndexed() < maxPending) {
            return;
        }
        if (draftRepository.existsByTitle(titleId)) {
            return;
        }
        if (draftRepository.findAll().size() >= maxPending) {
            throw new LlmDraftException(ErrorCode.LLM_DRAFT_LIMIT_EXCEEDED);
        }
    }
}
