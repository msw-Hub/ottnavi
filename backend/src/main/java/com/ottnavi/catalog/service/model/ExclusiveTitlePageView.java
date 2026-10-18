package com.ottnavi.catalog.service.model;

import java.time.LocalDate;
import java.util.List;

/** 독점 목록 한 페이지(캐시된 전체 목록을 잘라 만든다) */
public record ExclusiveTitlePageView(
        long ottServiceId,
        boolean judged,
        LocalDate dataAsOf,
        List<ExclusiveTitleItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
