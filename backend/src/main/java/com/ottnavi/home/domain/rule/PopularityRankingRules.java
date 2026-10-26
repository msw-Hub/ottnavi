package com.ottnavi.home.domain.rule;

import com.ottnavi.catalog.domain.enums.MediaType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 일별 인기 스냅샷으로 순위를 만든다(DB·프레임워크 의존 없음). 순위 계산 규칙은 이 클래스 한 곳에 모아 두었다.
 *
 * <p>규칙(PRD 11절 42번, 사용자 결정 2026-10-18):
 * 1. 평균 = 작품의 날별 인기도 합 ÷ 스냅샷이 있는 날 수(daysCounted). 그날 스냅샷에 없던 작품은 그날을 0으로 센다.
 *    예: 7일 중 하루만 상위권에 오른 작품(인기도 70)은 70 ÷ 7 = 10이라 꾸준한 작품에 밀린다. TODAY는 daysCounted=1이라 그날 인기도 그대로다.
 * 2. 같은 날 같은 작품이 여러 줄이면(전체 순위에서 여러 서비스에 걸쳐 나옴) 가장 큰 인기도 하나만 센다(TMDB 인기도는 작품마다 하나다).
 * 3. 정렬: 평균 내림차순 → 가장 최근 날의 인기도 내림차순(없으면 0) → TMDB ID 오름차순 → 영화가 드라마보다 앞.
 */
public final class PopularityRankingRules {

    private static final int AVERAGE_SCALE = 6; // 평균 계산 소수 자릿수(동률이 반올림으로 생기지 않게 넉넉히)

    private PopularityRankingRules() {
    }

    /**
     * 순위를 만든다.
     *
     * @param rows       순위 기간에 속한 스냅샷 줄들
     * @param daysCounted 평균에 쓰는 스냅샷 날 수(스냅샷이 있는 날만 센다). 1 미만이면 빈 목록이다
     * @param limit      돌려줄 최대 개수
     */
    public static List<RankedPopularity> rank(List<DailyPopularity> rows, int daysCounted, int limit) {
        if (daysCounted < 1 || rows.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 가장 최근 날짜(정렬 보조 기준)를 찾는다
        LocalDate latestDate = rows.get(0).date();
        for (DailyPopularity row : rows) {
            if (row.date().isAfter(latestDate)) {
                latestDate = row.date();
            }
        }

        // 2. 작품별로 날마다 가장 큰 인기도 하나만 모은다
        Map<String, Aggregate> byKey = new HashMap<>();
        for (DailyPopularity row : rows) {
            String key = row.mediaType() + ":" + row.tmdbId();
            Aggregate aggregate = byKey.get(key);
            if (aggregate == null) {
                aggregate = new Aggregate();
                byKey.put(key, aggregate);
            }
            aggregate.add(row, latestDate);
        }

        // 3. 평균을 계산한다(스냅샷이 없던 날은 합에 더하지 않으므로 0으로 센 것과 같다)
        List<Ranked> ranked = new ArrayList<>();
        BigDecimal days = BigDecimal.valueOf(daysCounted);
        for (Aggregate aggregate : byKey.values()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal value : aggregate.maxByDate.values()) {
                sum = sum.add(value);
            }
            BigDecimal average = sum.divide(days, AVERAGE_SCALE, RoundingMode.HALF_UP);
            ranked.add(new Ranked(aggregate, average));
        }

        // 4. 정렬해 상위 limit개만 남긴다
        ranked.sort(Comparator.comparing((Ranked r) -> r.average).reversed()
                .thenComparing((Ranked r) -> r.aggregate.latestDayPopularity, Comparator.reverseOrder())
                .thenComparingInt(r -> r.aggregate.latestRow.tmdbId())
                .thenComparingInt(r -> r.aggregate.latestRow.mediaType().ordinal()));
        List<RankedPopularity> result = new ArrayList<>();
        for (Ranked item : ranked) {
            if (result.size() >= limit) {
                break;
            }
            DailyPopularity meta = item.aggregate.latestRow;
            result.add(new RankedPopularity(meta.mediaType(), meta.tmdbId(), meta.title(), meta.posterPath(),
                    meta.releaseDate(), item.average));
        }
        return result;
    }

    /** 작품 하나의 날짜별 최대 인기도와 표시값(가장 최근 날의 줄) */
    private static final class Aggregate {
        private final Map<LocalDate, BigDecimal> maxByDate = new HashMap<>();
        private DailyPopularity latestRow;
        private BigDecimal latestDayPopularity = BigDecimal.ZERO; // 가장 최근 날(window의 마지막 날)의 인기도

        void add(DailyPopularity row, LocalDate windowLatestDate) {
            BigDecimal current = maxByDate.get(row.date());
            if (current == null || row.popularity().compareTo(current) > 0) {
                maxByDate.put(row.date(), row.popularity());
            }
            if (latestRow == null || row.date().isAfter(latestRow.date())) {
                latestRow = row;
            }
            if (row.date().equals(windowLatestDate)) {
                BigDecimal value = maxByDate.get(row.date());
                if (value.compareTo(latestDayPopularity) > 0) {
                    latestDayPopularity = value;
                }
            }
        }
    }

    private static final class Ranked {
        private final Aggregate aggregate;
        private final BigDecimal average;

        Ranked(Aggregate aggregate, BigDecimal average) {
            this.aggregate = aggregate;
            this.average = average;
        }
    }
}
