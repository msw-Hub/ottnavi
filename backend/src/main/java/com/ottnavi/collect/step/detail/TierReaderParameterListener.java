package com.ottnavi.collect.step.detail;

import com.ottnavi.collect.job.CollectJobParameters;
import com.ottnavi.collect.job.CollectTier;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;

/**
 * Step이 시작할 때 Job 파라미터 tier를 읽어 Reader 쿼리의 대상 범위(:refreshTiers)를 정한다(Task 077).
 * Reader는 싱글톤 빈이라 빌드 시점에는 tier를 모른다. 그래서 @StepScope 대신 beforeStep에서 파라미터를 채운다.
 * 예: tier=DAILY → refreshTiers=[DAILY], tier=ALL → [DAILY, WEEKLY](전체와 같다).
 */
public class TierReaderParameterListener implements StepExecutionListener {

    public static final String REFRESH_TIERS = "refreshTiers"; // 쿼리의 :refreshTiers 파라미터 이름

    private final JpaPagingItemReader<?> reader;
    private final Map<String, Object> fixedParameters; // tier와 무관하게 늘 같은 파라미터(예: mediaType)

    public TierReaderParameterListener(JpaPagingItemReader<?> reader, Map<String, Object> fixedParameters) {
        this.reader = reader;
        this.fixedParameters = fixedParameters;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // 1. 고정 파라미터를 복사해 새 맵을 만든다
        Map<String, Object> parameters = new HashMap<>(fixedParameters);

        // 2. 이 실행의 tier가 읽을 refresh_tier 목록을 넣는다
        CollectTier tier = CollectJobParameters.tierOf(stepExecution.getJobParameters());
        parameters.put(REFRESH_TIERS, tier.targetRefreshTiers());

        // 3. Reader는 첫 페이지를 읽을 때 이 맵으로 쿼리를 만든다
        reader.setParameterValues(parameters);
    }
}
