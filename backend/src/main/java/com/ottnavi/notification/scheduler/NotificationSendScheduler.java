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
 * lockAtMostFor(4분)는 점유 임대(5분)보다 짧아야 한다. SMTP 타임아웃은 응답마다 5초 제한이라 한 통의 상한이 아니고,
 * 정상 SMTP 기준 한 통은 수 초다. 배치 크기와 lockAtMostFor는 이 가정이 느슨하다는 점을 감안해 잡은 값이다.
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
