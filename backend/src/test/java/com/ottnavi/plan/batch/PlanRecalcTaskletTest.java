package com.ottnavi.plan.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.plan.service.PlanRecalcService;
import com.ottnavi.plan.service.PlanRecalcTargetService;
import com.ottnavi.plan.service.model.PlanRecalcOutcome;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * 재계산 Tasklet이 플랜 하나의 예외로 멈추지 않는지 확인한다(DB 없음).
 * 막으려는 사고: 한 사용자의 실패(예외)가 나머지 사용자의 갱신을 막는 일, 대상 수가 READ_COUNT에 남지 않는 일.
 */
class PlanRecalcTaskletTest {

	@Test
	void 한_플랜이_예외로_실패해도_나머지_플랜은_재계산하고_대상_수를_센다() throws Exception {
		// given: 대상 3건 중 두 번째가 예외를 던진다. 수동 실행이라 수집 완료 대기는 하지 않는다
		PlanRecalcTargetService targetService = mock(PlanRecalcTargetService.class);
		PlanRecalcService recalcService = mock(PlanRecalcService.class);
		PlanRecalcExecutionQuery executionQuery = mock(PlanRecalcExecutionQuery.class);
		when(targetService.findTargetPlanIds()).thenReturn(List.of(1L, 2L, 3L));
		when(recalcService.recalcOne(1L)).thenReturn(PlanRecalcOutcome.UPDATED);
		when(recalcService.recalcOne(2L)).thenThrow(new IllegalStateException("테스트용 실패"));
		when(recalcService.recalcOne(3L)).thenReturn(PlanRecalcOutcome.SKIPPED);
		PlanRecalcTasklet tasklet = new PlanRecalcTasklet(targetService, recalcService, executionQuery);

		JobParameters parameters = PlanRecalcJobParameters.of(PlanRecalcJobParameters.NO_COLLECT_EXECUTION,
				PlanRecalcJobParameters.TRIGGER_MANUAL, Instant.now());
		StepExecution stepExecution = new StepExecution(1L, "planRecalcStep",
				new JobExecution(1L, new JobInstance(1L, "planRecalcJob"), parameters));
		StepContribution contribution = new StepContribution(stepExecution);

		// when
		RepeatStatus status = tasklet.execute(contribution, null);

		// then: 세 플랜 모두 시도했고 대상 수 3이 READ_COUNT에 남으며 Step은 정상 종료한다
		assertThat(status).isEqualTo(RepeatStatus.FINISHED);
		assertThat(contribution.getReadCount()).isEqualTo(3);
		verify(recalcService).recalcOne(1L);
		verify(recalcService).recalcOne(2L);
		verify(recalcService).recalcOne(3L);
		verify(executionQuery, never()).isCollectCompleted(anyLong());
	}
}
