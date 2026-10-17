package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.service.model.StaleTitle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TMDB 약관의 6개월 보관 기한을 지키려고 "마지막 수집 시각이 오래된 작품"을 점검하는 조회 서비스(Task 083) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StaleTitleQueryService {

    private final TitleRepository titleRepository;

    /** 마지막 수집 시각이 cutoff보다 이른 작품 수 */
    public long countStale(Instant cutoff) {
        return titleRepository.countByFetchedAtBefore(cutoff);
    }

    /** 마지막 수집 시각이 cutoff보다 이른 작품을 가장 오래된 순으로 최대 limit건 돌려준다 */
    public List<StaleTitle> findStale(Instant cutoff, int limit) {
        List<StaleTitle> result = new ArrayList<>();
        for (Title title : titleRepository.findByFetchedAtBeforeOrderByFetchedAtAscIdAsc(cutoff, PageRequest.of(0, limit))) {
            result.add(new StaleTitle(title.getId(), title.getMediaType(), title.getTmdbId()));
        }
        return result;
    }
}
