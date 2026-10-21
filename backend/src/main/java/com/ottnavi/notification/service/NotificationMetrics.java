package com.ottnavi.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 알림 발송 결과 지표(notification.send). result 태그: sent / retry / failed / skipped */
@Component
@RequiredArgsConstructor
public class NotificationMetrics {

	public static final String SENT = "sent";       // 발송 성공
	public static final String RETRY = "retry";     // 실패했지만 다시 시도 예정
	public static final String FAILED = "failed";   // 최대 시도 도달로 포기
	public static final String SKIPPED = "skipped"; // 행이나 수신자가 사라져 보내지 않음

	private final MeterRegistry meterRegistry;

	public void count(String result) {
		meterRegistry.counter("notification.send", "result", result).increment();
	}
}
