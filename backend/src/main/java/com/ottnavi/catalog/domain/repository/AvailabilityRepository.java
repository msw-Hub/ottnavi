package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    /** 시청 단위 여러 개의 제공 상태를 한 번에 조회한다. 수집 chunk가 단위마다 쿼리하지 않으려고 쓴다 */
    List<Availability> findByWatchUnitIdIn(Collection<Long> watchUnitIds);
}
