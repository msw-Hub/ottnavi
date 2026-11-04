package com.ottnavi.collect.step;

import com.ottnavi.collect.job.CollectJobParameters;
import com.ottnavi.collect.job.CollectProgressRegistry;
import com.ottnavi.collect.job.CollectProgressRegistry.LiveStepProgress;
import com.ottnavi.collect.job.CollectProgressRules;
import com.ottnavi.collect.job.CollectStepCode;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.collect.step.candidate.CandidateReader;
import com.ottnavi.collect.step.detail.TierReaderParameterListener;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;

/**
 * 수집 Step 하나의 진행을 세어 로그와 진행 표시(CollectProgressRegistry)에 넘긴다.
 *
 * 상세·판정 단계(forTargets): 시작 때 Reader와 같은 조건으로 전체 대상 수를 세어 "전체 대상 N건"을 남기고,
 * 처리 퍼센트가 5%p 오를 때마다 "[진행] 영화 상세 처리 600/2400건 (25%)" 한 줄, 끝날 때(실패·중단 포함) 최종 한 줄을 남긴다.
 * 재시작한 단계는 Reader가 저장된 위치부터 이어 읽으므로 그 위치를 처리 수에 더한다.
 * 후보 단계(forCandidates): 로그는 CandidateReader의 "discover 끝 (서비스 n/7곳)"이 맡고, 여기서는 서비스·쪽 진행만 넘긴다.
 *
 * Step 빈마다 인스턴스가 하나이고 수집은 한 번에 하나만 돌아(전체 동시 실행 금지) 상태를 필드에 둔다.
 * 진행 표시 때문에 수집이 멈추면 안 되므로 전체 수를 세다 실패하면 경고만 남기고 전체 수를 비운다.
 */
@Slf4j
public class StepProgressListener implements StepExecutionListener, ChunkListener<Object, Object> {

    private static final String READ_COUNT_KEY = "read.count"; // JpaPagingItemReader가 읽은 위치를 저장하는 키(이름 접두사 앞)
    private static final int LOG_STEP_PERCENT = 5; // 진행 로그 간격(%p)

    private final CollectStepCode stepCode;
    private final String label; // 로그용 단계 이름
    private final CollectProgressRegistry registry;

    // 상세·판정 단계에서만 쓴다
    private final JpaPagingItemReader<?> targetReader;
    private final String countQuery; // Reader와 같은 from·where의 count JPQL
    private final Map<String, Object> fixedParameters; // Reader와 같은 고정 파라미터(예: mediaType)
    private final EntityManagerFactory entityManagerFactory;

    // 후보 단계에서만 쓴다
    private final CandidateReader candidateReader;

    // 실행마다 beforeStep에서 다시 채운다
    private long jobExecutionId;
    private int resumedCount; // 재시작해 이어 읽기 시작한 위치(처음 실행이면 0)
    private int readCount; // 이 Step 실행에서 읽은 수(청크 입력 크기 합)
    private int pendingChunkSize; // 처리 중인 청크의 입력 크기. 쓰기가 끝나면 readCount에 더한다
    private Integer totalCount; // 전체 대상 수. 세지 못했으면 null
    private int lastLoggedStep; // 마지막으로 로그를 남긴 퍼센트 구간(퍼센트 / 5)

    private StepProgressListener(CollectStepCode stepCode, String label, CollectProgressRegistry registry,
                                 JpaPagingItemReader<?> targetReader, String countQuery,
                                 Map<String, Object> fixedParameters, EntityManagerFactory entityManagerFactory,
                                 CandidateReader candidateReader) {
        this.stepCode = stepCode;
        this.label = label;
        this.registry = registry;
        this.targetReader = targetReader;
        this.countQuery = countQuery;
        this.fixedParameters = fixedParameters;
        this.entityManagerFactory = entityManagerFactory;
        this.candidateReader = candidateReader;
    }

    /**
     * 상세·판정 단계용 리스너를 만든다.
     *
     * @param countQuery      Reader 쿼리와 같은 from·where의 count JPQL(DetailStepConfig가 같은 조건 문자열로 만든다)
     * @param fixedParameters Reader에 넘기는 것과 같은 고정 파라미터. refreshTiers는 tier로 채운다
     */
    public static StepProgressListener forTargets(CollectStepCode stepCode, String label,
                                                  JpaPagingItemReader<?> reader, String countQuery,
                                                  Map<String, Object> fixedParameters,
                                                  EntityManagerFactory entityManagerFactory,
                                                  CollectProgressRegistry registry) {
        return new StepProgressListener(stepCode, label, registry, reader, countQuery, fixedParameters,
                entityManagerFactory, null);
    }

