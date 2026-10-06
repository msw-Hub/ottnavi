package com.ottnavi.provider.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OttServiceRepository extends JpaRepository<OttService, Long> {

	/** 서비스 전체를 화면 정렬 순서대로 조회한다 */
	List<OttService> findAllByOrderByDisplayOrderAsc();
}
