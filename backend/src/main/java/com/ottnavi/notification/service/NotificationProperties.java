package com.ottnavi.notification.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 알림 발송기 설정(app.notification.*). 값은 application.yml에 있고, 값끼리의 관계는 거기에 적혀 있다.
 *
 * @param schedulerEnabled 발송 스케줄러 사용 여부. 테스트는 끈다
 * @param pollIntervalMs   스케줄러 실행 간격(ms)
 * @param batchSize        한 번에 점유하는 행 수
 * @param lease            점유한 행을 다시 집지 않는 시간
 * @param maxAttempts      점유 횟수 상한. 이 횟수째 실패하면 FAILED
 * @param retryDelays      n번째 실패 뒤 다음 시도까지의 간격
 */
@Validated
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
		boolean schedulerEnabled,
		long pollIntervalMs,
		@Min(value = 1, message = "batch-size는 1 이상이어야 한다") int batchSize,
		@NotNull Duration lease,
		@Min(value = 1, message = "max-attempts는 1 이상이어야 한다") int maxAttempts,
		@NotNull List<Duration> retryDelays) {

	/** 재시도 간격은 (최대 시도 - 1)개 이상이어야 한다. 마지막 시도가 실패하면 간격 없이 FAILED가 되기 때문이다 */
	@AssertTrue(message = "retry-delays 개수는 max-attempts - 1 이상이어야 한다")
	public boolean isRetryDelaysEnough() {
		if (retryDelays == null) {
			return false;
		}
		return retryDelays.size() >= maxAttempts - 1;
	}
}
