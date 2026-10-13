package com.ottnavi.catalog.domain.rule;

/** 출연진 한 명에서 저장에 쓰는 값만 옮긴 것(title_cast 후보) */
public record CastCandidate(
        long personTmdbId,
        String name,
        String character, // 배역. 없으면 null
        String profilePath, // 프로필 이미지 상대 경로. 없으면 null
        Integer order) { // TMDB가 준 출연 순서. 없으면 null
}
