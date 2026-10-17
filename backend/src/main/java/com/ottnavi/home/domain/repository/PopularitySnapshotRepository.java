package com.ottnavi.home.domain.repository;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.home.domain.entity.PopularitySnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopularitySnapshotRepository extends JpaRepository<PopularitySnapshot, Long> {

	/** 한 날짜·서비스·매체의 스냅샷을 벌크로 지운다. 같은 날 재실행이 다시 넣기 전에 먼저 비운다 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PopularitySnapshot s where s.snapshotDate = :snapshotDate "
			+ "and s.ottService.id = :ottServiceId and s.mediaType = :mediaType")
	int deleteBySlot(@Param("snapshotDate") LocalDate snapshotDate, @Param("ottServiceId") Long ottServiceId,
			@Param("mediaType") MediaType mediaType);

	/** 기준 날짜보다 이른 스냅샷을 벌크로 지운다. 보관 기간이 지난 날짜 정리에 쓴다 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PopularitySnapshot s where s.snapshotDate < :cutoffDate")
	int deleteBySnapshotDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

	/** 한 날짜의 스냅샷 행 수 */
	long countBySnapshotDate(LocalDate snapshotDate);

	/** 가장 최근 스냅샷 날짜. 스냅샷이 하나도 없으면 null */
	@Query("select max(s.snapshotDate) from PopularitySnapshot s")
	LocalDate findLatestSnapshotDate();

	/** 날짜 범위에서 스냅샷이 있는 날짜들(중복 없이). 평균의 분모(스냅샷이 있는 날 수)를 센다 */
	@Query("select distinct s.snapshotDate from PopularitySnapshot s where s.snapshotDate between :from and :to")
	List<LocalDate> findSnapshotDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 날짜 범위의 모든 서비스 스냅샷 줄. 서비스 엔티티는 읽지 않는다(서비스 ID로 거르는 쪽은 아래 메서드) */
	@Query("select s from PopularitySnapshot s where s.snapshotDate between :from and :to")
	List<PopularitySnapshot> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 날짜 범위에서 한 서비스의 스냅샷 줄 */
	@Query("select s from PopularitySnapshot s where s.snapshotDate between :from and :to and s.ottService.id = :ottServiceId")
	List<PopularitySnapshot> findBetweenForService(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("ottServiceId") Long ottServiceId);
}
