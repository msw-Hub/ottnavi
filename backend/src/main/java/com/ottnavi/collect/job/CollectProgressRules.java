package com.ottnavi.collect.job;

import com.ottnavi.collect.job.CollectProgressRegistry.LiveStepProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.BatchStatus;

/**
 * 수집 실행의 단계별 진행을 계산하는 순수 규칙(퍼센트, 그 실행의 단계 목록, 단계 상태, 지금 단계).
 * DB·Spring 컨텍스트 없이 값만 넘겨 시험한다. 값을 모으는 일은 CollectExecutionQuery(DB)와 CollectProgressRegistry(메모리)가 한다.
 *
 * 예: tier=WEEKLY, 영화 후보·드라마 후보가 끝났고 영화 상세가 2400건 중 600건째 →
 *     totalStepCount=5, completedStepCount=2, currentStep=MOVIE_DETAIL, MOVIE_DETAIL percent=25
 */
public final class CollectProgressRules {

    private static final int FULL_PERCENT = 100;

    /**
     * Batch 메타데이터의 단계 기록 한 건.
     *
     * @param step               단계 코드
     * @param batchStatus        Step의 Batch 상태
     * @param readCount          그 Step 실행이 읽은 건수(BATCH_STEP_EXECUTION.READ_COUNT)
     * @param isOfThisExecution  이 실행이 만든 기록인지. false면 같은 인스턴스의 앞선 실행(재시작 전) 기록이다
     */
    public record StepRecord(CollectStepCode step, BatchStatus batchStatus, int readCount, boolean isOfThisExecution) {
    }

    /** 단계 하나의 진행(계약 CollectStepProgress와 같은 값) */
    public record StepProgress(CollectStepCode step, CollectStepStatus status, int processedCount, Integer totalCount,
                               Integer percent, Integer completedServiceCount, Integer totalServiceCount) {
    }

    /** 실행 하나의 진행(계약 CollectProgress와 같은 값) */
    public record Progress(int totalStepCount, int completedStepCount, CollectStepCode currentStep,
                           List<StepProgress> steps) {
    }

    private CollectProgressRules() {
    }

    /**
     * 그 실행이 거치는 단계 목록(순서대로).
     * 예: scope=AVAILABILITY_TV → [AVAILABILITY], tier=DAILY → [MOVIE_DETAIL, TV_DETAIL, AVAILABILITY], 그 밖 → 5단계 전부
     */
    public static List<CollectStepCode> plannedSteps(CollectTier tier, CollectScope scope) {
        List<CollectStepCode> steps = new ArrayList<>();
        if (scope == CollectScope.AVAILABILITY_TV) {
            steps.add(CollectStepCode.AVAILABILITY);
            return steps;
        }
        if (tier != CollectTier.DAILY) {
            steps.add(CollectStepCode.MOVIE_CANDIDATE);
            steps.add(CollectStepCode.TV_CANDIDATE);
        }
        steps.add(CollectStepCode.MOVIE_DETAIL);
        steps.add(CollectStepCode.TV_DETAIL);
        steps.add(CollectStepCode.AVAILABILITY);
        return steps;
    }

    /**
     * 처리 수와 전체 수로 퍼센트(0~100 정수, 버림)를 낸다. 전체를 모르면 null이다.
     * 예: 199/200 → 99, 200/200 → 100, 210/200(전체를 센 뒤 대상이 늘어난 경우) → 100, 0/0 → 100(할 일이 없음)
     */
    public static Integer percentOf(long processedCount, Integer totalCount) {
        if (totalCount == null) {
            return null;
        }
        if (totalCount <= 0) {
            return FULL_PERCENT;
        }
        long percent = processedCount * FULL_PERCENT / totalCount;
        if (percent > FULL_PERCENT) {
            return FULL_PERCENT;
        }
        if (percent < 0) {
            return 0;
        }
        return (int) percent;
    }

    /**
     * 후보 단계의 근사 퍼센트: 끝낸 서비스 몫 + 지금 서비스에서 읽은 쪽의 몫(버림).
     * 서비스마다 쪽 수가 달라 실제 남은 시간과 비례하지 않는다. 서비스 수를 모르면(0 이하) null이다.
     * 예: 7곳 중 2곳 끝, 지금 서비스 100쪽 중 50쪽 → (2 + 0.5) / 7 = 35%. 마지막 쪽을 아직 모르면 끝낸 서비스 몫만(2/7 = 28%)
     */
    public static Integer candidatePercentOf(int completedServiceCount, int totalServiceCount,
                                             int currentServicePageCount, Integer currentServiceLastPage) {
        // 1. 서비스 수를 모르면 퍼센트를 낼 수 없다
        if (totalServiceCount <= 0) {
            return null;
        }

        // 2. 지금 서비스의 마지막 쪽을 모르면 끝낸 서비스 몫만 센다
        if (currentServiceLastPage == null || currentServiceLastPage <= 0) {
            return percentOf((long) completedServiceCount * FULL_PERCENT, totalServiceCount * FULL_PERCENT);
        }

        // 3. 서비스 하나를 마지막 쪽 수만큼 나눈 칸으로 보고 끝낸 칸 수를 센다
        int pagesInCurrent = currentServicePageCount;
        if (pagesInCurrent > currentServiceLastPage) {
            pagesInCurrent = currentServiceLastPage;
        }
        long doneUnits = (long) completedServiceCount * currentServiceLastPage + pagesInCurrent;
        long totalUnits = (long) totalServiceCount * currentServiceLastPage;
        if (totalUnits > Integer.MAX_VALUE) {
            return percentOf(completedServiceCount, totalServiceCount); // 실제로는 7곳 × 500쪽이라 오지 않는다
        }
        return percentOf(doneUnits, (int) totalUnits);
    }

