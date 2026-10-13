package com.ottnavi.catalog.application;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집 Job이 쓰는 작품 저장 서비스 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleCollectService {

    private final TitleRepository titleRepository;

    /**
     * 후보를 저장하고 새로 저장한 개수를 돌려준다.
     * 없는 작품은 요약 값으로 새로 만들고, 있는 작품은 포스터·배경·인기도·평점만 갱신한다(나머지는 Task 044가 상세로 채운다).
     */
    @Transactional
    public int saveCandidates(List<CandidateTitle> candidates, Instant fetchedAt) {
        int saved = 0;
        for (CandidateTitle c : candidates) {
            // 같은 작품이 여러 서비스 목록에 나와도 UNIQUE(media_type, tmdb_id)를 건드리지 않게 먼저 확인한다
            Optional<Title> existing = titleRepository.findByMediaTypeAndTmdbId(c.mediaType(), c.tmdbId());
            if (existing.isPresent()) {
                // 조회한 엔티티는 영속 상태라 값이 바뀐 경우에만 커밋 때 UPDATE가 나간다(더티 체킹)
                existing.get().refreshSummary(c.posterPath(), c.backdropPath(), c.popularity(), c.voteAverage());
                continue;
            }
            titleRepository.save(Title.createFromSummary(c.mediaType(), c.tmdbId(), c.title(), c.originalTitle(),
                    c.releaseDate(), c.posterPath(), c.backdropPath(), c.originalLanguage(), c.popularity(),
                    c.voteAverage(), fetchedAt));
            saved++;
        }
        return saved;
    }
}
