package com.ottnavi.catalog.domain.repository;

/** TitleRepository.findOverviewCandidates의 결과 한 행(네이티브 쿼리 투영). 이름은 쿼리의 AS 별칭과 같다 */
public interface OverviewCandidateRow {

    Long getId();

    String getMediaType();

    String getTitle();

    String getOverview();

    Long getWishlistCount();
}
