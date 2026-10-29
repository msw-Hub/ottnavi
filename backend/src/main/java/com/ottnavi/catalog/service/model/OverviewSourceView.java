package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;

/**
 * 줄거리 번역·되돌리기가 읽는 작품의 현재 줄거리 상태.
 *
 * @param titleId      작품 ID
 * @param mediaType    작품 구분
 * @param tmdbId       TMDB ID(상세 캐시 키를 만드는 데 쓴다)
 * @param title        작품 제목
 * @param overview     현재 줄거리(없으면 null)
 * @param overviewLang 현재 줄거리 언어(ko/en, 줄거리가 없으면 null)
 * @param isTranslated 관리자가 반영한 번역인지(false면 TMDB가 준 줄거리이거나 줄거리가 없음). 영어로 되돌릴 수 있는 것은 번역뿐이다
 */
public record OverviewSourceView(Long titleId, MediaType mediaType, int tmdbId, String title, String overview,
                                 String overviewLang, boolean isTranslated) {
}
