package com.ottnavi.catalog.domain.rule;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/** 작품 상세를 저장하기 전에 적용하는 소규모 규칙 모음(ROADMAP Task 042). 순수 정적 함수 */
public final class TitleDetailRules {

    /** 저장할 출연진 수(ERD title_cast: 상위 10명) */
    public static final int MAX_CAST = 10;

    private TitleDetailRules() {
    }

    /** 수집 대상 시즌 번호만 남긴다. 시즌 0(스페셜)은 제외한다(ERD watch_unit) */
    public static List<Integer> collectableSeasonNumbers(List<Integer> seasonNumbers) {
        if (seasonNumbers == null) {
            return List.of();
        }
        return seasonNumbers.stream().filter(number -> number != null && number >= 1).toList();
    }

    /** 한국어 줄거리가 비어 있으면 영어로 대체하고 언어를 표시한다. 둘 다 없으면 (null, null) */
    public static OverviewPick pickOverview(String koreanOverview, String englishOverview) {
        if (hasText(koreanOverview)) {
            return new OverviewPick(koreanOverview, "ko");
        }
        if (hasText(englishOverview)) {
            return new OverviewPick(englishOverview, "en");
        }
        return new OverviewPick(null, null);
    }

    /**
     * 기존 줄거리와 새로 받은 줄거리 중 저장할 쪽을 고른다. 관리자가 반영한 번역을 수집이 영어로 덮어쓰지 않게 한다.
     * 번역인지는 언어(ko)가 아니라 번역 표시(isCurrentTranslated, overview_translated_at)로 안다. TMDB가 원래 준 한국어는 보호하지 않는다.
     * 새 줄거리가 null → 기존 유지(번역 표시도 그대로)
     * 기존이 번역 + 새 en → 기존 번역 유지(번역 표시 그대로)
     * 기존이 번역 + 새 ko → 새 한국어로 교체하고 번역 표시를 비운다(이제 TMDB 원본)
     * 기존이 TMDB 한국어(번역 아님) + 새 en → 새 영어로 교체(번역 표시는 계속 없음)
     * 그 밖 → 새 값으로 교체하고 번역 표시는 없음
     */
    public static OverviewMerge mergeOverview(String currentOverview, String currentLang, boolean isCurrentTranslated,
                                              String incomingOverview, String incomingLang) {
        // 1. 새 줄거리가 비어 있으면 기존 줄거리·언어·번역 표시를 지우지 않고 유지한다
        if (incomingOverview == null) {
            return new OverviewMerge(currentOverview, currentLang, isCurrentTranslated);
        }
        // 2. 기존이 관리자 번역인데 새 응답이 영어 대체본이면 기존 번역을 유지한다
        if (isCurrentTranslated && "en".equals(incomingLang)) {
            return new OverviewMerge(currentOverview, currentLang, true);
        }
        // 3. 그 외에는 새 값으로 교체하고 번역 표시를 비운다
        return new OverviewMerge(incomingOverview, incomingLang, false);
    }

    /** 출연 순서(order)로 정렬해 상위 10명만 남긴다. order가 없는 사람은 뒤로 보낸다(원래 순서 유지) */
    public static List<CastCandidate> topCast(List<CastCandidate> cast) {
        if (cast == null) {
            return List.of();
        }
        return cast.stream()
                .sorted(Comparator.comparing(CastCandidate::order, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_CAST)
                .toList();
    }

    /** TMDB 날짜 문자열("2023-03-01")을 날짜로 바꾼다. null·빈 값·해석 불가 형식은 null(미정)로 본다 */
    public static LocalDate parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
