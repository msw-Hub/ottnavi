package com.ottnavi.llmdraft.domain.rule;

/** 번역 초안이 자동 검사(DraftTextGuard)에서 걸린 이유. 이름이 그대로 API 응답의 이유 코드가 된다 */
public enum GuardFailure {
    EMPTY,                // 번역문이 비어 있다
    TOO_LONG,             // 번역문이 최대 글자 수를 넘는다
    LENGTH_RATIO_LOW,     // 원문에 비해 너무 짧다(내용이 빠졌을 가능성)
    LENGTH_RATIO_HIGH,    // 원문에 비해 너무 길다(설명이 덧붙었을 가능성)
    HANGUL_RATIO_LOW,     // 한글 비율이 낮다(번역이 안 됐거나 영어가 그대로 남았을 가능성)
    CONTAINS_URL,         // 주소(URL)가 들어 있다
    CONTAINS_MARKDOWN,    // 마크다운 기호가 들어 있다
    CONTAINS_PREAMBLE,    // "번역:", "Here is" 같은 머리말·설명 문구가 들어 있다
    NUMBER_MISSING        // 원문의 숫자(연도·회차 등)가 번역문에 없다
}
