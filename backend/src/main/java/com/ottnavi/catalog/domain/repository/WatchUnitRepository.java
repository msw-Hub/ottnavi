package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.WatchUnit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchUnitRepository extends JpaRepository<WatchUnit, Long> {
    /** 작품 하나의 시청 단위 전부를 조회한다. 수집이 이미 있는 단위를 갱신할지 새로 만들지 가를 때 쓴다 */
    List<WatchUnit> findByTitleId(Long titleId);
}
