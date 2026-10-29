package com.ottnavi.catalog.domain.rule;

/** 수집 때 저장할 줄거리 판정 결과(title.overview, overview_lang, 번역 표시 유지 여부) */
public record OverviewMerge(
        String overview, // 저장할 줄거리, 둘 다 없으면 null
        String overviewLang, // "ko" / "en" / 둘 다 없으면 null
        boolean isTranslated) { // true면 관리자 번역을 그대로 둔다(overview_translated_at 유지), false면 번역 표시를 비운다
}
