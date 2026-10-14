package com.ottnavi.catalog.domain.rule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 시청 단위의 필요 시간(분) 계산(PRD 5.4 "필요 시간"). 순수 Java */
public final class RuntimeCalculator {

    /** TMDB 장르 id 16 = Animation (영화·TV 공통) */
    public static final int ANIMATION_GENRE_ID = 16;

    private RuntimeCalculator() {
    }

    /** 영화 한 편: runtime이 있으면 그 값, 비었으면(null·0 이하) 영화 기본값으로 추정한다. 애니메이션 영화도 영화 기본값을 쓴다 */
    public static RuntimeResult forMovie(Integer runtime, RuntimeDefaults defaults) {
        if (isPresent(runtime)) {
            return new RuntimeResult(1, 1, runtime, 0, false);
        }
        return new RuntimeResult(1, 1, defaults.movieMinutes(), 1, true);
    }

    /**
     * 시즌 하나: 방영된 회차(방영일이 오늘 이전이거나 오늘)의 runtime을 더하고, 빈 회차는 기본값으로 채운다.
     * 미방영 회차는 합계와 missing 수에서 모두 뺀다.
     *
     * @param today 방영 여부 기준일(수집 기준일, KST 날짜)
     * @param animation 애니메이션 작품이면 회차 기본값이 24분이 된다
     */
    public static RuntimeResult forSeason(List<EpisodeRuntime> episodes, LocalDate today, boolean animation,
                                          RuntimeDefaults defaults) {
        // 1. 회차 runtime이 비었을 때 쓸 기본값을 정한다(애니메이션은 24분)
        int fallback = animation ? defaults.animationEpisodeMinutes() : defaults.episodeMinutes();
        int aired = 0;
        int total = 0;
        int missing = 0;
        // 2. 회차를 하나씩 본다
        for (EpisodeRuntime episode : episodes) {
            // 3. 방영일 미정·미방영 회차는 합계와 missing 수에서 뺀다
            if (episode.airDate() == null || episode.airDate().isAfter(today)) {
                continue;
            }
            // 4. 방영된 회차는 runtime을 더하고, 비었으면 기본값으로 채워 추정으로 센다
            aired++;
            if (isPresent(episode.runtime())) {
                total += episode.runtime();
            } else {
                total += fallback;
                missing++;
            }
        }
        // 5. 전체·방영 회차 수와 합계로 결과를 만든다
        return new RuntimeResult(episodes.size(), aired, total, missing, missing > 0);
    }

    /** 장르 id 목록에 애니메이션(16)이 있는지. 목록이 null이면 아니다 */
    public static boolean isAnimation(Collection<Integer> genreIds) {
        return genreIds != null && genreIds.contains(ANIMATION_GENRE_ID);
    }

    private static boolean isPresent(Integer runtime) {
        return runtime != null && runtime > 0;
    }
}
