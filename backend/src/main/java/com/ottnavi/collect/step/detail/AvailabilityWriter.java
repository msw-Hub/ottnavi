package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.service.AvailabilityCollectService;
import com.ottnavi.catalog.service.model.UnitProviders;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/** 판정 Step의 Writer: chunk 단위로 판정해 제공 상태와 변경 이력을 한 트랜잭션에 저장한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityWriter implements ItemWriter<UnitProviders>, StepExecutionListener {

    private final AvailabilityCollectService availabilityCollectService;

    private Long jobExecutionId; // 이력(availability_change.job_execution_id)에 남길 실행 ID. Step 시작 때 정한다

    @Override
    public void beforeStep(StepExecution stepExecution) {
        jobExecutionId = stepExecution.getJobExecutionId();
    }

    @Override
    public void write(Chunk<? extends UnitProviders> chunk) {
        List<UnitProviders> items = List.copyOf(chunk.getItems());
        int changed = availabilityCollectService.apply(items, jobExecutionId, Instant.now());
        log.debug("제공 상태 저장: 단위 {}건 중 상태가 바뀐(새로 생긴) 서비스 {}건", items.size(), changed);
    }
}