    /**
     * 단계 기록 하나를 단계 상태로 바꾼다.
     * 기록이 없으면 대기다. 앞선 실행의 기록은 완료만 이어받는다(재시작은 끝난 Step을 다시 돌지 않고, 끝나지 못한 Step은 다시 돈다).
     * 실행 중 Batch 상태는 실행이 RUNNING일 때만 진행 중이고, 아니면(서버가 꺼졌거나 복구됨) 중단됨이다.
     */
    public static CollectStepStatus stepStatusOf(StepRecord record, CollectExecutionStatus executionStatus) {
        // 1. 이 단계까지 오지 않았다
        if (record == null) {
            return CollectStepStatus.PENDING;
        }

        // 2. 앞선 실행의 기록: 완료만 이어받고 나머지는 이 실행이 다시 돌 단계다
        BatchStatus batchStatus = record.batchStatus();
        if (!record.isOfThisExecution()) {
            if (batchStatus == BatchStatus.COMPLETED) {
                return CollectStepStatus.COMPLETED;
            }
            return CollectStepStatus.PENDING;
        }

        // 3. 이 실행의 기록
        if (batchStatus == BatchStatus.COMPLETED) {
            return CollectStepStatus.COMPLETED;
        }
        if (batchStatus == BatchStatus.STARTING || batchStatus == BatchStatus.STARTED
                || batchStatus == BatchStatus.STOPPING) {
            if (executionStatus == CollectExecutionStatus.RUNNING) {
                return CollectStepStatus.RUNNING;
            }
            return CollectStepStatus.STOPPED;
        }
        return CollectStepStatus.FAILED;
    }

    /**
     * 실행 하나의 단계별 진행을 만든다.
     * 끝난 단계는 DB 기록(읽은 수 = 전체)만으로, 진행 중인 단계는 메모리의 실시간 값이 있으면 그것으로 만든다.
     *
     * @param records 단계별 가장 최근 기록(같은 인스턴스의 앞선 실행 포함). 없으면 빈 Map
     * @param live    단계별 실시간 진행. 실행 중이 아니면 빈 Map
     */
    public static Progress progressOf(CollectTier tier, CollectScope scope, CollectExecutionStatus executionStatus,
                                      Map<CollectStepCode, StepRecord> records,
                                      Map<CollectStepCode, LiveStepProgress> live) {
        // 1. 그 실행이 거치는 단계 목록
        List<CollectStepCode> planned = plannedSteps(tier, scope);

        // 2. 단계마다 진행을 만든다
        List<StepProgress> steps = new ArrayList<>();
        int completedStepCount = 0;
        CollectStepCode currentStep = null;
        for (CollectStepCode step : planned) {
            StepRecord record = records.get(step);
            CollectStepStatus status = stepStatusOf(record, executionStatus);
            StepProgress progress = stepProgressOf(step, status, record, live.get(step));
            steps.add(progress);

            // 3. 끝난 단계 수와 지금 단계(처음 만나는 진행 중 단계)를 센다
            if (status == CollectStepStatus.COMPLETED) {
                completedStepCount++;
            }
            if (status == CollectStepStatus.RUNNING && currentStep == null) {
                currentStep = step;
            }
        }
        return new Progress(planned.size(), completedStepCount, currentStep, steps);
    }

    /** 상태별로 처리 수·전체 수·퍼센트를 정한다 */
    private static StepProgress stepProgressOf(CollectStepCode step, CollectStepStatus status, StepRecord record,
                                               LiveStepProgress live) {
        // 1. 대기: 아직 읽은 것이 없다
        if (status == CollectStepStatus.PENDING) {
            return new StepProgress(step, status, 0, null, 0, null, null);
        }

        int readCount = 0;
        if (record != null) {
            readCount = record.readCount();
        }

        // 2. 완료: 읽은 수를 전체로 본다(끝난 실행은 대상 수를 다시 세지 않는다)
        if (status == CollectStepStatus.COMPLETED) {
            return new StepProgress(step, status, readCount, readCount, FULL_PERCENT, null, null);
        }

        // 3. 진행 중이고 실시간 값이 있으면 그것을 쓴다
        if (status == CollectStepStatus.RUNNING && live != null) {
            if (live.totalServiceCount() != null) {
                // 후보 단계: 전체 건수는 모르고 서비스·쪽 진행으로 근사 퍼센트를 낸다
                int currentPageCount = 0;
                if (live.currentServicePageCount() != null) {
                    currentPageCount = live.currentServicePageCount();
                }
                int completedServices = 0;
                if (live.completedServiceCount() != null) {
                    completedServices = live.completedServiceCount();
                }
                Integer percent = candidatePercentOf(completedServices, live.totalServiceCount(),
                        currentPageCount, live.currentServiceLastPage());
                return new StepProgress(step, status, live.processedCount(), null, percent,
                        live.completedServiceCount(), live.totalServiceCount());
            }
            Integer percent = percentOf(live.processedCount(), live.totalCount());
            return new StepProgress(step, status, live.processedCount(), live.totalCount(), percent, null, null);
        }

        // 4. 진행 중인데 실시간 값이 없거나, 실패·중단: 지금까지 읽은 수만 알고 전체는 모른다
        return new StepProgress(step, status, readCount, null, null, null, null);
    }
}
