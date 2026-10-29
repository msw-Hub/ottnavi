package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.repository.OverviewCandidateRow;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.TranslatedTitleRow;
import com.ottnavi.catalog.service.model.OverviewCandidatePage;
import com.ottnavi.catalog.service.model.OverviewCandidateView;
import com.ottnavi.catalog.service.model.OverviewSourceView;
import com.ottnavi.catalog.service.model.OverviewTranslationStats;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.catalog.service.model.TranslatedRevertBatch;
import com.ottnavi.global.config.RedisConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 줄거리 번역을 위한 작품 줄거리 조회·반영·되돌리기(llmdraft가 호출한다).
 * 이 서비스는 DB와 작품 상세 캐시만 다루고 LLM은 모른다. 반영·되돌리기는 DB 갱신만 트랜잭션으로 묶고,
 * 상세 캐시 삭제는 커밋 뒤에 호출하는 쪽이 evictDetailCache로 따로 한다(커밋 전에 지우면 그 사이 읽기가 옛 값을 다시 캐시할 수 있다).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TitleOverviewService {

    /** 기준 시각 없이 번역 전부를 되돌릴 때 쓰는 "어떤 번역 시각보다도 늦은" 값(PostgreSQL timestamptz 범위 안) */
    private static final Instant ALL_TRANSLATED_BEFORE = Instant.parse("9999-12-31T00:00:00Z");

    private final TitleRepository titleRepository;
    private final CacheManager cacheManager;

    /** 번역 후보를 찜한 사용자 수 많은 순 → 인기도 순으로 한 페이지 돌려준다. 총수는 번역 대기 중인 영어 줄거리 작품 수다 */
    public OverviewCandidatePage findCandidates(int page, int size) {
        Page<OverviewCandidateRow> found = titleRepository.findOverviewCandidates(PageRequest.of(page, size));
        List<OverviewCandidateView> content = new ArrayList<>();
        for (OverviewCandidateRow row : found.getContent()) {
            long wishlistCount = 0;
            if (row.getWishlistCount() != null) {
                wishlistCount = row.getWishlistCount();
            }
            content.add(new OverviewCandidateView(row.getId(), MediaType.valueOf(row.getMediaType()), row.getTitle(),
                    row.getOverview(), wishlistCount));
        }
        return new OverviewCandidatePage(content, page, size, found.getTotalElements(), found.getTotalPages());
    }

    /** 번역 대기 중인 영어 줄거리 작품의 총수 */
    public long countCandidates() {
        return titleRepository.findOverviewCandidates(PageRequest.of(0, 1)).getTotalElements();
    }

    /** 후보 정렬(찜 많은 순 → 인기순)의 한 페이지에 든 작품 ID들. 일괄 번역이 상위 N건을 고를 때 쓴다 */
    public List<Long> findCandidateTitleIds(int page, int size) {
        Page<OverviewCandidateRow> found = titleRepository.findOverviewCandidates(PageRequest.of(page, size));
        List<Long> ids = new ArrayList<>();
        for (OverviewCandidateRow row : found.getContent()) {
            ids.add(row.getId());
        }
        return ids;
    }

    /** 작품의 현재 줄거리 상태를 읽는다. 없는 작품이면 비어 있다 */
    public Optional<OverviewSourceView> findSource(Long titleId) {
        Optional<Title> found = titleRepository.findById(titleId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Title title = found.get();
        return Optional.of(new OverviewSourceView(title.getId(), title.getMediaType(), title.getTmdbId(),
                title.getTitle(), title.getOverview(), title.getOverviewLang(), title.isOverviewTranslated()));
    }

    /** 관리자가 반영한 번역의 작품 수와 가장 오래된 반영 시각을 센다(번역 6개월 경과 여부를 보려는 통계) */
    public OverviewTranslationStats findTranslationStats() {
        return new OverviewTranslationStats(titleRepository.countByOverviewTranslatedAtIsNotNull(),
                titleRepository.findOldestOverviewTranslatedAt());
    }

    /**
     * 영어 줄거리를 번역문으로 바꾼다. 작품의 줄거리가 아직 영어이고 originalOverview와 똑같을 때만 바꾸고,
     * 바뀌었으면 작품 키를, 조건이 맞지 않아 바꾸지 못했으면 빈 값을 돌려준다.
     */
    @Transactional
    public Optional<TitleKey> applyKoreanOverview(Long titleId, String originalOverview, String translated) {
        // 1. 캐시 키를 만들 작품 키를 먼저 읽는다(벌크 UPDATE는 영속성 컨텍스트를 비운다)
        Optional<Title> found = titleRepository.findById(titleId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TitleKey key = new TitleKey(found.get().getMediaType(), found.get().getTmdbId());
        // 2. 조건부 갱신: 줄거리가 아직 영어이고 번역할 때의 원문과 같을 때만 1건이 바뀐다(번역 도장도 이때 찍힌다).
        //    초안을 만든 뒤 수집이 줄거리를 바꿨다면 0건이라 번역문으로 덮어쓰지 않는다
        int updated = titleRepository.applyKoreanOverview(titleId, originalOverview, translated);
        if (updated == 0) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    /**
     * 관리자가 반영한 번역을 비운다(overview, overview_lang, overview_translated_at → null). 다음 수집이 TMDB 영어 줄거리를 다시 채운다.
     * 바꿨으면 작품 키를, 번역이 아니어서(TMDB 한국어 포함) 바꾸지 못했으면 빈 값을 돌려준다.
     */
    @Transactional
    public Optional<TitleKey> clearKoreanOverview(Long titleId) {
        Optional<Title> found = titleRepository.findById(titleId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TitleKey key = new TitleKey(found.get().getMediaType(), found.get().getTmdbId());
        int updated = titleRepository.clearKoreanOverview(titleId);
        if (updated == 0) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    /**
     * 대량 되돌리기의 대상 작품 수를 센다(DB를 바꾸지 않는다). translatedBefore가 null이면 번역 전부, 있으면 그 시각보다 먼저 반영한 번역만 센다.
     */
    public long countTranslatedForRevert(Instant translatedBefore) {
        Instant before = translatedBefore;
        if (before == null) {
            before = ALL_TRANSLATED_BEFORE;
        }
        return titleRepository.countTranslatedBefore(before);
    }

    /**
     * 번역 한 묶음(최대 limit건)을 영어로 되돌린다. translatedBefore보다 오래된 번역만 대상이고, null이면 번역 전부가 대상이다.
     * 읽은 작품 키를 돌려주므로 호출하는 쪽이 커밋 뒤에 상세 캐시를 지운다. 되돌린 행은 조건에서 빠지므로
     * 키가 빈 묶음이 나올 때까지 같은 조건으로 반복 호출하면 대상을 모두 처리한다.
     */
    @Transactional
    public TranslatedRevertBatch revertTranslatedBatch(Instant translatedBefore, int limit) {
        // 1. 대상 범위를 정한다(기준 시각이 없으면 어떤 번역 시각보다도 늦은 값을 넣어 번역 전부를 대상으로 한다)
        Instant before = translatedBefore;
        if (before == null) {
            before = ALL_TRANSLATED_BEFORE;
        }

        // 2. 캐시 키를 만들 작품 키를 먼저 읽는다(UPDATE 뒤에는 조건에서 빠져 읽을 수 없다)
        List<TranslatedTitleRow> rows = titleRepository.findTranslatedTitles(before, limit);
        if (rows.isEmpty()) {
            return new TranslatedRevertBatch(List.of(), 0);
        }
        List<Long> titleIds = new ArrayList<>();
        List<TitleKey> keys = new ArrayList<>();
        for (TranslatedTitleRow row : rows) {
            titleIds.add(row.getId());
            keys.add(new TitleKey(MediaType.valueOf(row.getMediaType()), row.getTmdbId()));
        }

        // 3. 조건부 갱신: 읽은 뒤 번역이 아니게 된 작품은 0건으로 건너뛴다
        int updated = titleRepository.clearTranslatedOverviews(titleIds);
        return new TranslatedRevertBatch(keys, updated);
    }

    /**
     * 작품 상세 캐시 한 건을 지운다(키는 TitleDetailQueryService와 같은 "{mediaType}:{tmdbId}").
     * 실패해도 DB는 이미 바뀌었으므로 경고만 남긴다(상세 캐시는 TTL 24시간 뒤 저절로 새 값이 된다).
     */
    public void evictDetailCache(TitleKey key) {
        try {
            Cache cache = cacheManager.getCache(RedisConfig.TITLE_DETAIL);
            if (cache != null) {
                cache.evict(key.mediaType().toApiValue() + ":" + key.tmdbId());
            }
        } catch (RuntimeException e) {
            log.warn("작품 상세 캐시를 지우지 못했다(TTL 24시간 뒤 새 값이 된다): mediaType={}, tmdbId={}, error={}",
                    key.mediaType(), key.tmdbId(), e.toString());
        }
    }
}
