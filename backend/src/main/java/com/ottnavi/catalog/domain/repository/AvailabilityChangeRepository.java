package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityChangeRepository extends JpaRepository<AvailabilityChange, Long> {

    /** 시청 단위들의 제공 상태 변경 이력을 벌크로 지운다. 작품 삭제 정리가 쓴다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AvailabilityChange c where c.watchUnit.id in :unitIds")
    int deleteByWatchUnitIdIn(@Param("unitIds") Collection<Long> unitIds);

    /** 시청 단위별 가장 최근 변경 시각. 변경이 없는 단위는 행이 없다. 각 행은 [watchUnitId(Long), 최근 changedAt(Instant)] */
    @Query("select c.watchUnit.id, max(c.changedAt) from AvailabilityChange c "
            + "where c.watchUnit.id in :unitIds group by c.watchUnit.id")
    List<Object[]> findLatestChangedAtByUnitIds(@Param("unitIds") Collection<Long> unitIds);

    /** 시청 단위들의 변경 중 since 이후(초과)인 것을 시각 순으로 조회한다 */
    @Query("select c from AvailabilityChange c where c.watchUnit.id in :unitIds and c.changedAt > :since "
            + "order by c.changedAt asc, c.id asc")
    List<AvailabilityChange> findByUnitIdsChangedAfter(@Param("unitIds") Collection<Long> unitIds,
            @Param("since") Instant since);

    /**
     * 입고(ARRIVAL) 후보: since 이후에 없음·모름에서 있음으로 바뀐 기록. 첫 적재(before가 null)는 제외한다.
     * 지금도 그 서비스에서 있음인 것만 센다(그 뒤 빠진 작품은 "새로 볼 수 있게 됨"이 아니다). 시청 단위·작품을 함께 읽어 N+1을 피한다.
     */
    @Query("select c from AvailabilityChange c join fetch c.watchUnit u join fetch u.title t join fetch c.ottService s "
            + "where c.afterStatus = com.ottnavi.catalog.domain.enums.AvailabilityStatus.AVAILABLE "
            + "and c.beforeStatus is not null and c.beforeStatus <> com.ottnavi.catalog.domain.enums.AvailabilityStatus.AVAILABLE "
            + "and c.changedAt >= :since "
            + "and exists (select 1 from Availability a where a.watchUnit = u and a.ottService = s "
            + "and a.status = com.ottnavi.catalog.domain.enums.AvailabilityStatus.AVAILABLE)")
    List<AvailabilityChange> findArrivalsSince(@Param("since") Instant since);

    /**
     * 공개(RELEASE) 후보: 첫 적재(before가 null)로 바로 있음이 됐고, 공개일이 [from, to]에 드는 기록.
     * 공개일은 영화면 작품의 개봉일, 드라마면 시즌(시청 단위)의 방영일이다. 공개일이 null이면 조건에 맞지 않아 빠진다.
     * 지금도 그 서비스에서 있음인 것만 센다.
     */
    @Query("select c from AvailabilityChange c join fetch c.watchUnit u join fetch u.title t join fetch c.ottService s "
            + "where c.afterStatus = com.ottnavi.catalog.domain.enums.AvailabilityStatus.AVAILABLE "
            + "and c.beforeStatus is null "
            + "and ((t.mediaType = com.ottnavi.catalog.domain.enums.MediaType.MOVIE and t.releaseDate between :from and :to) "
            + "or (t.mediaType = com.ottnavi.catalog.domain.enums.MediaType.TV and u.airDate between :from and :to)) "
            + "and exists (select 1 from Availability a where a.watchUnit = u and a.ottService = s "
            + "and a.status = com.ottnavi.catalog.domain.enums.AvailabilityStatus.AVAILABLE)")
    List<AvailabilityChange> findFirstLoadsReleasedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 수집 실행 ID별 변경 건수. 변경이 없는 실행은 행이 없다. 각 행은 [jobExecutionId(Long), count(Long)] */
    @Query("select c.jobExecutionId, count(c) from AvailabilityChange c "
            + "where c.jobExecutionId in :ids group by c.jobExecutionId")
    List<Object[]> countGroupedByJobExecutionId(@Param("ids") List<Long> ids);

    /**
     * 한 실행의 변경을 제목·시즌 번호·서비스 ID 순으로 한 페이지 조회한다. 시청 단위·작품·서비스를 같이 읽는다(to-one이라 페이징과 함께 쓴다).
     * 영화의 시즌 번호는 null이고 PostgreSQL은 오름차순에서 null을 뒤에 둔다.
     */
    @Query(value = "select c from AvailabilityChange c join fetch c.watchUnit u join fetch u.title t "
            + "join fetch c.ottService s where c.jobExecutionId = :id "
            + "order by t.title asc, u.seasonNumber asc, s.id asc, c.id asc",
            countQuery = "select count(c) from AvailabilityChange c where c.jobExecutionId = :id")
    Page<AvailabilityChange> findPageByJobExecutionId(@Param("id") Long jobExecutionId, Pageable pageable);
}
