package com.ottnavi.plan.service;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.NotificationMailRenderer;
import com.ottnavi.notification.service.model.MonthlyPayload;
import com.ottnavi.notification.service.model.RenderedMail;
import com.ottnavi.notification.service.model.SubscribeGuidePayload;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanMailPreviewNoMailReason;
import com.ottnavi.plan.domain.enums.PlanMailPreviewType;
import com.ottnavi.plan.domain.enums.PlanStatus;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.model.PlanMailPreviewView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 계정에 "플랜을 저장하면 이런 메일이 온다"를 보여 줄 예시 메일을 만든다(Task 110).
 * 운영 알림과 같은 재료 조립(PlanNotificationComposer)과 렌더러(NotificationMailRenderer)를 쓰되,
 * 메일을 보내지도 outbox에 쌓지도 않고 알림 설정도 읽지 않는다. 제목과 HTML만 돌려준다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanMailPreviewService {

	private final PlanRepository planRepository;
	private final PlanNotificationComposer planNotificationComposer;
	private final NotificationMailRenderer notificationMailRenderer;

	/** 내 현재 플랜(ACTIVE)으로 예시 메일을 만든다. 순서는 구독 안내 → 월초 안내이고, 만들 수 없는 메일은 사유로 남는다 */
	public PlanMailPreviewView preview(Long userId) {
		List<PlanMailPreviewView.Mail> mails = new ArrayList<>();
		List<PlanMailPreviewNoMailReason> reasons = new ArrayList<>();

		// 1. 현재 플랜이 없으면 만들 메일이 없다
		Optional<Plan> active = planRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
		if (active.isEmpty()) {
			reasons.add(PlanMailPreviewNoMailReason.NO_CURRENT_PLAN);
			return new PlanMailPreviewView(mails, reasons);
		}
		Plan plan = active.get();

		// 2. 구독 안내: 이번 달 가입할 상품이 있을 때만 만든다
		Optional<SubscribeGuidePayload> guide = planNotificationComposer.composeSubscribeGuide(userId, plan.getId());
		if (guide.isPresent()) {
			RenderedMail rendered = notificationMailRenderer.render(NotificationType.SUBSCRIBE_GUIDE, guide.get());
			mails.add(new PlanMailPreviewView.Mail(PlanMailPreviewType.SUBSCRIBE_GUIDE, rendered.subject(), rendered.htmlBody()));
		} else {
			reasons.add(PlanMailPreviewNoMailReason.NO_SUBSCRIBE_GUIDE);
		}

		// 3. 월초 안내: 변경 요약 없이(처음 보내는 메일처럼) 만든다
		MonthlyPayload monthly = planNotificationComposer.composeMonthly(userId, plan, Optional.empty());
		RenderedMail rendered = notificationMailRenderer.render(NotificationType.MONTHLY, monthly);
		mails.add(new PlanMailPreviewView.Mail(PlanMailPreviewType.MONTHLY, rendered.subject(), rendered.htmlBody()));
		return new PlanMailPreviewView(mails, reasons);
	}
}
