package com.ottnavi.collect.job;

import java.time.LocalDate;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/** 수집 Job 파라미터(targetDate + tier)를 만들고 검사한다. 두 값이 같으면 같은 실행으로 본다 */
public final class CollectJobParameters implements JobParametersValidator {

    public static final String TARGET_DATE = "targetDate"; // 수집 기준일
    public static final String TIER = "tier"; // 수집 등급

    /** 수집 Job을 시작할 때 넘길 파라미터를 만든다. 두 값 모두 식별 파라미터다 */
    public static JobParameters of(LocalDate targetDate, CollectTier tier) {
        return new JobParametersBuilder()
                .addLocalDate(TARGET_DATE, targetDate)
                .addString(TIER, tier.name())
                .toJobParameters();
    }

    /** 두 파라미터가 있고 tier가 ALL인지 검사한다. DAILY·WEEKLY는 2단계(Task 077)에서 열 때까지 거부한다 */
    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null || parameters.getLocalDate(TARGET_DATE) == null) {
            throw new InvalidJobParametersException("targetDate(LocalDate) 파라미터가 필요하다");
        }
        String tier = parameters.getString(TIER);
        if (!CollectTier.ALL.name().equals(tier)) {
            throw new InvalidJobParametersException("MVP는 tier=ALL만 실행할 수 있다: " + tier);
        }
    }
}
