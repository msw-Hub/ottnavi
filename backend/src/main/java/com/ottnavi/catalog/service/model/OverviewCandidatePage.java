package com.ottnavi.catalog.service.model;

import java.util.List;

/** 번역 후보 한 페이지. totalElements는 번역 대기 중인 영어 줄거리 작품의 총수다 */
public record OverviewCandidatePage(List<OverviewCandidateView> content, int page, int size, long totalElements,
                                    int totalPages) {
}
