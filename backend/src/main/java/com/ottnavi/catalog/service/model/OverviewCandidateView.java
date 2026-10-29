package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;

/**
 * 번역 후보 작품 한 건(영어 줄거리가 남아 있는 작품).
 *
 * @param titleId       작품 ID
 * @param mediaType     작품 구분
 * @param title         작품 제목
 * @param overview      영어 줄거리 전문
 * @param wishlistCount 이 작품을 찜한 사용자 수(시즌별로 찜해도 한 사람은 1명으로 센다)
 */
public record OverviewCandidateView(Long titleId, MediaType mediaType, String title, String overview, long wishlistCount) {
}
