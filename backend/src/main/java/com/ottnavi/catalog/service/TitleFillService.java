package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.KrProviders;
import com.ottnavi.catalog.service.model.FetchedTitle;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.UnitProviders;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인기 순위에 오른 작품을 title 표에 새로 채우는 저장 서비스. TMDB는 부르지 않고, 호출 쪽이 받아 둔 값(FetchedTitle)만 저장한다.
 * 작품 하나를 한 트랜잭션으로 저장해 "작품은 있는데 제공 상태가 없는" 중간 상태가 보이지 않게 한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleFillService {

    private final TitleRepository titleRepository;
    private final WatchUnitRepository watchUnitRepository;
    private final TitleDetailCollectService titleDetailCollectService;
    private final AvailabilityCollectService availabilityCollectService;

    /**
     * 작품 하나를 title, 장르, 출연진, 시청 단위, 제공 상태, 제공 상태 이력까지 저장한다. 새로 저장했으면 true다.
     * 이미 같은 (종류, TMDB ID)의 작품이 있으면 아무것도 바꾸지 않고 false다.
     * 동시에 같은 작품이 들어와 UNIQUE를 어기면 DataIntegrityViolationException을 그대로 던진다(호출 쪽이 그 작품만 건너뛴다).
     *
     * @param fallbackTitle TMDB 응답에 제목이 없을 때 쓸 제목(인기 스냅샷에 기록된 제목)
     */
    @Transactional
    public boolean saveFetched(FetchedTitle fetched, String fallbackTitle, Instant fetchedAt) {
        TitleDetail detail = fetched.detail();

        // 1. 이미 있는 작품이면 건드리지 않는다(TMDB를 받는 사이 수집이 먼저 넣었을 수 있어 저장 직전에 다시 확인한다)
        if (titleRepository.findByMediaTypeAndTmdbId(detail.mediaType(), detail.tmdbId()).isPresent()) {
            return false;
        }

        // 2. title 행을 만든다. 갱신 기준(refresh_tier)은 WEEKLY다(찜하면 DAILY로 올라간다)
        String title = detail.title();
        if (title == null) {
            title = fallbackTitle;
        }
        Title saved = titleRepository.save(Title.createFromSummary(detail.mediaType(), detail.tmdbId(), title,
                detail.originalTitle(), detail.releaseDate(), detail.posterPath(), detail.backdropPath(),
                detail.originalLanguage(), detail.popularity(), detail.voteAverage(), fetchedAt));

        // 3. 상세(줄거리·장르·출연진·시청 단위)를 채운다. 같은 트랜잭션에 합류하고, 방금 넣은 title 행을 찾아 갱신한다
        // title은 IDENTITY 키라 save 시점에 INSERT가 나가고, 이어지는 조회(JPQL)는 Hibernate가 자동 flush해서 앞 단계 행이 보인다
        titleDetailCollectService.saveDetails(List.of(detail), fetchedAt);

        // 4. 시청 단위를 읽어 시즌 번호로 제공처를 짝지어 판정 입력을 만든다
        List<UnitProviders> items = new ArrayList<>();
        for (WatchUnit unit : watchUnitRepository.findByTitleId(saved.getId())) {
            KrProviders providers = fetched.providersBySeason().get(unit.getSeasonNumber());
            if (providers == null) {
                log.warn("시청 단위에 맞는 제공처 정보가 없어 제공 상태를 만들지 않는다: titleId={}, seasonNumber={}",
                        saved.getId(), unit.getSeasonNumber());
                continue;
            }
            items.add(new UnitProviders(unit.getId(), providers));
        }

        // 5. 제공 상태를 판정해 저장하고 이력을 남긴다. 수집 Job 밖의 변경이라 jobExecutionId는 null이다(ERD)
        availabilityCollectService.apply(items, null, fetchedAt);
        return true;
    }
}
