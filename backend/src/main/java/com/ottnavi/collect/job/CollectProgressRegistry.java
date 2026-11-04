package com.ottnavi.collect.job;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 지금 이 서버에서 도는 단계의 실시간 진행(전체 대상 수, 이어 읽기 위치 포함 처리 수, 후보 단계의 서비스·쪽 진행)을 메모리에 둔다.
 * Step 리스너(StepProgressListener)가 청크마다 쓰고, 이력 조회가 실행 중인 실행에만 읽는다.
 * DB에 저장하지 않는 이유: 실행 중(RUNNING) 판정 자체가 "이 서버가 기동한 뒤 만든 실행"이라 값이 필요한 실행은 늘 이 서버 메모리에 있다.
 * 서버가 다시 뜨면 비지만 그때 그 실행은 중단됨(STOPPED)으로 보여 실시간 값이 필요 없다. 끝난 실행은 Batch 메타데이터만으로 만든다.
 */
@Component
public class CollectProgressRegistry {

    /**
     * 단계 하나의 실시간 진행.
     *
     * @param processedCount         처음부터 센 처리 수. 재시작한 상세·판정 단계는 앞선 실행이 읽은 위치를 더한다
     * @param totalCount             전체 대상 수. 모르면 null(후보 단계는 늘 null)
     * @param completedServiceCount  후보 단계: discover를 끝낸 서비스 수. 다른 단계는 null
     * @param totalServiceCount      후보 단계: discover할 서비스 수
     * @param currentServicePageCount 후보 단계: 지금 서비스에서 읽은 쪽 수
     * @param currentServiceLastPage 후보 단계: 지금 서비스의 마지막 쪽(첫 쪽을 받기 전이면 null)
     */
    public record LiveStepProgress(int processedCount, Integer totalCount, Integer completedServiceCount,
                                   Integer totalServiceCount, Integer currentServicePageCount,
                                   Integer currentServiceLastPage) {
    }

    // 실행 ID → (단계 → 진행). 수집은 한 번에 하나만 돌아 크기는 많아야 단계 몇 개다
    private final Map<Long, Map<CollectStepCode, LiveStepProgress>> byExecution = new ConcurrentHashMap<>();

    /** 단계의 진행을 덮어쓴다 */
    public void update(long jobExecutionId, CollectStepCode step, LiveStepProgress progress) {
        Map<CollectStepCode, LiveStepProgress> steps = byExecution.get(jobExecutionId);
        if (steps == null) {
            steps = new ConcurrentHashMap<>();
            Map<CollectStepCode, LiveStepProgress> existing = byExecution.putIfAbsent(jobExecutionId, steps);
            if (existing != null) {
                steps = existing;
            }
        }
        steps.put(step, progress);
    }

    /** 단계가 끝나면 지운다. 그 뒤로는 DB의 단계 기록이 진행을 대신한다 */
    public void remove(long jobExecutionId, CollectStepCode step) {
        Map<CollectStepCode, LiveStepProgress> steps = byExecution.get(jobExecutionId);
        if (steps == null) {
            return;
        }
        steps.remove(step);
        if (steps.isEmpty()) {
            byExecution.remove(jobExecutionId);
        }
    }

    /** 실행 하나의 실시간 진행을 복사해 돌려준다. 없으면 빈 Map */
    public Map<CollectStepCode, LiveStepProgress> find(long jobExecutionId) {
        Map<CollectStepCode, LiveStepProgress> steps = byExecution.get(jobExecutionId);
        if (steps == null) {
            return new HashMap<>();
        }
        return new HashMap<>(steps);
    }
}
