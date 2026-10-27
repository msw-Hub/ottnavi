package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.ArrivalType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "새로 볼 수 있게 된 작품" 후보에서 같은 작품·서비스의 대표 한 건만 남기고 최신순으로 자른다(DB·프레임워크 의존 없음).
 *
 * <p>예: 같은 작품·서비스에 시즌 1(10월 3일 입고)과 시즌 2(10월 9일 입고) 후보가 있으면 10월 9일 한 건만 남는다.
 * 날짜가 같으면 입고(ARRIVAL)를 공개(RELEASE)보다 앞에 둔다(실제 상태 변화를 확인한 쪽이 더 확실하다).
 * 정렬: 작품 개봉일 그룹(최근 6개월 → 그 이전 → 미래·모름) → 날짜 최신순 → 작품 ID 순 → 서비스 ID 순(항상 같은 순서로 나오게 한다).
 * 옛 고전이 입고 날짜만 최근이라고 맨 앞에 오는 것을 막으려고 작품 자체의 개봉일을 먼저 본다.
 */
public final class NewArrivalPicker {

    private NewArrivalPicker() {
    }

    /** 같은 작품·서비스는 대표 한 건만 남기고, 작품 개봉일 그룹과 날짜 최신순으로 정렬해 최대 limit건을 돌려준다. today는 KST 오늘이다 */
    public static List<NewArrivalCandidate> pick(List<NewArrivalCandidate> candidates, int limit, LocalDate today) {
        // 1. 작품·서비스마다 대표 한 건(더 최근 날짜, 같으면 ARRIVAL)을 고른다
        Map<String, NewArrivalCandidate> best = new LinkedHashMap<>();
        for (NewArrivalCandidate candidate : candidates) {
            String key = candidate.titleId() + ":" + candidate.ottServiceId();
            NewArrivalCandidate current = best.get(key);
            if (current == null || isBetter(candidate, current)) {
                best.put(key, candidate);
            }
        }

        // 2. 작품 개봉일이 최근 6개월 안인 후보 → 오래된 작품 → 개봉일이 미래·모름 순으로, 각 그룹 안은 날짜 최신순으로 정렬한다
        List<NewArrivalCandidate> sorted = new ArrayList<>(best.values());
        sorted.sort(new Comparator<NewArrivalCandidate>() {
            @Override
            public int compare(NewArrivalCandidate a, NewArrivalCandidate b) {
                int groupA = RecentReleaseRules.group(a.titleReleaseDate(), today, RecentReleaseRules.EXCLUSIVE_WINDOW);
                int groupB = RecentReleaseRules.group(b.titleReleaseDate(), today, RecentReleaseRules.EXCLUSIVE_WINDOW);
                if (groupA != groupB) {
                    return Integer.compare(groupA, groupB);
                }
                int byDate = b.date().compareTo(a.date());
                if (byDate != 0) {
                    return byDate;
                }
                int byTitle = Long.compare(a.titleId(), b.titleId());
                if (byTitle != 0) {
                    return byTitle;
                }
                return Long.compare(a.ottServiceId(), b.ottServiceId());
            }
        });

        // 3. 상위 limit건만 돌려준다
        if (sorted.size() > limit) {
            return new ArrayList<>(sorted.subList(0, limit));
        }
        return sorted;
    }

    private static boolean isBetter(NewArrivalCandidate candidate, NewArrivalCandidate current) {
        int byDate = candidate.date().compareTo(current.date());
        if (byDate != 0) {
            return byDate > 0;
        }
        return candidate.type() == ArrivalType.ARRIVAL && current.type() == ArrivalType.RELEASE;
    }
}
