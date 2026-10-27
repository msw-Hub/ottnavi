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

    /** 작품 한 행을 벌크로 지운다. 하위 행(시청 단위·장르·출연진)은 호출 전에 먼저 지워야 한다(FK가 막는다) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Title t where t.id = :titleId")
    int deleteByTitleId(@Param("titleId") Long titleId);
}
