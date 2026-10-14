package com.ottnavi.catalog.service.model;

/** 상세 응답에 붙은 장르 하나. genre 표에 없으면 이 값으로 새로 등록한다 */
public record GenreInfo(
        int id, // TMDB 장르 ID
        String name) { // ko-KR 이름
}
