package com.ottnavi.llmdraft.service.model;

/**
 * 번역 초안의 등급. 자동 검사 결과로 정한다: 실패가 있으면 FAILED, 실패 없이 경고만 있으면 WARNING, 둘 다 없으면 PASSED.
 * 일괄 번역이 여러 시도 중 최선을 고를 때 쓴다(PASSED가 가장 좋다).
 */
public enum DraftGrade {
    PASSED(2),    // 성공: 실패도 경고도 없음
    WARNING(1),   // 주의: 실패는 없지만 의심 신호가 있음
    FAILED(0);    // 실패: 자동 검사에서 걸림

    private final int rank;   // 클수록 좋은 등급

    DraftGrade(int rank) {
        this.rank = rank;
    }

    /** 이 등급이 other보다 엄격하게 좋은지 */
    public boolean isBetterThan(DraftGrade other) {
        return rank > other.rank;
    }
}
