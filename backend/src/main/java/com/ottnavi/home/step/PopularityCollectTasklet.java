package com.ottnavi.home.step;

import com.ottnavi.home.service.PopularityCollectService;
import com.ottnavi.home.service.model.PopularityCollectSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * 인기 수집 Step 1: 서비스 7곳 × 영화·드라마의 인기순 상위 작품을 TMDB에서 받아 오늘 스냅샷으로 저장한다(Task 107).
 * 쓴 수는 저장한 스냅샷 행 수다. 일부 자리만 실패하면 건너뛰고 끝내지만, 모든 호출이 실패하면 Step을 실패로 만든다.
 */
@Component
@RequiredArgsConstructor
public class PopularityCollectTasklet implements Tasklet {

	private final PopularityCollectService popularityCollectService;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		// 1. 스냅샷을 받아 저장한다
		PopularityCollectSummary summary = popularityCollectService.collect();
		contribution.incrementWriteCount(summary.savedRows());

		// 2. 호출이 전부 실패했으면(토큰 오류·TMDB 장애 등) 실행 기록이 성공으로 남지 않게 실패시킨다
		if (summary.slots() > 0 && summary.failedSlots() == summary.slots()) {
			throw new IllegalStateException("인기 순위 호출이 모두 실패했다: 대상=" + summary.slots() + "곳");
		}
		return RepeatStatus.FINISHED;
	}
}
