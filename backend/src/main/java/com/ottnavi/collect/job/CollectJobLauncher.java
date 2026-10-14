package com.ottnavi.collect.job;

import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.batch.core.ExitStatus;
import org.springframework.stereotype.Component;

/**
 * 수집 Job을 시작·재시작·복구하는 창구. 비동기 operator를 감싸 Batch 검사 예외를 한곳에서 처리한다.
 *
 * <p>이 클래스와 이를 호출하는 서비스에는 {@code @Transactional}을 붙이지 않는다.
 * JobRepository가 이미 열린 트랜잭션 안에서 실행 기록을 만드는 것을 거부하고,
 * 바깥 트랜잭션이 커밋되기 전에는 비동기 스레드가 방금 만든 실행 행을 읽지 못하기 때문이다.
 *
 * <p>호출하기 전에 {@link CollectExecutionRules}로 사전 검사를 마쳐야 한다. 사전 검사를 통과한 뒤 나는
 * Batch 검사 예외는 예상 밖이므로 IllegalStateException으로 감싸 올린다.
 */
@Component
public class CollectJobLauncher {

    public static final String RECOVERED_EXIT_CODE = "RECOVERED"; // 복구된 실행을 구분하는 종료 코드
    public static final String RECOVERED_REASON = "중단된 실행을 관리자가 실패로 복구함";

    private final JobOperator operator;
    private final Job collectJob;
    private final JobRepository jobRepository;

    public CollectJobLauncher(@Qualifier("collectJobOperator") JobOperator operator, Job collectJob,
                              JobRepository jobRepository) {
        this.operator = operator;
        this.collectJob = collectJob;
        this.jobRepository = jobRepository;
    }

    /** 수집을 비동기로 시작하고 새 실행의 ID를 돌려준다. 이 메서드가 돌아올 때 수집은 아직 끝나지 않았다 */
    public long start(LocalDate targetDate, CollectTier tier) {
        return launch(CollectJobParameters.of(targetDate, tier));
    }

    /**
     * 실패한 실행을 같은 파라미터로 다시 시작하고 새 실행의 ID를 돌려준다.
     * operator.restart(JobExecution)은 원인 예외를 뭉쳐 버려서, 이전 실행의 파라미터로 start를 다시 부른다.
     * 같은 파라미터면 Batch가 같은 인스턴스의 재시작으로 처리해 읽던 위치부터 이어간다.
     */
    public long restart(long latestExecutionId) {
        // 1. 이전 실행의 파라미터를 읽는다
        JobExecution previous = jobRepository.getJobExecution(latestExecutionId);
        if (previous == null) {
            throw new IllegalStateException("재시작할 실행을 찾지 못했다: " + latestExecutionId);
        }

        // 2. 같은 파라미터로 시작한다
        return launch(previous.getJobParameters());
    }

    /**
     * 중단된 실행을 FAILED로 바꾸고 종료 코드를 RECOVERED로 남긴다.
     * Batch의 recover는 복구 표시를 메모리 컨텍스트에만 넣고 종료 코드는 건드리지 않아서,
     * 화면이 복구된 실행을 구분할 수 있게 종료 코드를 직접 저장한다.
     */
    public void recover(long executionId) {
        // 1. Batch가 상태를 FAILED로 바꾼다(실행 중이던 Step도 함께)
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            throw new IllegalStateException("복구할 실행을 찾지 못했다: " + executionId);
        }
        JobExecution recovered = operator.recover(execution);

        // 2. 종료 코드를 RECOVERED로 저장한다. 버전 충돌이면 다시 읽어서 한 번 더 시도한다
        try {
            markRecovered(recovered);
        } catch (OptimisticLockingFailureException e) {
            markRecovered(jobRepository.getJobExecution(executionId));
        }
    }

    private void markRecovered(JobExecution execution) {
        execution.setExitStatus(new ExitStatus(RECOVERED_EXIT_CODE, RECOVERED_REASON));
        jobRepository.update(execution);
    }

    private long launch(JobParameters parameters) {
        try {
            JobExecution execution = operator.start(collectJob, parameters);
            return execution.getId();
        } catch (Exception e) {
            throw new IllegalStateException("수집 Job을 시작하지 못했다: " + e.getMessage(), e);
        }
    }
}
