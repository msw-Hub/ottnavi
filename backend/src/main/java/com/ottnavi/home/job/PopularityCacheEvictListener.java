package com.ottnavi.home.job;

import com.ottnavi.home.service.HomeCacheEvictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 인기 수집 Job이 끝나면(완료·실패·중단 모두) 인기 순위 캐시(homePopularity)를 비운다(Task 107).
 * 날짜별 인기 순위 기록이 새로 쌓이거나 오래된 날짜가 지워졌으므로, 캐시에 남은 옛 순위(특히 "기록 없음")를 오래 보여 주지 않기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularityCacheEvictListener implements JobExecutionListener {

	private final HomeCacheEvictor homeCacheEvictor;

	@Override
	public void afterJob(JobExecution jobExecution) {
		homeCacheEvictor.evictPopularity();
		log.info("인기 수집이 끝나 인기 순위 캐시를 비웠다: executionId={}", jobExecution.getId());
	}
}
