package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.enums.MediaType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TitleRepository extends JpaRepository<Title, Long> {

    /** 목록 정렬의 첫 키: 0 = 개봉일이 최근 기간 안, 1 = 그보다 오래됨, 2 = 개봉일이 미래이거나 모름. RecentReleaseRules.group과 같은 규칙이다 */
    String RECENT_GROUP_ORDER = "case when t.releaseDate is null or t.releaseDate > :recentTo then 2 "
            + "when t.releaseDate >= :recentFrom then 0 else 1 end asc";

    /** 종류와 TMDB ID로 작품을 찾는다. 수집이 이미 있는 작품인지 확인할 때 쓴다 */
    Optional<Title> findByMediaTypeAndTmdbId(MediaType mediaType, Integer tmdbId);

    /**
     * 제목·원제에 패턴이 들어가는 작품을 한 페이지 조회한다.
     * 정렬: 개봉일이 recentFrom~recentTo(최근 기간) 안인 작품 → 그보다 오래된 작품 → 개봉일이 recentTo 뒤(미래)이거나 모르는 작품 순이고,
     * 각 그룹 안은 인기도 내림차순(동률은 id 오름차순)이다. 인기도가 비어 있는 작품은 뒤로 보낸다(PostgreSQL은 DESC에서 NULL을 먼저 두기 때문).
     * 패턴은 호출 쪽이 %·_·\를 이스케이프하고 앞뒤에 %를 붙여 넘긴다(이스케이프 문자는 escapeChar).
     */
    @Query(value = "select t from Title t "
            + "where t.title ilike :pattern escape :escapeChar or t.originalTitle ilike :pattern escape :escapeChar "
            + "order by " + RECENT_GROUP_ORDER + ", t.popularity desc nulls last, t.id asc",
            countQuery = "select count(t) from Title t "
                    + "where t.title ilike :pattern escape :escapeChar or t.originalTitle ilike :pattern escape :escapeChar")
    Page<Title> searchByTitle(@Param("pattern") String pattern, @Param("escapeChar") char escapeChar,
            @Param("recentFrom") LocalDate recentFrom, @Param("recentTo") LocalDate recentTo, Pageable pageable);

    /** 작품 구분 하나와 TMDB ID 여러 개로 작품을 한 번에 찾는다. 홈 인기 순위가 스냅샷의 작품을 카드로 바꿀 때 쓴다 */
    List<Title> findByMediaTypeAndTmdbIdIn(MediaType mediaType, Collection<Integer> tmdbIds);

    /** 장르에 연결된 작품을 한 페이지 조회한다. 정렬은 {@link #searchByTitle}와 같다(최근 개봉 그룹 → 과거 → 미래·모름, 그룹 안은 인기도 내림차순) */
    @Query(value = "select t from Title t where t.id in (select tg.title.id from TitleGenre tg where tg.genre.id = :genreId) "
            + "order by " + RECENT_GROUP_ORDER + ", t.popularity desc nulls last, t.id asc",
            countQuery = "select count(t) from Title t "
                    + "where t.id in (select tg.title.id from TitleGenre tg where tg.genre.id = :genreId)")
    Page<Title> findPageByGenreId(@Param("genreId") Integer genreId,
            @Param("recentFrom") LocalDate recentFrom, @Param("recentTo") LocalDate recentTo, Pageable pageable);

    /**
     * 이름이 같은(대소문자 무시) 출연자가 나온 작품을 한 페이지 조회한다. 정렬은 {@link #searchByTitle}와 같다.
     * 출연진은 작품당 상위 10명만 저장돼 있어 그 범위의 결과다.
     */
    @Query(value = "select t from Title t where t.id in (select c.title.id from TitleCast c where lower(c.name) = lower(:name)) "
            + "order by " + RECENT_GROUP_ORDER + ", t.popularity desc nulls last, t.id asc",
            countQuery = "select count(t) from Title t "
                    + "where t.id in (select c.title.id from TitleCast c where lower(c.name) = lower(:name))")
    Page<Title> findPageByCastName(@Param("name") String name,
            @Param("recentFrom") LocalDate recentFrom, @Param("recentTo") LocalDate recentTo, Pageable pageable);

    /** 마지막 수집 시각이 기준보다 이른(오래된) 작품 수. 6개월 갱신 점검이 센다 */
    long countByFetchedAtBefore(Instant cutoff);

    /** 마지막 수집 시각이 기준보다 이른 작품을 가장 오래된 순(같으면 id 순)으로 조회한다. 한 번에 처리할 건수는 Pageable로 자른다 */
    List<Title> findByFetchedAtBeforeOrderByFetchedAtAscIdAsc(Instant cutoff, Pageable pageable);

    /**
     * 찜 행(wishlist_item)이 하나라도 있는데 WEEKLY인 작품을 DAILY로 바꾼다. 바뀐 작품 수를 돌려준다.
     * 찜은 시청 단위(watch_unit)에 걸리므로 watch_unit.title_id로 작품을 찾는다. "다 봤음" 여부는 보지 않는다.
     * 벌크 SQL이라 영속성 컨텍스트를 건드리지 않고 updated_at을 직접 채운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE title SET refresh_tier = 'DAILY', updated_at = now() "
            + "WHERE refresh_tier = 'WEEKLY' AND EXISTS ("
            + "SELECT 1 FROM watch_unit u JOIN wishlist_item w ON w.watch_unit_id = u.id WHERE u.title_id = title.id)",
            nativeQuery = true)
    int promoteWishlistedToDaily();

    /** 찜 행이 하나도 없는데 DAILY인 작품을 WEEKLY로 되돌린다(찜 삭제·회원 탈퇴 뒤). 바뀐 작품 수를 돌려준다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE title SET refresh_tier = 'WEEKLY', updated_at = now() "
            + "WHERE refresh_tier = 'DAILY' AND NOT EXISTS ("
            + "SELECT 1 FROM watch_unit u JOIN wishlist_item w ON w.watch_unit_id = u.id WHERE u.title_id = title.id)",
            nativeQuery = true)
    int demoteUnwishlistedToWeekly();

    /**
     * 번역 후보(영어 줄거리가 비어 있지 않은 작품)를 찜한 사용자 수 많은 순 → 인기도 순(동률은 id 순)으로 한 페이지 조회한다.
     * 찜은 시청 단위(watch_unit)에 걸리므로 작품별로 사용자 수를 DISTINCT로 센다(드라마는 시즌별로 찜해도 한 명).
     * 별칭은 따옴표로 감싸 PostgreSQL이 소문자로 바꾸지 못하게 한다(투영 getter 이름과 맞추려고).
     */
    @Query(value = "SELECT t.id AS \"id\", t.media_type AS \"mediaType\", t.title AS \"title\", t.overview AS \"overview\", "
            + "COALESCE(w.cnt, 0) AS \"wishlistCount\" FROM title t "
            + "LEFT JOIN (SELECT u.title_id AS title_id, COUNT(DISTINCT i.user_id) AS cnt FROM watch_unit u "
            + "JOIN wishlist_item i ON i.watch_unit_id = u.id GROUP BY u.title_id) w ON w.title_id = t.id "
            + "WHERE t.overview_lang = 'en' AND t.overview IS NOT NULL AND btrim(t.overview) <> '' "
            + "ORDER BY COALESCE(w.cnt, 0) DESC, t.popularity DESC NULLS LAST, t.id ASC",
            countQuery = "SELECT count(*) FROM title t "
                    + "WHERE t.overview_lang = 'en' AND t.overview IS NOT NULL AND btrim(t.overview) <> ''",
            nativeQuery = true)
    Page<OverviewCandidateRow> findOverviewCandidates(Pageable pageable);

    /**
     * 영어 줄거리를 한국어 번역으로 바꾸고 번역 반영 시각(overview_translated_at)을 지금으로 적는다.
     * 줄거리가 아직 영어이고 내용이 originalOverview와 같을 때만 바꾼다
     * (그 사이 수집이 줄거리를 바꿨거나 다른 번역이 먼저 반영됐으면 0건). 바뀐 행 수를 돌려준다.
     * <p>
     * 왜 조건을 붙이나: 초안은 Redis에 최대 7일 있고, 그동안 매일 도는 TMDB 수집이 줄거리를 바꿀 수 있다.
     * 번역문은 "번역할 때의 영어 원문"에 대한 것이므로, 줄거리가 그 원문이 아니게 됐다면 덮어쓰면 안 된다.
     * 예) 월요일 영어 원문(en)으로 초안 생성 → 화요일 수집이 TMDB 한국어(ko)를 채움 → 수요일 반영하면
     *     overview_lang이 'ko'라 WHERE에서 걸려 0건이 되고, TMDB가 준 한국어가 그대로 남는다.
     * 확인과 변경을 SELECT 후 UPDATE로 나누지 않고 UPDATE 한 문장의 WHERE에 합친 이유: 그 사이에 수집이 끼어들 틈을 없애려는 것이다
     * (DB가 확인과 변경을 한 번에 처리한다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE title SET overview = :translated, overview_lang = 'ko', overview_translated_at = now(), "
            + "updated_at = now() "
            + "WHERE id = :titleId AND overview_lang = 'en' AND overview = :originalOverview", nativeQuery = true)
    int applyKoreanOverview(@Param("titleId") Long titleId, @Param("originalOverview") String originalOverview,
            @Param("translated") String translated);

    /**
     * 관리자가 반영한 번역(overview_translated_at이 있음)만 비운다(overview, overview_lang, overview_translated_at을 null로).
     * TMDB가 원래 준 한국어 줄거리는 건드리지 않는다. 다음 수집이 TMDB 줄거리를 다시 채운다. 바뀐 행 수를 돌려준다
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE title SET overview = NULL, overview_lang = NULL, overview_translated_at = NULL, "
            + "updated_at = now() WHERE id = :titleId AND overview_translated_at IS NOT NULL", nativeQuery = true)
    int clearKoreanOverview(@Param("titleId") Long titleId);

    /**
     * 번역(overview_translated_at이 있음) 중 반영 시각이 translatedBefore보다 오래된 작품의 키를 id 순으로 limit건 읽는다.
     * 대량 되돌리기가 한 묶음씩 읽을 때 쓴다. 되돌린 행은 조건에서 빠지므로 같은 조건으로 다시 읽으면 다음 묶음이 나온다.
     */
    @Query(value = "SELECT t.id AS \"id\", t.media_type AS \"mediaType\", t.tmdb_id AS \"tmdbId\" FROM title t "
            + "WHERE t.overview_translated_at IS NOT NULL AND t.overview_translated_at < :translatedBefore "
            + "ORDER BY t.id ASC LIMIT :limit", nativeQuery = true)
    List<TranslatedTitleRow> findTranslatedTitles(@Param("translatedBefore") Instant translatedBefore,
            @Param("limit") int limit);

    /** 번역(overview_translated_at이 있음) 중 반영 시각이 translatedBefore보다 오래된 작품 수. 대량 되돌리기의 미리보기가 쓴다 */
    @Query(value = "SELECT count(*) FROM title t WHERE t.overview_translated_at IS NOT NULL "
            + "AND t.overview_translated_at < :translatedBefore", nativeQuery = true)
    long countTranslatedBefore(@Param("translatedBefore") Instant translatedBefore);

    /** 주어진 작품들 중 번역인 것만 비운다(단건 되돌리기와 같은 효과). 그 사이 번역이 아니게 된 작품은 건너뛴다. 바뀐 행 수를 돌려준다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE title SET overview = NULL, overview_lang = NULL, overview_translated_at = NULL, "
            + "updated_at = now() WHERE id IN (:titleIds) AND overview_translated_at IS NOT NULL", nativeQuery = true)
    int clearTranslatedOverviews(@Param("titleIds") Collection<Long> titleIds);

    /** 관리자가 반영한 번역 작품 수 */
    long countByOverviewTranslatedAtIsNotNull();

    /** 가장 오래된 번역의 반영 시각. 번역이 하나도 없으면 null */
    @Query("select min(t.overviewTranslatedAt) from Title t")
    Instant findOldestOverviewTranslatedAt();

    /** 작품 한 행을 벌크로 지운다. 하위 행(시청 단위·장르·출연진)은 호출 전에 먼저 지워야 한다(FK가 막는다) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Title t where t.id = :titleId")
    int deleteByTitleId(@Param("titleId") Long titleId);
}
