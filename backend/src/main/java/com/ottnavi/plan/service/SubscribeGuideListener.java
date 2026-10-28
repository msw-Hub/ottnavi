package com.ottnavi.plan.service;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.NotificationCreateService;
import com.ottnavi.notification.service.NotificationDispatcher;
import com.ottnavi.notification.service.NotificationProperties;
import com.ottnavi.notification.service.model.SubscribeGuidePayload;
import com.ottnavi.user.service.UserQueryService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 플랜 저장이 커밋된 뒤 가입 안내(SUBSCRIBE_GUIDE) 알림을 만들고 바로 보낸다(Task 073, TECH 5절).
 * 저장 응답을 늦추지 않도록 별도 스레드(@Async)에서 돌고, 실패해도 이미 커밋된 저장에는 영향이 없다.
 * 이 메서드에는 @Transactional을 달지 않는다: 커밋이 끝난 뒤라 이 스레드에는 트랜잭션이 없고,
 * 행 생성(REQUIRES_NEW)과 점유·결과 기록(각각 REQUIRES_NEW)이 서비스 쪽에서 따로 커밋된다. 메일 발송은 트랜잭션 밖이다.
 * 행만 만들고 발송에 실패해도 PENDING이 남아 스케줄러가 재시도한다.
 *
 * <pre>
 * 저장 커밋 → (비동기) 이번 달 가입할 상품이 있으면 SUBSCRIBE_GUIDE:{userId}:{YYYY-MM} 행 생성 → 즉시 발송 시도
 * 가입할 상품이 없거나 알림이 꺼져 있거나 이번 달에 이미 만들었으면 아무것도 하지 않는다
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscribeGuideListener {

	private final PlanNotificationComposer composer;
	private final NotificationCreateService createService;
	private final NotificationDispatcher dispatcher;
	private final NotificationProperties properties;
	private final UserQueryService userQueryService;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPlanActivated(PlanActivatedEvent event) {
		try {
			// 1. 알림이 꺼져 있으면 플랜을 읽지 않고 끝낸다(생성 관문도 같은 확인을 하지만 조립 비용을 아낀다)
			if (!userQueryService.isNotificationEnabled(event.userId())) {
				return;
			}

			// 2. 저장된 플랜으로 메일 재료를 조립한다. 이번 달 가입할 상품이 없으면 만들지 않는다
			Optional<SubscribeGuidePayload> payload = composer.composeSubscribeGuide(event.userId(), event.planId());
			if (payload.isEmpty()) {
				log.info("가입 안내를 만들지 않는다(이번 달 가입할 상품 없음): userId={}, planId={}", event.userId(), event.planId());
				return;
			}

			// 3. 사용자·월마다 한 번만 만든다(같은 달에 다시 저장해도 중복되지 않는다)
			String dedupeKey = "SUBSCRIBE_GUIDE:" + event.userId() + ":" + payload.get().month();
			Optional<Long> outboxId = createService.createInNewTransaction(event.userId(),
					NotificationType.SUBSCRIBE_GUIDE, dedupeKey, payload.get());

			// 4. 새로 만들었고 즉시 발송이 켜져 있으면 스케줄러를 기다리지 않고 보낸다
			if (outboxId.isPresent() && properties.immediateSendEnabled()) {
				dispatcher.dispatchNow(outboxId.get());
			}
		} catch (Exception e) {
			// 저장은 이미 커밋됐다. 알림 실패가 사용자에게 가지 않도록 로그만 남긴다
			log.error("가입 안내 알림 처리 중 예외가 났다: userId={}, planId={}", event.userId(), event.planId(), e);
		}
	}
}
