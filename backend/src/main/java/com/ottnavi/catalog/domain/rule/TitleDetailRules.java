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
