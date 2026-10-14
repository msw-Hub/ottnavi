package com.ottnavi.catalog.domain.repository;

import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityChangeRepository extends JpaRepository<AvailabilityChange, Long> {

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
