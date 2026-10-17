package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;

/** 작품을 가리키는 외부 기준 키(작품 구분 + TMDB ID). 영화와 드라마의 TMDB ID가 겹칠 수 있어 둘이 함께 작품을 정한다 */
public record TitleKey(MediaType mediaType, int tmdbId) {
}
