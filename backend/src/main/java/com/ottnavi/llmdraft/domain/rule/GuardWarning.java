package com.ottnavi.llmdraft.domain.rule;

/**
 * 번역 초안이 자동 검사(DraftTextGuard)에서 의심받은 이유. 실패(GuardFailure)와 달리 반영을 막지 않고,
 * 실패 없이 경고만 있는 초안은 "주의"로 분류돼 사람이 승인해야 반영된다. 이름이 그대로 API 응답의 경고 코드가 된다
 */
public enum GuardWarning {
    SENTENCE_COUNT_MISMATCH,   // 원문과 번역문의 문장 수 차이가 허용치를 넘는다(문장이 빠졌거나 합쳐졌을 가능성)
    STYLE_NOT_PLAIN,           // "~다." 꼴이 아닌 문장이 많다(문체가 섞였을 가능성)
    SUSPICIOUS_SPACING,        // 한 글자 낱말 뒤 어미처럼 보이는 낱말이 따로 떨어져 있다("데 이고" 같은 깨진 띄어쓰기)
    LENGTH_RATIO_NEAR_LIMIT    // 길이 비율이 실패 한계에 가깝다
}
