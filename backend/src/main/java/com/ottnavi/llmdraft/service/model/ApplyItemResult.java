package com.ottnavi.llmdraft.service.model;

/**
 * 초안 한 건의 반영 결과.
 *
 * @param draftId 초안 ID
 * @param titleId 작품 ID(초안이 없으면 null)
 * @param outcome 반영 여부
 * @param reason  건너뛴 이유(반영했으면 null)
 */
public record ApplyItemResult(String draftId, Long titleId, ApplyOutcome outcome, ApplySkipReason reason) {
}
