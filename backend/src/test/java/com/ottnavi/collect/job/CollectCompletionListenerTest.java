package com.ottnavi.collect.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 수집 Job이 COMPLETED로 끝났을 때만 완료 이벤트를 발행하는지 확인한다(DB 없음).
 * 막으려는 사고: 실패·중단된 수집 뒤에도 재계산이 시작되는 일.
 */
class CollectCompletionListenerTest {

    @Test
    void COMPLETED일_때만_이벤트를_발행하고_FAILED면_발행하지_않는다() {
        // given
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        CollectCompletionListener listener = new CollectCompletionListener(publisher);
        JobExecution completed = new JobExecution(7L, new JobInstance(1L, "collectJob"), new JobParameters());
        completed.setStatus(BatchStatus.COMPLETED);
        JobExecution failed = new JobExecution(8L, new JobInstance(2L, "collectJob"), new JobParameters());
        failed.setStatus(BatchStatus.FAILED);

        // when
        listener.afterJob(failed);
        listener.afterJob(completed);

        // then: 완료된 7번 실행만 이벤트가 나갔다
        verify(publisher).publishEvent(new CollectCompletedEvent(7L));
        verify(publisher, never()).publishEvent(new CollectCompletedEvent(8L));
    }

    @Test
    void scope가_AVAILABILITY_TV인_수집은_COMPLETED여도_이벤트를_발행하지_않는다() {
        // given: 일부 범위(시즌 제공 상태 판정만) 수집이 완료됐다
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        CollectCompletionListener listener = new CollectCompletionListener(publisher);
        JobExecution partial = new JobExecution(9L, new JobInstance(3L, "collectJob"),
                CollectJobParameters.of(LocalDate.of(2026, 3, 10), CollectTier.ALL, CollectScope.AVAILABILITY_TV));
        partial.setStatus(BatchStatus.COMPLETED);

        // when
        listener.afterJob(partial);

        // then
        verify(publisher, never()).publishEvent(new CollectCompletedEvent(9L));
    }
}
