package com.ottnavi.catalog.domain.rule;

/** runtime이 비었을 때 쓰는 추정 기본값(분). 설정 app.collect.runtime-defaults.*에서 받아 넣는다(PRD 11절 25번) */
public record RuntimeDefaults(
        int movieMinutes, // 영화 120
        int episodeMinutes, // 회차 45
        int animationEpisodeMinutes) { // 애니메이션 회차 24

    public RuntimeDefaults {
        if (movieMinutes <= 0 || episodeMinutes <= 0 || animationEpisodeMinutes <= 0) {
            throw new IllegalArgumentException("러닝타임 기본값은 1분 이상이어야 한다");
        }
    }
}
