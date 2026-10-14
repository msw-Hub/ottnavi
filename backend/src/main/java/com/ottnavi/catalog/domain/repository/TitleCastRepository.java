package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.TitleCast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TitleCastRepository extends JpaRepository<TitleCast, Long>  {
    /** 작품의 출연진을 출연 순서대로 조회한다 */
    List<TitleCast> findByTitleIdOrderByCastOrder(Long titleId);
}
