package com.ottnavi.catalog.domain;

import java.util.Locale;

/** 작품 종류. DB에는 대문자(MOVIE/TV)로 저장하고 API는 소문자(movie/tv)를 쓴다 */
public enum MediaType {
    MOVIE,
    TV;

    /** API 경로·응답에 쓰는 소문자 표기. 서버 로케일(예: 터키어)에 따라 결과가 달라지지 않도록 Locale.ROOT를 쓴다 */
    public String toApiValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** API의 소문자 표기(movie/tv)를 enum으로 바꾼다. 모르는 값이면 IllegalArgumentException */
    public static MediaType fromApiValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
