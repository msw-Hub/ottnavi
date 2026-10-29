package com.ottnavi.llmdraft.service.model;

/** 초안 반영을 건너뛴 이유 */
public enum ApplySkipReason {
    DRAFT_NOT_FOUND,     // 초안이 없다(보관 기간이 지났거나 이미 반영·삭제됨)
    GUARD_FAILED,        // 자동 검사에서 걸린 초안이라 반영하지 않는다
    ORIGINAL_CHANGED     // 번역한 뒤 작품 줄거리가 바뀌었거나 이미 한국어라 조건이 맞지 않는다
}
