package com.ottnavi.retention.step;

import com.ottnavi.retention.service.RetentionService;
import com.ottnavi.retention.service.model.TitleRetentionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * 정리 Step 1: 마지막 수집이 오래된 작품을 TMDB에 다시 묻고 갱신하거나 삭제한다(Task 083).
 * 쓴 수는 갱신+삭제한 작품 수로 센다. 작품 단위 실패는 RetentionService 안에서 처리된다.
 */
@Component
@RequiredArgsConstructor
public class TitleRetentionTasklet implements Tasklet {

	private final RetentionService retentionService;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		TitleRetentionSummary summary = retentionService.retainTitles();
		contribution.incrementWriteCount(summary.refreshed() + summary.purged());
		return RepeatStatus.FINISHED;
	}
}
