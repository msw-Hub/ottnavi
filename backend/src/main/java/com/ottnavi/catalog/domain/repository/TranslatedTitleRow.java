package com.ottnavi.catalog.domain.repository;

/** TitleRepository.findTranslatedTitles의 결과 한 행(네이티브 쿼리 투영). 이름은 쿼리의 AS 별칭과 같다 */
public interface TranslatedTitleRow {

    Long getId();

    String getMediaType();

    Integer getTmdbId();
}
