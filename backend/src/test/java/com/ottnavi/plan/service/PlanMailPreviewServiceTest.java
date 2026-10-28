package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 예시 메일 조립(Task 110)의 분기를 확인한다.
 * 막으려는 사고: 현재 플랜이 없는데 메일이 만들어지는 일, 가입할 상품이 없는데 구독 안내가 나오는 일,
 * 메일 순서(구독 안내 → 월초 안내)가 바뀌는 일.
 */
class PlanMailPreviewServiceTest {

	private static final long USER_ID = 7L;
	private static final long PLAN_ID = 70L;

	private final PlanRepository planRepository = mock(PlanRepository.class);
	private final PlanNotificationComposer composer = mock(PlanNotificationComposer.class);
	private final NotificationMailRenderer renderer = mock(NotificationMailRenderer.class);
	private final PlanMailPreviewService service = new PlanMailPreviewService(planRepository, composer, renderer);

	@Test
	void 현재_플랜이_없으면_메일이_없고_NO_CURRENT_PLAN_사유만_담긴다() {
		// given
		when(planRepository.findByUserIdAndStatus(USER_ID, PlanStatus.ACTIVE)).thenReturn(Optional.empty());

		// when
		PlanMailPreviewView view = service.preview(USER_ID);

		// then
		assertThat(view.mails()).isEmpty();
		assertThat(view.noMailReasons()).containsExactly(PlanMailPreviewNoMailReason.NO_CURRENT_PLAN);
		verifyNoInteractions(composer, renderer);
	}

	@Test
	void 구독_안내와_월초_안내_두_통을_이_순서로_만든다() {
		// given
		Plan plan = activePlan();
		SubscribeGuidePayload guide = mock(SubscribeGuidePayload.class);
		MonthlyPayload monthly = mock(MonthlyPayload.class);
		when(composer.composeSubscribeGuide(USER_ID, PLAN_ID)).thenReturn(Optional.of(guide));
		when(composer.composeMonthly(eq(USER_ID), eq(plan), any())).thenReturn(monthly);
		when(renderer.render(NotificationType.SUBSCRIBE_GUIDE, (Object) guide)).thenReturn(new RenderedMail("구독 제목", "<p>구독</p>"));
		when(renderer.render(NotificationType.MONTHLY, (Object) monthly)).thenReturn(new RenderedMail("월초 제목", "<p>월초</p>"));

		// when
		PlanMailPreviewView view = service.preview(USER_ID);

		// then
		assertThat(view.noMailReasons()).isEmpty();
		List<PlanMailPreviewView.Mail> mails = view.mails();
		assertThat(mails).hasSize(2);
		assertThat(mails.get(0).type()).isEqualTo(PlanMailPreviewType.SUBSCRIBE_GUIDE);
		assertThat(mails.get(0).subject()).isEqualTo("구독 제목");
		assertThat(mails.get(0).html()).isEqualTo("<p>구독</p>");
		assertThat(mails.get(1).type()).isEqualTo(PlanMailPreviewType.MONTHLY);
		assertThat(mails.get(1).subject()).isEqualTo("월초 제목");
		verify(composer).composeMonthly(USER_ID, plan, Optional.empty());
	}

	@Test
	void 가입할_상품이_없으면_월초_안내만_만들고_NO_SUBSCRIBE_GUIDE_사유를_담는다() {
		// given
		Plan plan = activePlan();
		MonthlyPayload monthly = mock(MonthlyPayload.class);
		when(composer.composeSubscribeGuide(USER_ID, PLAN_ID)).thenReturn(Optional.empty());
		when(composer.composeMonthly(eq(USER_ID), eq(plan), any())).thenReturn(monthly);
		when(renderer.render(NotificationType.MONTHLY, (Object) monthly)).thenReturn(new RenderedMail("월초 제목", "<p>월초</p>"));

		// when
		PlanMailPreviewView view = service.preview(USER_ID);

		// then
		assertThat(view.mails()).hasSize(1);
		assertThat(view.mails().get(0).type()).isEqualTo(PlanMailPreviewType.MONTHLY);
		assertThat(view.noMailReasons()).containsExactly(PlanMailPreviewNoMailReason.NO_SUBSCRIBE_GUIDE);
	}

	private Plan activePlan() {
		Plan plan = mock(Plan.class);
		when(plan.getId()).thenReturn(PLAN_ID);
		when(planRepository.findByUserIdAndStatus(USER_ID, PlanStatus.ACTIVE)).thenReturn(Optional.of(plan));
		return plan;
	}
}
