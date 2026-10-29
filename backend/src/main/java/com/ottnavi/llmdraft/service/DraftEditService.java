package com.ottnavi.llmdraft.service;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.llmdraft.domain.rule.DraftTextGuard;
import com.ottnavi.llmdraft.domain.rule.GuardFailure;
import com.ottnavi.llmdraft.domain.rule.GuardResult;
import com.ottnavi.llmdraft.domain.rule.GuardWarning;
import com.ottnavi.llmdraft.exception.LlmDraftException;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자가 번역 초안의 번역문을 직접 고친다. 초안은 Redis에만 있으므로 DB의 줄거리는 바뀌지 않는다(반영은 apply가 한다).
 * 이 클래스에 트랜잭션을 걸지 않는다(Redis만 쓴다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftEditService {

    private final DraftRepository draftRepository;
    private final DraftTextGuard draftTextGuard;

    /**
     * 초안의 번역문을 새 글로 바꾸고 자동 검사를 다시 돌려 성공·주의·실패를 새로 판정한다. 수정한 초안은 isEdited가 true가 되어
     * 일괄 번역이 같은 작품을 다시 만들어 덮어쓰지 않는다. 보관 기간(TTL)은 저장하는 시각부터 다시 센다.
     * 검사에서 실패가 남아도 저장은 된다(관리자가 이어서 고칠 수 있게). 다만 실패 초안은 반영할 수 없다(GUARD_FAILED).
     *
     * @throws LlmDraftException 없는 초안(LLM_DRAFT_NOT_FOUND)
     */
    public DraftRecord edit(String draftId, String translatedText) {
        // 1. 초안을 찾는다(보관 기간이 지났거나 이미 반영·삭제됐으면 없다)
        Optional<DraftRecord> found = draftRepository.findById(draftId);
        if (found.isEmpty()) {
            throw new LlmDraftException(ErrorCode.LLM_DRAFT_NOT_FOUND);
        }
        DraftRecord draft = found.get();

        // 2. 고친 글을 원문과 비교해 자동 검사를 다시 한다
        GuardResult guard = draftTextGuard.check(draft.original(), translatedText);
        List<String> failures = new ArrayList<>();
        for (GuardFailure failure : guard.failures()) {
            failures.add(failure.name());
        }
        List<String> warnings = new ArrayList<>();
        for (GuardWarning warning : guard.warnings()) {
            warnings.add(warning.name());
        }

        // 3. 수정 표시와 새 판정을 담아 저장한다(같은 draftId라 작품당 1개는 그대로이고 TTL이 새로 시작된다)
        DraftRecord edited = draft.withEditedTranslation(translatedText, guard.passed(), failures, warnings);
        draftRepository.save(edited);
        log.info("번역 초안을 수정했다: draftId={}, titleId={}, grade={}, failures={}, warnings={}", draftId,
                edited.titleId(), edited.grade(), failures, warnings);
        return edited;
    }
}
