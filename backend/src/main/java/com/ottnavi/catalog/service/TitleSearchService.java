package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.rule.RecentReleaseRules;
import com.ottnavi.catalog.service.model.TitleCards;
import com.ottnavi.catalog.service.model.TitleSearchResult;
import com.ottnavi.global.error.FieldValidationException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작품 검색(Task 050). DB에 저장된 작품만 대상으로 제목·원제 부분 일치로 찾는다.
 * 순서는 개봉일이 최근 2년 이내인 작품 → 그 이전 작품 → 개봉일이 미래이거나 모르는 작품이고, 각 그룹 안은 인기도 순이다.
 * 외부(TMDB) 호출은 하지 않는다. 단건 수집은 후속 Task다.
 * 카드(서비스별 제공 상태) 조립은 TitleCardAssembler가 맡는다(Task 107에서 홈·탐색과 함께 쓰려고 분리).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleSearchService {

    private static final char ESCAPE_CHAR = '\\'; // LIKE 이스케이프 문자

    private final TitleRepository titleRepository;
    private final TitleCardAssembler titleCardAssembler;
    private final Clock clock; // KST 기준 오늘(ClockConfig)

    /**
     * 검색어로 작품을 한 페이지 찾는다. 검색어는 앞뒤 공백을 뺀 뒤 1자 이상이어야 한다.
     * 예: q=" 기생 " → 제목 또는 원제에 "기생"이 들어간 작품, 인기도 높은 순.
     */
    public TitleSearchResult search(String q, int page, int size) {
        // 1. 검색어 검증: 앞뒤 공백을 빼고 비면 오류
        String keyword = "";
        if (q != null) {
            keyword = q.trim();
        }
        if (keyword.isEmpty()) {
            throw new FieldValidationException("q", "검색어를 입력해 주세요.");
        }

        // 2. 제목·원제 부분 일치 조회(최근 2년 개봉작 먼저, 그 안은 인기도 내림차순)
        String pattern = "%" + escapeLike(keyword) + "%";
        LocalDate today = LocalDate.now(clock);
        Page<Title> titles = titleRepository.searchByTitle(pattern, ESCAPE_CHAR,
                today.minus(RecentReleaseRules.SEARCH_WINDOW), today, PageRequest.of(page, size));

        // 3. 이 페이지 작품들을 서비스별 제공 상태가 붙은 카드로 만든다
        TitleCards cards = titleCardAssembler.assemble(titles.getContent());

        return new TitleSearchResult(cards.items(), page, size, titles.getTotalElements(), titles.getTotalPages(),
                cards.dataAsOf());
    }

    /** LIKE 패턴의 특수문자(\, %, _)를 문자 그대로 찾도록 이스케이프한다. 예: "50%" → "50\%" */
    static String escapeLike(String keyword) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
