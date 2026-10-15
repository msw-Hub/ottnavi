package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.List;

/** 검색 결과 한 페이지 */
public record TitleSearchResult(
        List<TitleSearchItemView> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LocalDate dataAsOf) { // 이 페이지 판정의 가장 오래된 확인일(KST)
}
