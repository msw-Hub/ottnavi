package com.ottnavi.collect.service;

import com.ottnavi.collect.job.CollectExecutionQuery;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집 데이터가 언제 기준인지 다른 도메인(플랜 등)에 알려 주는 창구. 수집 이력 조회 코드를 직접 쓰지 않게 한다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CollectDataVersionService {

	private final CollectExecutionQuery collectExecutionQuery;

	/** 데이터 기준 시각: 수집 Job 마지막 COMPLETED 실행의 종료 시각(TECH 2절). 완료된 수집이 한 번도 없으면 비어 있다 */
	public Optional<Instant> findDataAsOf() {
		return collectExecutionQuery.findLatestCompletedEndTime();
	}
}
