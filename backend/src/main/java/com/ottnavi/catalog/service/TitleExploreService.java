package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Genre;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.repository.GenreRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.service.model.GenreInfo;
import com.ottnavi.catalog.service.model.TitleCards;
import com.ottnavi.catalog.service.model.TitleSearchResult;
import com.ottnavi.global.error.FieldValidationException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탐색(Task 107, FR-23): 장르·출연자 이름으로 우리 DB에 저장된 작품만 찾는다. TMDB를 호출하지 않는다.
 * 결과는 "7개 서비스에서 확인된 범위"이며 인기도 높은 순이다. 출연진은 작품당 상위 10명만 저장돼 있다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleExploreService {

    private final GenreRepository genreRepository;
    private final TitleRepository titleRepository;
    private final TitleCardAssembler titleCardAssembler;

    /** 작품이 하나 이상 연결된 장르를 이름 가나다순으로 돌려준다 */
    public List<GenreInfo> listGenres() {
        List<GenreInfo> result = new ArrayList<>();
        for (Genre genre : genreRepository.findAllWithTitles()) {
            result.add(new GenreInfo(genre.getId(), genre.getName()));
        }
        return result;
    }

    /** 장르에 속한 작품을 인기도 순으로 한 페이지 돌려준다. 없는 장르는 오류가 아니라 빈 결과다 */
    public TitleSearchResult listTitlesByGenre(int genreId, int page, int size) {
        Page<Title> titles = titleRepository.findPageByGenreId(genreId, PageRequest.of(page, size));
        return toResult(titles, page, size);
    }

    /**
     * 이름이 같은(대소문자 무시) 출연자가 나온 작품을 인기도 순으로 한 페이지 돌려준다.
     * 이름은 앞뒤 공백을 뺀 뒤 1자 이상이어야 한다. 예: name=" 송강호 " → "송강호"가 출연 상위 10명에 든 작품.
     */
    public TitleSearchResult listTitlesByCastName(String name, int page, int size) {
        // 1. 이름 검증: 앞뒤 공백을 빼고 비면 오류
        String trimmed = "";
        if (name != null) {
            trimmed = name.trim();
        }
        if (trimmed.isEmpty()) {
            throw new FieldValidationException("name", "출연자 이름을 입력해 주세요.");
        }

        // 2. 이름이 같은 출연자의 작품 조회
        Page<Title> titles = titleRepository.findPageByCastName(trimmed, PageRequest.of(page, size));
        return toResult(titles, page, size);
    }

    private TitleSearchResult toResult(Page<Title> titles, int page, int size) {
        TitleCards cards = titleCardAssembler.assemble(titles.getContent());
        return new TitleSearchResult(cards.items(), page, size, titles.getTotalElements(), titles.getTotalPages(),
                cards.dataAsOf());
    }
}
