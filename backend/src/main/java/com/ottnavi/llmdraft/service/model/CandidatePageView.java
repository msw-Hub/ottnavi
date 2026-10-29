package com.ottnavi.llmdraft.service.model;

import com.ottnavi.catalog.service.model.OverviewTranslationStats;
import java.util.List;

/**
 * 번역 후보 한 페이지와 화면 상단 요약.
 *
 * @param pendingTotal 번역 대기 중인 영어 줄거리 작품의 총수(페이지 총수와 같다)
 * @param usage        호출 상한과 오늘 사용량
 * @param model        사용 중인 모델 이름
 * @param isEnabled    LLM을 쓸 수 있는지(키가 설정됐는지)
 * @param translation  관리자가 반영한 번역의 수와 가장 오래된 반영 시각
 */
public record CandidatePageView(List<CandidateItemView> content, int page, int size, long totalElements,
                                int totalPages, long pendingTotal, QuotaUsage usage, String model, boolean isEnabled,
                                OverviewTranslationStats translation) {
}
