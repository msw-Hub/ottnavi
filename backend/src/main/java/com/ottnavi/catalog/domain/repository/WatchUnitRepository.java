package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.WatchUnit;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchUnitRepository extends JpaRepository<WatchUnit, Long> {
    /** 작품 하나의 시청 단위 전부를 조회한다. 수집이 이미 있는 단위를 갱신할지 새로 만들지 가를 때 쓴다 */
    List<WatchUnit> findByTitleId(Long titleId);

    /** 작품 여러 개의 시청 단위를 한 번에 조회한다(작품·시즌 번호 순). 조회 API가 작품마다 쿼리하지 않으려고 쓴다 */
    @Query("select u from WatchUnit u join fetch u.title "
            + "where u.title.id in :titleIds order by u.title.id asc, u.seasonNumber asc")
    List<WatchUnit> findByTitleIdIn(@Param("titleIds") Collection<Long> titleIds);
}
