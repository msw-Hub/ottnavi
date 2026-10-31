package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.model.CandidateTitle;
import com.ottnavi.catalog.service.model.TitleKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     * 주어진 작품 키 중 이미 title 표에 있는 키만 돌려준다. 작품 구분(영화·드라마)별로 tmdb_id IN 조회를 한 번씩만 한다.
     * 인기 순위 상위 작품 중 채울 작품(표에 없는 작품)을 가릴 때 쓴다.
     */
    public Set<TitleKey> findExistingKeys(Collection<TitleKey> keys) {
        // 1. 작품 구분별로 TMDB ID를 모은다
        Map<MediaType, List<Integer>> idsByType = new EnumMap<>(MediaType.class);
        for (TitleKey key : keys) {
            List<Integer> ids = idsByType.get(key.mediaType());
            if (ids == null) {
                ids = new ArrayList<>();
                idsByType.put(key.mediaType(), ids);
            }
            ids.add(key.tmdbId());
        }

        // 2. 구분마다 한 번씩 조회해 있는 키를 모은다
        Set<TitleKey> existing = new HashSet<>();
        for (Map.Entry<MediaType, List<Integer>> entry : idsByType.entrySet()) {
            for (Integer tmdbId : titleRepository.findTmdbIdsByMediaTypeAndTmdbIdIn(entry.getKey(), entry.getValue())) {
                existing.add(new TitleKey(entry.getKey(), tmdbId));
            }
        }
        return existing;
    }

    /**
     * 후보를 저장하고 새로 저장한 개수를 돌려준다.
     * 없는 작품은 요약 값으로 새로 만들고, 있는 작품은 포스터·배경·인기도·평점만 갱신한다(나머지는 Task 044가 상세로 채운다).
     */
    @Transactional
    public int saveCandidates(List<CandidateTitle> candidates, Instant fetchedAt) {
        int saved = 0;
        for (CandidateTitle c : candidates) {
            // 1. 이미 저장된 작품인지 먼저 확인한다
            // 같은 작품이 여러 서비스 목록에 나와도 UNIQUE(media_type, tmdb_id)를 건드리지 않게 먼저 확인한다
            Optional<Title> existing = titleRepository.findByMediaTypeAndTmdbId(c.mediaType(), c.tmdbId());
            // 2. 있으면 요약 값만 갱신하고 다음 후보로 간다
            if (existing.isPresent()) {
                // 조회한 엔티티는 영속 상태라 값이 바뀐 경우에만 커밋 때 UPDATE가 나간다(더티 체킹)
                existing.get().refreshSummary(c.posterPath(), c.backdropPath(), c.popularity(), c.voteAverage());
                continue;
            }
            // 3. 처음 보는 작품이면 새로 저장하고 개수를 센다
            titleRepository.save(Title.createFromSummary(c.mediaType(), c.tmdbId(), c.title(), c.originalTitle(),
                    c.releaseDate(), c.posterPath(), c.backdropPath(), c.originalLanguage(), c.popularity(),
                    c.voteAverage(), fetchedAt));
            saved++;
        }
        return saved;
    }
}
