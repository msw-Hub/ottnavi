package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.rule.ExclusiveSourceRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    /** 시청 단위들의 제공 상태를 벌크로 지운다. 작품 삭제 정리가 쓴다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Availability a where a.watchUnit.id in :watchUnitIds")
    int deleteByWatchUnitIdIn(@Param("watchUnitIds") Collection<Long> watchUnitIds);

    /** 시청 단위 여러 개의 제공 상태를 한 번에 조회한다. 수집 chunk가 단위마다 쿼리하지 않으려고 쓴다 */
    List<Availability> findByWatchUnitIdIn(Collection<Long> watchUnitIds);

    /** 조회 API용: 시청 단위·서비스를 함께 가져와 ID를 읽을 때 추가 쿼리가 나가지 않게 한다 */
    @Query("select a from Availability a join fetch a.watchUnit join fetch a.ottService "
            + "where a.watchUnit.id in :watchUnitIds")
    List<Availability> findWithServiceByWatchUnitIdIn(@Param("watchUnitIds") Collection<Long> watchUnitIds);

    /** 전체 제공 상태 중 가장 최근 확인 시각. 판정이 하나도 없으면 null */
    @Query("select max(a.checkedAt) from Availability a")
    Instant findLatestCheckedAt();

    /**
     * 독점 판정용: 모든 제공 상태를 판정에 필요한 값만 읽는다(엔티티를 만들지 않는다).
     * 소속 작품은 watch_unit의 title_id 컬럼으로 읽어 title 테이블을 조인하지 않는다.
     */
    @Query("select new com.ottnavi.catalog.domain.rule.ExclusiveSourceRow(a.watchUnit.id, a.watchUnit.title.id, "
            + "a.watchUnit.unitType, a.watchUnit.seasonNumber, a.ottService.id, a.status, a.unknownReason, a.checkedAt) "
            + "from Availability a")
    List<ExclusiveSourceRow> findAllExclusiveSourceRows();
}
