package com.ottnavi.home.job;

import com.ottnavi.home.service.HomeCacheEvictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 수집 Job이 끝나면(완료·실패·중단 모두) 홈·탐색 캐시 5개를 모두 비운다(Task 107).
 * 수집이 작품의 제공 상태를 바꾸므로 인기 순위·찜 많은 작품·새 작품·장르별 작품 카드에 옛 상태가 남지 않게 하기 위해서다.
 * 실패한 실행도 실패 시점까지는 작품 단위로 반영되어 있으므로 함께 비운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeCacheEvictListener implements JobExecutionListener {

	private final HomeCacheEvictor homeCacheEvictor;

	@Override
	public void afterJob(JobExecution jobExecution) {
		homeCacheEvictor.evictAll();
		log.info("수집이 끝나 홈 캐시를 비웠다: executionId={}", jobExecution.getId());
	}
}
