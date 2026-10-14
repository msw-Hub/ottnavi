package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Genre;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.TitleCast;
import com.ottnavi.catalog.domain.entity.TitleGenre;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.repository.GenreRepository;
import com.ottnavi.catalog.domain.repository.TitleCastRepository;
import com.ottnavi.catalog.domain.repository.TitleGenreRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.CastCandidate;
import com.ottnavi.catalog.service.model.GenreInfo;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집 Job의 상세 Step이 쓰는 저장 서비스: title 갱신, 장르·출연진·시청 단위를 작품 단위로 맞춘다(Task 044) */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleDetailCollectService {

    private final TitleRepository titleRepository;
    private final GenreRepository genreRepository;
    private final TitleGenreRepository titleGenreRepository;
    private final TitleCastRepository titleCastRepository;
    private final WatchUnitRepository watchUnitRepository;

    /** 상세 수집 결과를 저장하고 저장한 작품 수를 돌려준다. 같은 입력으로 다시 불러도 결과가 같다(멱등) */
    @Transactional
    public int saveDetails(List<TitleDetail> details, Instant fetchedAt) {
        int saved = 0;
        for (TitleDetail detail : details) {
            Optional<Title> found = titleRepository.findByMediaTypeAndTmdbId(detail.mediaType(), detail.tmdbId());
            if (found.isEmpty()) {
                log.warn("상세를 저장할 작품이 DB에 없어 건너뛴다: mediaType={}, tmdbId={}", detail.mediaType(), detail.tmdbId());
                continue;
            }
            Title title = found.get();
            // 영속 상태의 엔티티라 값을 바꾸면 커밋 때 UPDATE가 나간다(더티 체킹). save를 부르지 않아도 된다
            // 새 응답의 줄거리가 한국어·영어 모두 비어 null이면 기존 줄거리와 언어 표시를 지우지 않고 유지한다
            boolean keepOverview = detail.overview() == null;
            String newTitle = detail.title();
            if (newTitle == null) {
                newTitle = title.getTitle(); // 새 응답에 제목이 없으면 기존 제목을 유지한다
            }
            String overview = detail.overview();
            String overviewLang = detail.overviewLang();
            if (keepOverview) {
                overview = title.getOverview();
                overviewLang = title.getOverviewLang();
            }
            title.updateDetail(newTitle, detail.originalTitle(), overview, overviewLang,
                    detail.releaseDate(), detail.posterPath(),
                    detail.backdropPath(), detail.originalLanguage(), detail.tvStatus(), detail.popularity(),
                    detail.voteAverage(), fetchedAt);
            syncGenres(title, detail.genres());
            syncCast(title, detail.cast());
            syncUnits(title, detail.units(), fetchedAt);
            saved++;
        }
        return saved;
    }

    /**
     * 장르 연결을 응답에 맞춘다: 없던 장르는 genre 표에 등록하고(시드가 없다), 빠진 연결은 지우고, 새 연결은 넣는다.
     * 같은 키를 지웠다 다시 넣으면 Hibernate가 INSERT를 먼저 실행해 위반이 나므로(TECH 6절) 차이만 반영한다.
     */
    private void syncGenres(Title title, List<GenreInfo> incoming) {
        Map<Integer, String> wanted = new LinkedHashMap<>();
        for (GenreInfo genre : incoming) {
            if (genre.name() == null || genre.name().isBlank()) {
                log.warn("이름이 없는 장르는 건너뛴다: tmdbId={}, genreId={}", title.getTmdbId(), genre.id());
                continue;
            }
            wanted.putIfAbsent(genre.id(), genre.name());
        }
        Map<Integer, Genre> known = new HashMap<>();
        for (Genre genre : genreRepository.findAllById(wanted.keySet())) {
            known.put(genre.getId(), genre);
        }
        // genre 표에 없는 장르는 새로 등록한다
        for (Map.Entry<Integer, String> entry : wanted.entrySet()) {
            if (!known.containsKey(entry.getKey())) {
                known.put(entry.getKey(), genreRepository.save(Genre.create(entry.getKey(), entry.getValue())));
            }
        }

        List<TitleGenre> existing = titleGenreRepository.findByTitleId(title.getId());
        List<TitleGenre> toDelete = new ArrayList<>(); // 이번 응답에서 빠진 연결
        List<Integer> alreadyLinked = new ArrayList<>();
        for (TitleGenre link : existing) {
            Integer genreId = link.getId().getGenreId();
            alreadyLinked.add(genreId);
            if (!wanted.containsKey(genreId)) {
                toDelete.add(link);
            }
        }
        titleGenreRepository.deleteAll(toDelete);
        for (Integer genreId : wanted.keySet()) {
            if (!alreadyLinked.contains(genreId)) {
                titleGenreRepository.save(TitleGenre.create(title, known.get(genreId)));
            }
        }
    }

    /** 출연진을 출연 순서 자리(0부터)마다 제자리에서 갱신하고, 남는 자리는 지운다 */
    private void syncCast(Title title, List<CastCandidate> incoming) {
        Map<Integer, TitleCast> byOrder = new LinkedHashMap<>();
        for (TitleCast cast : titleCastRepository.findByTitleIdOrderByCastOrder(title.getId())) {
            byOrder.put(cast.getCastOrder(), cast);
        }
        for (int order = 0; order < incoming.size(); order++) {
            CastCandidate candidate = incoming.get(order);
            TitleCast current = byOrder.remove(order);
            if (current == null) {
                titleCastRepository.save(TitleCast.create(title, order, Math.toIntExact(candidate.personTmdbId()),
                        candidate.name(), candidate.character(), candidate.profilePath()));
            } else {
                current.updatePerson(Math.toIntExact(candidate.personTmdbId()), candidate.name(),
                        candidate.character(), candidate.profilePath());
            }
        }
        titleCastRepository.deleteAll(byOrder.values()); // 이번 응답에 없는 자리(출연진이 줄어든 경우)
    }

    /** 시청 단위를 시즌 번호(영화는 null)로 맞춰 갱신하거나 새로 만든다. 응답에 없는 기존 단위는 건드리지 않는다 */
    private void syncUnits(Title title, List<UnitDetail> incoming, Instant fetchedAt) {
        Map<Integer, WatchUnit> bySeason = new HashMap<>(); // 영화 단위의 키는 null
        for (WatchUnit unit : watchUnitRepository.findByTitleId(title.getId())) {
            bySeason.put(unit.getSeasonNumber(), unit);
        }
        for (UnitDetail detail : incoming) {
            WatchUnit current = bySeason.get(detail.seasonNumber());
            if (current != null) {
                current.applyCollected(detail.tmdbSeasonId(), detail.name(), detail.posterPath(), detail.airDate(),
                        detail.runtime(), fetchedAt);
            } else if (detail.unitType() == UnitType.MOVIE) {
                watchUnitRepository.save(WatchUnit.createMovie(title, detail.runtime(), fetchedAt));
            } else {
                watchUnitRepository.save(WatchUnit.createSeason(title, detail.seasonNumber(), detail.tmdbSeasonId(),
                        detail.name(), detail.posterPath(), detail.airDate(), detail.runtime(), fetchedAt));
            }
        }
    }
}
