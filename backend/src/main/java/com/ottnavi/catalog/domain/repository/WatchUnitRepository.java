package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchUnitRepository extends JpaRepository<WatchUnit, Long> {
    /** 작품의 시청 단위 ID만 조회한다. 작품 삭제 정리가 찜·플랜 항목·제공 상태를 지울 대상을 정할 때 쓴다 */
    @Query("select u.id from WatchUnit u where u.title.id = :titleId")
    List<Long> findIdsByTitleId(@Param("titleId") Long titleId);

    /** 작품의 시청 단위를 벌크로 지운다. 제공 상태·변경 이력·찜·플랜 항목을 먼저 지운 뒤에만 부를 수 있다(FK가 막는다) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WatchUnit u where u.title.id = :titleId")
    int deleteByTitleId(@Param("titleId") Long titleId);

    /** 작품 하나의 시청 단위 전부를 조회한다. 수집이 이미 있는 단위를 갱신할지 새로 만들지 가를 때 쓴다 */
    List<WatchUnit> findByTitleId(Long titleId);

    /** 작품 여러 개의 시청 단위를 한 번에 조회한다(작품·시즌 번호 순). 조회 API가 작품마다 쿼리하지 않으려고 쓴다 */
    @Query("select u from WatchUnit u join fetch u.title "
            + "where u.title.id in :titleIds order by u.title.id asc, u.seasonNumber asc")
    List<WatchUnit> findByTitleIdIn(@Param("titleIds") Collection<Long> titleIds);

    /** 영화의 시청 단위 ID를 찾는다(영화는 시즌 번호가 없는 단위 하나다). 찜 추가가 쓴다 */
    @Query("select u.id from WatchUnit u "
            + "where u.title.mediaType = :mediaType and u.title.tmdbId = :tmdbId and u.seasonNumber is null")
    Optional<Long> findMovieUnitId(@Param("mediaType") MediaType mediaType, @Param("tmdbId") Integer tmdbId);

    /** 드라마 시즌의 시청 단위 ID를 찾는다. 찜 추가가 쓴다 */
    @Query("select u.id from WatchUnit u "
            + "where u.title.mediaType = :mediaType and u.title.tmdbId = :tmdbId and u.seasonNumber = :seasonNumber")
    Optional<Long> findSeasonUnitId(@Param("mediaType") MediaType mediaType, @Param("tmdbId") Integer tmdbId,
            @Param("seasonNumber") Integer seasonNumber);

    /** 시청 단위 ID 여러 개를 소속 작품과 함께 한 번에 조회한다. 찜 목록처럼 단위마다 쿼리하지 않으려고 쓴다 */
    @Query("select u from WatchUnit u join fetch u.title where u.id in :unitIds")
    List<WatchUnit> findWithTitleByIdIn(@Param("unitIds") Collection<Long> unitIds);
}
