package com.ottnavi.catalog.domain.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

/**
 * 작품 목록 정렬에서 "작품 자체가 최근 나온 것"을 먼저 보이게 하는 규칙(DB·프레임워크 의존 없음).
 * 화면에 보이는 날짜는 서비스가 판권을 사서 올린 날일 뿐이라, 옛 고전이 인기도만으로 맨 앞에 오는 것을 막으려는 것이다.
 *
 * <p>그룹: 0 = 개봉일이 최근 기간 안(오늘 포함), 1 = 그보다 오래된 과거, 2 = 개봉일이 미래이거나 모름.
 * 각 그룹 안은 인기도 내림차순(인기도 없는 작품은 뒤)이다. 기간은 이 클래스의 상수 한 곳에서만 정한다.
 */
public final class RecentReleaseRules {

    /** 독점 목록과 새로 볼 수 있게 된 작품: 최근 6개월 */
    public static final Period EXCLUSIVE_WINDOW = Period.ofMonths(6);

    /** 작품 검색·탐색(장르·출연자): 최근 2년 */
    public static final Period SEARCH_WINDOW = Period.ofYears(2);

    public static final int GROUP_RECENT = 0;
    public static final int GROUP_PAST = 1;
    public static final int GROUP_FUTURE_OR_UNKNOWN = 2;

    private RecentReleaseRules() {
    }

    /** 개봉일이 속한 그룹을 돌려준다. 예: 오늘 2026-10-27, 기간 6개월이면 2026-04-27 이후 ~ 오늘은 0, 그 전은 1, 내일 이후·null은 2 */
    public static int group(LocalDate releaseDate, LocalDate today, Period window) {
        if (releaseDate == null || releaseDate.isAfter(today)) {
            return GROUP_FUTURE_OR_UNKNOWN;
        }
        if (releaseDate.isBefore(today.minus(window))) {
            return GROUP_PAST;
        }
        return GROUP_RECENT;
    }

    /** 인기도 내림차순 비교. 인기도가 없는(null) 쪽이 뒤로 간다. 같으면 0 */
    public static int comparePopularityDesc(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }
}
