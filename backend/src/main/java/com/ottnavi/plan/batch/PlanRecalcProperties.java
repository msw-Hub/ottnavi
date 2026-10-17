package com.ottnavi.plan.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 매월 재계산 설정(app.plan-recalc.*). 값은 application.yml에 있다.
 *
 * @param autoStart 수집이 COMPLETED로 끝나면 재계산을 자동으로 시작할지. 테스트 설정에서는 false다
 */
@ConfigurationProperties(prefix = "app.plan-recalc")
public record PlanRecalcProperties(boolean autoStart) {
}
