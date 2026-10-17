package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import java.time.Instant;
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
