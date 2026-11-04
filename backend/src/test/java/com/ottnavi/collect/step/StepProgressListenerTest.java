package com.ottnavi.collect.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.CollectJobParameters;
import com.ottnavi.collect.job.CollectProgressRegistry;
import com.ottnavi.collect.job.CollectProgressRegistry.LiveStepProgress;
import com.ottnavi.collect.job.CollectProgressRules;
import com.ottnavi.collect.job.CollectProgressRules.StepProgress;
import com.ottnavi.collect.job.CollectProgressRules.StepRecord;
import com.ottnavi.collect.job.CollectStepCode;
import com.ottnavi.collect.job.CollectStepStatus;
import com.ottnavi.collect.job.CollectTier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;

/** 상세·판정 Step 진행 리스너가 전체 대상 수를 세지 못했을 때의 대체 동작 단위 테스트(DB 없이 가짜 EntityManager로) */
class StepProgressListenerTest {

    private static final long JOB_EXECUTION_ID = 7L;

    private final CollectProgressRegistry registry = new CollectProgressRegistry();
    private final EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    @Test
    void 전체_대상_수를_세지_못하면_수집을_멈추지_않고_전체와_퍼센트_없이_처리_수만_넘긴다() {
        // given: count 쿼리가 예외를 던지고, 재시작이라 앞선 실행이 10건까지 읽어 둔 Step
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.createQuery(anyString())).thenThrow(new IllegalArgumentException("가짜 count 실패"));
        JpaPagingItemReader<Object> reader = new JpaPagingItemReader<>(entityManagerFactory);
        reader.setName("movieTitleReader");
        StepProgressListener listener = StepProgressListener.forTargets(CollectStepCode.MOVIE_DETAIL, "영화 상세",
                reader, "select count(t) from Title t", Map.of(), entityManagerFactory, registry);
        StepExecution stepExecution = stepExecution();
        stepExecution.getExecutionContext().putInt(reader.getExecutionContextKey("read.count"), 10);

        // when: Step 시작과 청크 하나(5건)를 거친다. 예외가 밖으로 나오면 이 구역에서 테스트가 실패한다
        listener.beforeStep(stepExecution);
        listener.beforeChunk(chunkOf(5));
        listener.afterChunk(chunkOf(5));

        // then: 처리 수는 재시작 위치를 더한 15건, 전체는 모름(null)이고 화면 진행도 퍼센트 없이 처리 수만 보인다
        LiveStepProgress live = registry.find(JOB_EXECUTION_ID).get(CollectStepCode.MOVIE_DETAIL);
        assertThat(live.processedCount()).isEqualTo(15);
        assertThat(live.totalCount()).isNull();
        Map<CollectStepCode, StepRecord> records = new HashMap<>();
        records.put(CollectStepCode.MOVIE_DETAIL,
                new StepRecord(CollectStepCode.MOVIE_DETAIL, BatchStatus.STARTED, 5, true));
        StepProgress shown = stepOf(CollectProgressRules.progressOf(CollectTier.DAILY, null,
                CollectExecutionStatus.RUNNING, records, registry.find(JOB_EXECUTION_ID)), CollectStepCode.MOVIE_DETAIL);
        assertThat(shown.status()).isEqualTo(CollectStepStatus.RUNNING);
        assertThat(shown.processedCount()).isEqualTo(15);
        assertThat(shown.totalCount()).isNull();
        assertThat(shown.percent()).isNull();
        verify(entityManager).close(); // 실패해도 EntityManager를 닫는다
    }

    // 도우미

    private static StepExecution stepExecution() {
        JobInstance jobInstance = new JobInstance(1L, "collectJob");
        JobExecution jobExecution = new JobExecution(JOB_EXECUTION_ID, jobInstance,
                CollectJobParameters.of(LocalDate.of(2026, 11, 5), CollectTier.DAILY));
        return new StepExecution(1L, "movieDetailStep", jobExecution);
    }

    private static Chunk<Object> chunkOf(int size) {
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            items.add("작품 " + i);
        }
        return new Chunk<>(items);
    }

    private static StepProgress stepOf(CollectProgressRules.Progress progress, CollectStepCode code) {
        for (StepProgress step : progress.steps()) {
            if (step.step() == code) {
                return step;
            }
        }
        throw new AssertionError("단계가 없다: " + code);
    }
}
