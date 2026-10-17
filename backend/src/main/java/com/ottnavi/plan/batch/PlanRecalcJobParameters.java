package com.ottnavi.plan.batch;

import java.time.Instant;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * 재계산 Job 파라미터를 만들고 검사한다.
 * collectExecutionId와 requestedAt은 식별 파라미터라 실행마다 새 Job 인스턴스가 된다(재계산은 몇 번이든 다시 돌 수 있다).
 * trigger는 비식별이다. requestedAt은 Instant의 ISO-8601 문자열로 담는다(Batch가 Instant 파라미터를 다시 읽는 변환이 없어 문자열이 안전하다).
 */
public final class PlanRecalcJobParameters implements JobParametersValidator {

	public static final String COLLECT_EXECUTION_ID = "collectExecutionId"; // 이 재계산을 부른 수집 실행 ID. 수동이면 0
	public static final String TRIGGER = "trigger"; // COLLECT(수집 완료 뒤 자동) / MANUAL(관리자 수동)
	public static final String REQUESTED_AT = "requestedAt"; // 요청 시각(ISO-8601 문자열)

	public static final String TRIGGER_COLLECT = "COLLECT";
	public static final String TRIGGER_MANUAL = "MANUAL";
	public static final long NO_COLLECT_EXECUTION = 0L; // 수동 실행은 부른 수집이 없다

	/** 재계산을 시작할 파라미터를 만든다 */
	public static JobParameters of(long collectExecutionId, String trigger, Instant requestedAt) {
		return new JobParametersBuilder()
				.addLong(COLLECT_EXECUTION_ID, collectExecutionId, true)
				.addString(TRIGGER, trigger, false)
				.addString(REQUESTED_AT, requestedAt.toString(), true)
				.toJobParameters();
	}

	@Override
	public void validate(JobParameters parameters) throws InvalidJobParametersException {
		if (parameters == null || parameters.getLong(COLLECT_EXECUTION_ID) == null) {
			throw new InvalidJobParametersException("collectExecutionId(Long) 파라미터가 필요하다");
		}
		String trigger = parameters.getString(TRIGGER);
		if (!TRIGGER_COLLECT.equals(trigger) && !TRIGGER_MANUAL.equals(trigger)) {
			throw new InvalidJobParametersException("trigger는 COLLECT 또는 MANUAL이어야 한다: " + trigger);
		}
		if (parameters.getString(REQUESTED_AT) == null) {
			throw new InvalidJobParametersException("requestedAt 파라미터가 필요하다");
		}
	}
}
