package com.ottnavi.retention.step;

import com.ottnavi.retention.service.RetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/** 정리 Step 3: 보관 기간이 지난, 플랜과 연결이 끊긴 계산 기록(calc_run)을 지운다(Task 083). 쓴 수는 지운 행 수다 */
@Component
@RequiredArgsConstructor
public class CalcRunPurgeTasklet implements Tasklet {

	private final RetentionService retentionService;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		contribution.incrementWriteCount(retentionService.purgeCalcRuns());
		return RepeatStatus.FINISHED;
	}
}