    /** 후보 단계용 리스너를 만든다. 서비스·쪽 진행은 CandidateReader에서 읽는다 */
    public static StepProgressListener forCandidates(CollectStepCode stepCode, CandidateReader reader,
                                                     CollectProgressRegistry registry) {
        return new StepProgressListener(stepCode, null, registry, null, null, null, null, reader);
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // 1. 이번 Step 실행의 상태를 비운다
        jobExecutionId = stepExecution.getJobExecutionId();
        resumedCount = 0;
        readCount = 0;
        pendingChunkSize = 0;
        totalCount = null;
        lastLoggedStep = 0;

        // 2. 후보 단계는 Reader가 아직 열리기 전이라 서비스 수를 모른다. 첫 청크 뒤부터 넘긴다
        if (candidateReader != null) {
            return;
        }

        // 3. 재시작이면 Reader가 이어 읽을 위치를 읽는다(재시작 때 Batch가 앞선 실행의 ExecutionContext를 복사해 둔다)
        String resumeKey = targetReader.getExecutionContextKey(READ_COUNT_KEY);
        resumedCount = stepExecution.getExecutionContext().getInt(resumeKey, 0);

        // 4. Reader와 같은 조건으로 전체 대상 수를 센다
        CollectTier tier = CollectJobParameters.tierOf(stepExecution.getJobParameters());
        totalCount = countTargets(tier);

        // 5. 시작 로그를 남기고 진행 표시에 첫 값을 넘긴다
        if (resumedCount > 0) {
            log.info("[진행] {} 시작: 전체 대상 {}건, 재시작이라 {}건 다음부터 이어 읽는다", label, totalCount, resumedCount);
        } else {
            log.info("[진행] {} 시작: 전체 대상 {}건", label, totalCount);
        }
        Integer startPercent = CollectProgressRules.percentOf(resumedCount, totalCount);
        if (startPercent != null) {
            lastLoggedStep = startPercent / LOG_STEP_PERCENT;
        }
        publish();
    }

    @Override
    public void beforeChunk(Chunk<Object> chunk) {
        pendingChunkSize = chunk.size(); // 읽은 입력 건수. 처리에서 걸러져도 읽은 것으로 센다
    }

    /** 청크 쓰기가 끝나면 처리 수를 올리고, 5%p 구간을 넘었으면 진행 로그를 남긴다 */
    @Override
    public void afterChunk(Chunk<Object> chunk) {
        // 1. 처리 수를 올리고 진행 표시에 넘긴다
        // Batch 6의 Step은 afterChunk를 StepExecution 건수 반영(apply)보다 먼저 부르므로 getReadCount 대신 직접 센다
        readCount += pendingChunkSize;
        pendingChunkSize = 0;
        publish();

        // 2. 후보 단계는 진행 로그를 남기지 않는다(Reader가 서비스마다 남긴다)
        if (candidateReader != null) {
            return;
        }

        // 3. 퍼센트 구간이 올랐을 때만 한 줄 남긴다(5%마다, 로그 폭주 방지)
        Integer percent = CollectProgressRules.percentOf(processedCount(), totalCount);
        if (percent == null) {
            return;
        }
        int currentStep = percent / LOG_STEP_PERCENT;
        if (currentStep > lastLoggedStep) {
            lastLoggedStep = currentStep;
            log.info("[진행] {} 처리 {}/{}건 ({}%)", label, processedCount(), totalCount, percent);
        }
    }

    /** 끝나면(실패·중단 포함) 최종 한 줄을 남기고 진행 표시에서 지운다. 상태는 이미 DB에 저장된 뒤라 화면은 DB 기록으로 넘어간다 */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (candidateReader == null) {
            Integer percent = CollectProgressRules.percentOf(processedCount(), totalCount);
            log.info("[진행] {} 끝({}): 처리 {}/{}건 ({}%)", label, stepExecution.getStatus(), processedCount(),
                    totalCount, percent);
        }
        registry.remove(jobExecutionId, stepCode);
        return null; // 종료 상태는 바꾸지 않는다
    }

    /** 처음부터 센 처리 수(재시작 위치 + 이번 실행에서 읽은 수) */
    private int processedCount() {
        return resumedCount + readCount;
    }

    /** 지금 진행을 진행 표시에 넘긴다 */
    private void publish() {
        if (candidateReader != null) {
            registry.update(jobExecutionId, stepCode, new LiveStepProgress(readCount, null,
                    candidateReader.getCompletedServiceCount(), candidateReader.getServiceCount(),
                    candidateReader.getCurrentServicePageCount(), lastPageOrNull()));
            return;
        }
        registry.update(jobExecutionId, stepCode,
                new LiveStepProgress(processedCount(), totalCount, null, null, null, null));
    }

    private Integer lastPageOrNull() {
        int lastPage = candidateReader.getCurrentServiceLastPage();
        if (lastPage <= 0) {
            return null;
        }
        return lastPage;
    }

    /** Reader와 같은 조건(tier → refreshTiers)으로 전체 대상 수를 센다. 실패하면 null(수집은 계속한다) */
    private Integer countTargets(CollectTier tier) {
        Map<String, Object> parameters = TierReaderParameterListener.parametersOf(fixedParameters, tier);
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            Query query = entityManager.createQuery(countQuery);
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                query.setParameter(parameter.getKey(), parameter.getValue());
            }
            Number count = (Number) query.getSingleResult();
            return count.intValue();
        } catch (RuntimeException e) {
            log.warn("[진행] {} 전체 대상 수를 세지 못해 퍼센트 없이 진행한다: {}", label, e.getMessage());
            return null;
        } finally {
            entityManager.close();
        }
    }
}
