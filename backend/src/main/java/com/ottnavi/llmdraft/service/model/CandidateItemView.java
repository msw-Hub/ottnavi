package com.ottnavi.llmdraft.service.model;

import com.ottnavi.catalog.service.model.OverviewCandidateView;

/** 번역 후보 한 건과 초안 보유 여부 */
public record CandidateItemView(OverviewCandidateView candidate, boolean hasDraft) {
}
