package com.ottnavi.llmdraft.service;

import com.ottnavi.catalog.service.TitleOverviewService;
import com.ottnavi.catalog.service.model.OverviewCandidatePage;
import com.ottnavi.catalog.service.model.OverviewCandidateView;
import com.ottnavi.catalog.service.model.OverviewTranslationStats;
import com.ottnavi.infra.llm.GeminiClient;
import com.ottnavi.infra.llm.LlmProperties;
import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.repository.LlmQuotaStore;
import com.ottnavi.llmdraft.service.model.CandidateItemView;
import com.ottnavi.llmdraft.service.model.CandidatePageView;
import com.ottnavi.llmdraft.service.model.QuotaUsage;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 번역 후보 목록과 화면 상단 요약(대기 총수, 오늘 사용량, 모델, 키 유무)을 만든다.
 * 키가 없어도 조회는 동작한다(isEnabled=false로 알려 준다). 트랜잭션은 catalog 서비스가 짧게 연다.
 */
@Service
@RequiredArgsConstructor
public class OverviewCandidateService {

    private final TitleOverviewService titleOverviewService;
    private final DraftRepository draftRepository;
    private final LlmQuotaStore quotaStore;
    private final GeminiClient geminiClient;
    private final LlmProperties llmProperties;
    private final LlmDraftProperties draftProperties;

    /** 번역 후보를 찜 많은 순 → 인기순으로 한 페이지 돌려준다. 각 후보에 초안이 있는지 함께 알려 준다 */
    public CandidatePageView list(int page, int size) {
        // 1. 후보 한 페이지
        OverviewCandidatePage found = titleOverviewService.findCandidates(page, size);

        // 2. 후보마다 초안 보유 여부를 붙인다
        List<CandidateItemView> content = new ArrayList<>();
        for (OverviewCandidateView candidate : found.content()) {
            content.add(new CandidateItemView(candidate, draftRepository.existsByTitle(candidate.titleId())));
        }

        // 3. 요약: 대기 총수, 상한과 오늘 사용량, 모델, 키 유무, 반영된 번역 통계
        QuotaUsage usage = new QuotaUsage(quotaStore.usedToday(), draftProperties.limits().requestsPerDay(),
                draftProperties.limits().requestsPerMinute());
        OverviewTranslationStats translation = titleOverviewService.findTranslationStats();
        return new CandidatePageView(content, found.page(), found.size(), found.totalElements(), found.totalPages(),
                found.totalElements(), usage, llmProperties.model(), geminiClient.isEnabled(), translation);
    }
}
