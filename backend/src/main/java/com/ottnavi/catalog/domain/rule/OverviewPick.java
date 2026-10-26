package com.ottnavi.catalog.domain.rule;

/** 고른 줄거리와 언어 표시(title.overview, title.overview_lang) */
public record OverviewPick(
        String overview, // 둘 다 없으면 null
        String overviewLang) { // "ko" / "en" / 둘 다 없으면 null
}
