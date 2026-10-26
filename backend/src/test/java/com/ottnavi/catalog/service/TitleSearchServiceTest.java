package com.ottnavi.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 검색어 이스케이프 단위 테스트(Task 050). LIKE 특수문자가 문자 그대로 검색되는지 확인한다 */
class TitleSearchServiceTest {

    @Test
    void 퍼센트와_언더스코어와_역슬래시를_이스케이프한다() {
        // given
        String keyword = "100%_a\\b";

        // when
        String escaped = TitleSearchService.escapeLike(keyword);

        // then
        assertThat(escaped).isEqualTo("100\\%\\_a\\\\b");
    }

    @Test
    void 특수문자가_없으면_그대로다() {
        // when
        String escaped = TitleSearchService.escapeLike("기생충 Parasite");

        // then
        assertThat(escaped).isEqualTo("기생충 Parasite");
    }
}
