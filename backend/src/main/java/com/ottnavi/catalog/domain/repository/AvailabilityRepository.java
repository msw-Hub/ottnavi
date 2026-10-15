package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    /** 시청 단위 여러 개의 제공 상태를 한 번에 조회한다. 수집 chunk가 단위마다 쿼리하지 않으려고 쓴다 */
    List<Availability> findByWatchUnitIdIn(Collection<Long> watchUnitIds);

    /** 조회 API용: 시청 단위·서비스를 함께 가져와 ID를 읽을 때 추가 쿼리가 나가지 않게 한다 */
    @Query("select a from Availability a join fetch a.watchUnit join fetch a.ottService "
            + "where a.watchUnit.id in :watchUnitIds")
    List<Availability> findWithServiceByWatchUnitIdIn(@Param("watchUnitIds") Collection<Long> watchUnitIds);

    /** 전체 제공 상태 중 가장 최근 확인 시각. 판정이 하나도 없으면 null */
    @Query("select max(a.checkedAt) from Availability a")
    Instant findLatestCheckedAt();
}
