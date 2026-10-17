package com.ottnavi.home.step;

import com.ottnavi.home.service.PopularityCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/** 인기 수집 Step 2: 보관 기간(기본 14일)이 지난 스냅샷을 지운다(Task 107). 쓴 수는 지운 행 수다 */
@Component
@RequiredArgsConstructor
public class PopularityPurgeTasklet implements Tasklet {

	private final PopularityCollectService popularityCollectService;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		contribution.incrementWriteCount(popularityCollectService.purgeOld());
		return RepeatStatus.FINISHED;
	}
}
