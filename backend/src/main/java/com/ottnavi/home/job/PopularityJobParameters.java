package com.ottnavi.home.job;

import java.time.Instant;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * 인기 수집 Job 파라미터를 만들고 검사한다(Task 107).
 * requestedAt은 식별 파라미터라 실행마다 새 Job 인스턴스가 된다(같은 날 몇 번이든 다시 돌 수 있고, 저장은 날짜 단위로 덮어쓰기라 멱등이다).
 * 시각은 Instant의 ISO-8601 문자열로 담는다(정리·재계산 Job과 같다).
 */
public final class PopularityJobParameters implements JobParametersValidator {

	public static final String REQUESTED_AT = "requestedAt"; // 요청 시각(ISO-8601 문자열)

	/** 인기 수집을 시작할 파라미터를 만든다 */
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
