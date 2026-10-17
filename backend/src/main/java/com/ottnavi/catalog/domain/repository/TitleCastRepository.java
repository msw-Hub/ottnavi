package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.TitleCast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TitleCastRepository extends JpaRepository<TitleCast, Long>  {
    /** 작품의 출연진을 벌크로 지운다. 작품 삭제 정리가 쓴다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TitleCast c where c.title.id = :titleId")
    int deleteByTitleId(@Param("titleId") Long titleId);

    /** 작품의 출연진을 출연 순서대로 조회한다 */
    List<TitleCast> findByTitleIdOrderByCastOrder(Long titleId);
}
