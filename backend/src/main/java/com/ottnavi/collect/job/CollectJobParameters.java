package com.ottnavi.collect.job;

import java.time.LocalDate;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/** 수집 Job 파라미터(targetDate + tier + 선택 scope)를 만들고 검사한다. 값이 모두 같으면 같은 실행으로 본다 */
public final class CollectJobParameters implements JobParametersValidator {

    public static final String TARGET_DATE = "targetDate"; // 수집 기준일
    public static final String TIER = "tier"; // 수집 등급
    public static final String SCOPE = "scope"; // 일부 Step만 실행하는 범위. 전체 실행에는 이 파라미터가 없다

    /** 전체 수집을 시작할 때 넘길 파라미터를 만든다. 두 값 모두 식별 파라미터다 */
    public static JobParameters of(LocalDate targetDate, CollectTier tier) {
        return of(targetDate, tier, null);
    }

    /**
     * 수집 Job을 시작할 때 넘길 파라미터를 만든다.
     * scope는 값이 있을 때만 식별 파라미터로 넣는다. 전체 실행의 파라미터는 예전과 같아서 이미 쌓인 실행 이력이 그대로 유효하고,
     * scope가 있는 실행은 같은 날짜의 전체 실행과 다른 인스턴스라 "이미 완료"로 막히지 않는다.
     */
    public static JobParameters of(LocalDate targetDate, CollectTier tier, CollectScope scope) {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLocalDate(TARGET_DATE, targetDate)
                .addString(TIER, tier.name());
        if (scope != null) {
            builder.addString(SCOPE, scope.name());
        }
        return builder.toJobParameters();
    }

    /** 이 실행의 범위를 읽는다. 파라미터가 없으면(전체 실행) null이다 */
    public static CollectScope scopeOf(JobParameters parameters) {
        String scope = parameters.getString(SCOPE);
        if (scope == null) {
            return null;
        }
        return CollectScope.valueOf(scope);
    }

    /** 이 실행의 tier를 읽는다. 값이 없거나 알 수 없으면 IllegalArgumentException(또는 NullPointerException)이다 */
    public static CollectTier tierOf(JobParameters parameters) {
        return CollectTier.valueOf(parameters.getString(TIER));
    }

    /** targetDate가 있는지, tier가 DAILY/WEEKLY/ALL 중 하나인지, scope가 있으면 알려진 값인지 검사한다 */
    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null || parameters.getLocalDate(TARGET_DATE) == null) {
            throw new InvalidJobParametersException("targetDate(LocalDate) 파라미터가 필요하다");
        }
        String tier = parameters.getString(TIER);
        try {
            CollectTier.valueOf(tier);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidJobParametersException("알 수 없는 tier다(DAILY, WEEKLY, ALL 중 하나여야 한다): " + tier);
        }
        try {
            scopeOf(parameters); // 알 수 없는 값이면 valueOf가 IllegalArgumentException을 던진다
        } catch (IllegalArgumentException e) {
            throw new InvalidJobParametersException("알 수 없는 scope다: " + parameters.getString(SCOPE));
        }
    }
}
