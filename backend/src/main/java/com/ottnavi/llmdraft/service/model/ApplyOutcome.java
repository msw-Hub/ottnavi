package com.ottnavi.llmdraft.service.model;

/** 초안 반영 결과의 종류 */
public enum ApplyOutcome {
    APPLIED,   // 작품 줄거리를 번역문으로 바꿨다
    SKIPPED    // 건너뛰었다(이유는 ApplySkipReason)
}
