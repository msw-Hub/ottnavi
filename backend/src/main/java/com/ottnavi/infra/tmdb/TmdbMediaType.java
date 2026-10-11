package com.ottnavi.infra.tmdb;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 제공처 전체 목록(/watch/providers/{mediaType}) 호출에서 쓰는 매체 종류 */
@Getter
@RequiredArgsConstructor
public enum TmdbMediaType {

    MOVIE("movie"),
    TV("tv");

    private final String path;   // TMDB 경로에 들어가는 값
}
