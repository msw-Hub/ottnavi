package com.ottnavi.notification.service;

import com.ottnavi.global.common.ExceptionRootCause;
import com.ottnavi.infra.mail.MailClient;
import com.ottnavi.infra.mail.OutgoingMail;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.service.model.ClaimedNotification;
import com.ottnavi.notification.service.model.RenderedMail;
import com.ottnavi.user.service.UserQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 대기 중인 알림을 보내는 진입점. 트랜잭션을 걸지 않는다: 메일을 보내는 동안 DB 연결과 행 잠금을 붙잡지 않으려고
 * 점유(NotificationClaimService)와 결과 기록(NotificationResultRecorder)을 각각 짧은 트랜잭션으로 나눴다(TECH 5절, 외부 호출은 트랜잭션 밖).
 *
 * <pre>
 * 점유(짧은 트랜잭션) → 발송(트랜잭션 밖: 행·수신 주소 재확인 → 템플릿 → SMTP) → 기록(짧은 트랜잭션)
 * </pre>
 *
 * 전달 보장은 최소 1회다. 발송에 성공한 직후 기록 전에 앱이 죽으면 임대가 끝난 뒤 같은 메일이 한 번 더 나갈 수 있다(허용 범위).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

	private static final int ERROR_TEXT_MAX_LENGTH = 500;

	private final NotificationClaimService claimService;
	private final NotificationResultRecorder resultRecorder;
	private final NotificationMailRenderer mailRenderer;
	private final UserQueryService userQueryService;
	private final MailClient mailClient;
	private final NotificationMetrics metrics;
	private final Clock clock;

	/** 발송할 때가 된 알림을 한 번 훑어 보낸다. 점유한 건수를 돌려준다 */
	public int dispatchDue() {
		// 1. 발송할 행을 점유한다(점유 단계)
		List<ClaimedNotification> claimed = claimService.claimDue(Instant.now(clock));
		dispatchAll(claimed);
		return claimed.size();
	}

	/**
	 * 방금 만든 알림 한 건을 스케줄러를 기다리지 않고 바로 보낸다(구독 안내). 점유에 실패하면(스케줄러가 먼저 잡았거나 때가 아님) 아무것도 하지 않는다.
	 * 점유한 뒤에는 스케줄러와 같은 발송·기록 단계를 탄다. 발송이 실패해도 PENDING이 남아 스케줄러가 재시도한다.
	 */
	public void dispatchNow(Long outboxId) {
		// 1. 그 행 하나를 점유한다. 못 잡았으면 스케줄러가 맡는다
		Optional<ClaimedNotification> claimed = claimService.claimOne(outboxId, Instant.now(clock));
		if (claimed.isEmpty()) {
			log.info("즉시 발송을 건너뛴다(이미 점유됐거나 때가 아님): outboxId={}", outboxId);
			return;
		}

		// 2. 점유한 행을 발송하고 결과를 기록한다
		dispatchOne(claimed.get());
	}

	/** 이미 점유한 알림들을 발송·기록 단계로 처리한다. 점유 직후 상태를 시험이 끼어들 수 있게 따로 뺐다 */
	void dispatchAll(List<ClaimedNotification> claimed) {
		if (claimed.isEmpty()) {
			return;
		}

		// 1. 행마다 발송한다. 한 건의 예외가 다른 건을 막지 않게 건별로 잡는다
		for (ClaimedNotification notification : claimed) {
			try {
				dispatchOne(notification);
			} catch (Exception e) {
				// 결과 기록 자체가 실패한 경우 등. 임대가 끝나면 행이 다시 집힌다
				log.error("알림 처리 중 예외가 났다: outboxId={}", notification.id(), e);
			}
		}
		log.info("알림 발송 주기를 마쳤다: 점유={}건", claimed.size());
	}

	/** 점유한 알림 한 건을 발송하고 결과를 기록한다 */
	private void dispatchOne(ClaimedNotification notification) {
		// 1. 발송 직전에 행이 아직 있는지 확인한다(점유 뒤 탈퇴하면 FK CASCADE로 행이 사라진다)
		if (!claimService.isStillPending(notification.id())) {
			log.info("점유한 알림 행이 없거나 이미 끝나 발송하지 않는다: outboxId={}", notification.id());
			metrics.count(NotificationMetrics.SKIPPED);
			return;
		}

		// 2. 수신 주소를 발송 직전에 다시 읽는다(탈퇴·주소 변경 반영). 없으면 보내지 않는다
		Optional<String> email = userQueryService.findEmail(notification.userId());
		if (email.isEmpty()) {
			log.info("수신자를 찾을 수 없어 발송하지 않는다: outboxId={}", notification.id());
			metrics.count(NotificationMetrics.SKIPPED);
			return;
		}

		// 3. 템플릿을 채워 메일을 보낸다(발송 단계, 트랜잭션 밖). 실패하면 사유를 문자열로 모은다
		String error = null;
		try {
			RenderedMail rendered = mailRenderer.render(notification.type(), notification.payload());
			mailClient.send(new OutgoingMail(email.get(), rendered.subject(), rendered.htmlBody()));
		} catch (Exception e) {
			error = describe(e);
			// 수신 주소가 예외 메시지에 섞일 수 있어 예외 객체나 메시지는 로그에 남기지 않는다(사유는 DB last_error에 기록)
			log.warn("알림 발송에 실패했다: outboxId={}, 시도={}, 예외={}, 근본원인={}", notification.id(), notification.attemptCount(),
					e.getClass().getSimpleName(), ExceptionRootCause.rootCauseName(e));
		}

		// 4. 결과를 기록한다(기록 단계)
		if (error == null) {
			boolean recorded = resultRecorder.recordSent(notification.id(), notification.attemptCount(), Instant.now(clock));
			if (!recorded) {
				// 메일은 나갔지만 점유가 바뀌어 기록하지 못했다. 최소 1회 전달 범위이고 기록은 현재 점유자가 맡는다
				metrics.count(NotificationMetrics.SKIPPED);
				return;
			}
			metrics.count(NotificationMetrics.SENT);
			log.info("알림을 발송했다: outboxId={}, 유형={}", notification.id(), notification.type());
			return;
		}
		Optional<NotificationStatus> status = resultRecorder.recordFailure(notification.id(),
				notification.attemptCount(), error, Instant.now(clock));
		if (status.isEmpty()) {
			metrics.count(NotificationMetrics.SKIPPED);
		} else if (status.get() == NotificationStatus.FAILED) {
			metrics.count(NotificationMetrics.FAILED);
			log.warn("알림을 최대 시도 횟수까지 보내지 못해 포기한다: outboxId={}", notification.id());
		} else {
			metrics.count(NotificationMetrics.RETRY);
		}
	}

	/** 실패 사유를 "예외 클래스: 메시지" 형태로 만든다. DB에 넣기 전 500자로 자른다 */
	private String describe(Exception e) {
		String text = e.getClass().getSimpleName() + ": " + e.getMessage();
		if (text.length() > ERROR_TEXT_MAX_LENGTH) {
			return text.substring(0, ERROR_TEXT_MAX_LENGTH);
		}
		return text;
	}
}
