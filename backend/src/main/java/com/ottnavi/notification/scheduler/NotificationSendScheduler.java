package com.ottnavi.notification.scheduler;

import com.ottnavi.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기 중인 알림을 주기적으로 발송하도록 깨우는 스케줄러. 일은 NotificationDispatcher가 한다.
 * 이전 실행이 끝난 뒤부터 간격을 센다(fixedDelay). 서버가 여러 대여도 ShedLock으로 한 곳에서만 돈다.
 * lockAtMostFor(4분)는 배치 크기 × 15초(SMTP 타임아웃 합)보다 길고 점유 임대(5분)보다 짧아야 한다.
 * lockAtLeastFor(30초)는 서버 둘이 거의 동시에 깨어도 한 번만 돌게 하는 간격이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("app.notification.scheduler-enabled")
public class NotificationSendScheduler {

	private final NotificationDispatcher dispatcher;

	@Scheduled(fixedDelayString = "${app.notification.poll-interval-ms:60000}")
	@SchedulerLock(name = "notificationSend", lockAtMostFor = "4m", lockAtLeastFor = "30s")
	public void sendDueNotifications() {
		dispatcher.dispatchDue();
	}
}
