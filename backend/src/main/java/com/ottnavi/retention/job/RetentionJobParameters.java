package com.ottnavi.retention.job;

import java.time.Instant;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * 정리 Job 파라미터를 만들고 검사한다(Task 083).
 * requestedAt은 식별 파라미터라 실행마다 새 Job 인스턴스가 된다(정리는 몇 번이든 다시 돌 수 있다).
 * 시각은 Instant의 ISO-8601 문자열로 담는다(재계산 Job과 같은 이유로 문자열이 안전하다).
 */
public final class RetentionJobParameters implements JobParametersValidator {

	public static final String REQUESTED_AT = "requestedAt"; // 요청 시각(ISO-8601 문자열)

	/** 정리를 시작할 파라미터를 만든다 */
	public static JobParameters of(Instant requestedAt) {
		return new JobParametersBuilder()
				.addString(REQUESTED_AT, requestedAt.toString(), true)
				.toJobParameters();
	}

	@Override
	public void validate(JobParameters parameters) throws InvalidJobParametersException {
		if (parameters == null || parameters.getString(REQUESTED_AT) == null) {
			throw new InvalidJobParametersException("requestedAt 파라미터가 필요하다");
		}
	}
}
