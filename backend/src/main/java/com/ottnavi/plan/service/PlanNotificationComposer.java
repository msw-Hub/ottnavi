package com.ottnavi.plan.service;

import com.ottnavi.notification.service.model.MonthlyPayload;
import com.ottnavi.notification.service.model.NotificationProductItem;
import com.ottnavi.notification.service.model.NotificationTitleItem;
import com.ottnavi.notification.service.model.PlanChangePayload;
import com.ottnavi.notification.service.model.SubscribeGuidePayload;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.domain.rule.PlanChangeAlertRule.LeftTitle;
import com.ottnavi.plan.domain.rule.PlanChangeSummary;
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import com.ottnavi.user.service.UserQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 저장된 플랜을 알림 메일의 재료(payload record)로 바꾼다(Task 073). 어떤 상품을 "가입"으로, 어떤 상품을 "해지 예약 권장"으로
 * 볼지의 판정이 여기 있다. 알림 도메인은 플랜을 모르므로 플랜을 아는 이 클래스가 재료를 만들어 알림 서비스에 넘긴다.
 *
 * <pre>
 * 이번 달(0번 달)에 가격 출처가 ADMIN·USER_OVERRIDE인 상품 → 가입할 상품 (ALREADY_PAID·FREE는 가입이 아니다)
 * 이번 달에 ALREADY_PAID인 상품                              → 이미 구독 중(계속)
 * 이번 달에 있고(FREE 제외) 다음 달에는 없는 상품            → 해지 예약 권장 (계속 쓸 서비스는 권하지 않는다)
 * </pre>
 *
 * 플랜을 읽는 메서드(composeXxx)는 호출하는 쪽의 트랜잭션이 있으면 거기에 합류하고, 없으면 읽기마다 짧게 연다.
 * 값을 만드는 판정(buildXxx)은 DB를 쓰지 않는 순수 변환이라 따로 시험한다.
 */
@Component
@RequiredArgsConstructor
public class PlanNotificationComposer {

	private final PlanRepository planRepository;
	private final PlanViewLoader planViewLoader;
	private final UserQueryService userQueryService;
	private final OttServiceQueryService ottServiceQueryService;
	private final Clock clock;

	/**
	 * 저장한 플랜의 가입 안내 재료를 만든다. 이번 달 가입할 상품이 없거나 플랜의 첫 달이 이번 달이 아니면 빈 값이다.
	 * 플랜·사용자가 사라졌어도(그 사이 삭제·탈퇴) 빈 값이다.
	 */
	public Optional<SubscribeGuidePayload> composeSubscribeGuide(Long userId, Long planId) {
		// 1. 플랜과 사용자 이름을 읽는다
		Optional<Plan> plan = planRepository.findById(planId);
		Optional<String> userName = userQueryService.findName(userId);
		if (plan.isEmpty() || userName.isEmpty()) {
			return Optional.empty();
		}

		// 2. 화면과 같은 값(PlanDraftView)으로 다시 읽고 서비스 이름을 모은다
		PlanDraftView view = loadView(plan.get());
		Map<Long, String> serviceNames = loadServiceNames();

		// 3. 재료를 만든다
		return buildSubscribeGuide(userName.get(), view, serviceNames, LocalDate.now(clock).withDayOfMonth(1));
	}

	/** 월초 안내 재료를 만든다. 재계산 쓰기 트랜잭션 안에서 새 ACTIVE 플랜을 읽는다. 변경 요약이 있으면 이유 문장을 함께 담는다 */
	public MonthlyPayload composeMonthly(Long userId, Plan plan, Optional<PlanChangeSummary> summary) {
		String userName = userQueryService.findName(userId).orElse("");
		return buildMonthly(userName, loadView(plan), loadServiceNames(), reasonsOf(summary));
	}

	/** 변경 알림 재료를 만든다. 재계산 쓰기 트랜잭션 안에서 새 ACTIVE 플랜을 읽는다 */
	public PlanChangePayload composePlanChange(Long userId, Plan plan, PlanChangeSummary summary,
			List<LeftTitle> leftTitles) {
		String userName = userQueryService.findName(userId).orElse("");
		return buildPlanChange(userName, loadView(plan), loadServiceNames(), leftTitles, reasonsOf(Optional.of(summary)));
	}

	// 값 만들기 (DB를 쓰지 않는 순수 변환)

	/**
	 * 가입 안내 재료를 만든다. 첫 달이 이번 달이 아니거나 가입할 상품이 하나도 없으면 빈 값이다.
	 *
	 * @param thisMonth 이번 달(KST)의 1일
	 */
	static Optional<SubscribeGuidePayload> buildSubscribeGuide(String userName, PlanDraftView view,
			Map<Long, String> serviceNames, LocalDate thisMonth) {
		// 1. 확정 달(0번 달)이 이번 달이어야 한다
		PlanDraftView.Month first = monthAt(view, 0);
		if (first == null || !first.month().equals(thisMonth)) {
			return Optional.empty();
		}

		// 2. 이번 달 가입할 상품이 없으면 안내할 것이 없다
		List<NotificationProductItem> join = productsOf(first, serviceNames, true, false);
		if (join.isEmpty()) {
			return Optional.empty();
		}

		// 3. 재료를 채운다
		List<NotificationProductItem> cancelReserve = cancelReserveOf(view, first, serviceNames);
		List<NotificationTitleItem> titles = titlesOf(first, serviceNames);
		String summary = summaryOf(first, join, titles.size());
		return Optional.of(new SubscribeGuidePayload(SubscribeGuidePayload.CURRENT_VERSION, userName,
				view.dataAsOf().toString(), YearMonth.from(first.month()).toString(), summary, join, cancelReserve, titles,
				first.cost()));
	}

	/** 월초 안내 재료를 만든다. 가입할 상품이 없어도 만든다(이미 구독 중인 상품을 이어 쓰는 달도 안내한다) */
	static MonthlyPayload buildMonthly(String userName, PlanDraftView view, Map<Long, String> serviceNames,
			List<String> changeReasons) {
		// 1. 확정 달(0번 달)
		PlanDraftView.Month first = monthAt(view, 0);
		if (first == null) {
			throw new IllegalStateException("플랜에 확정 달이 없다: planId=" + view.planId());
		}

		// 2. 재료를 채운다
		List<NotificationProductItem> join = productsOf(first, serviceNames, true, false);
		List<NotificationProductItem> keep = productsOf(first, serviceNames, false, true);
		List<NotificationProductItem> cancelReserve = cancelReserveOf(view, first, serviceNames);
		List<NotificationTitleItem> titles = titlesOf(first, serviceNames);
		String summary = summaryOf(first, join, titles.size());
		return new MonthlyPayload(MonthlyPayload.CURRENT_VERSION, userName, view.dataAsOf().toString(),
				YearMonth.from(first.month()).toString(), summary, join, keep, cancelReserve, titles, first.cost(),
				changeReasons);
	}

	/** 변경 알림 재료를 만든다. leftTitles는 배정된 서비스에서 빠진 작품이다 */
	static PlanChangePayload buildPlanChange(String userName, PlanDraftView view, Map<Long, String> serviceNames,
			List<LeftTitle> leftTitles, List<String> changeReasons) {
		// 1. 확정 달(0번 달)
		PlanDraftView.Month first = monthAt(view, 0);
		if (first == null) {
			throw new IllegalStateException("플랜에 확정 달이 없다: planId=" + view.planId());
		}

		// 2. 빠진 작품을 보여 줄 모양으로 바꾼다
		List<NotificationTitleItem> left = new ArrayList<>();
		for (LeftTitle title : leftTitles) {
			left.add(new NotificationTitleItem(title.title(), title.seasonNumber(),
					serviceNameOf(serviceNames, title.ottServiceId())));
		}

		// 3. 바뀐 플랜의 이번 달 상품과 한 줄 요약을 채운다
		List<NotificationProductItem> current = new ArrayList<>();
		for (PlanDraftView.Product product : first.products()) {
			current.add(toItem(product, serviceNames));
		}
		String summary = "이번 달에 볼 작품 중 " + left.size() + "개가 배정된 서비스에서 빠져 구독 플랜을 다시 짰어요.";
		return new PlanChangePayload(PlanChangePayload.CURRENT_VERSION, userName, view.dataAsOf().toString(),
				YearMonth.from(first.month()).toString(), summary, left, changeReasons, current);
	}

	// 판정 보조

	private static PlanDraftView.Month monthAt(PlanDraftView view, int monthIndex) {
		for (PlanDraftView.Month month : view.months()) {
			if (month.monthIndex() == monthIndex) {
				return month;
			}
		}
		return null;
	}

	/**
	 * 그 달 상품 중 가격 출처로 골라 담는다. includePaying은 ADMIN·USER_OVERRIDE(새로 결제), includeAlreadyPaid는 ALREADY_PAID(이미 구독 중)다.
	 * FREE(무료 이용)는 어느 쪽에도 담지 않는다.
	 */
	private static List<NotificationProductItem> productsOf(PlanDraftView.Month month, Map<Long, String> serviceNames,
			boolean includePaying, boolean includeAlreadyPaid) {
		List<NotificationProductItem> items = new ArrayList<>();
		for (PlanDraftView.Product product : month.products()) {
			boolean paying = product.priceSource() == PriceSource.ADMIN || product.priceSource() == PriceSource.USER_OVERRIDE;
			boolean alreadyPaid = product.priceSource() == PriceSource.ALREADY_PAID;
			if ((includePaying && paying) || (includeAlreadyPaid && alreadyPaid)) {
				items.add(toItem(product, serviceNames));
			}
		}
		return items;
	}

	/** 이번 달에 쓰고(FREE 제외) 다음 달 상품에 없는 상품. 다음 달이 없는 플랜이면 권할 것이 없다 */
	private static List<NotificationProductItem> cancelReserveOf(PlanDraftView view, PlanDraftView.Month first,
			Map<Long, String> serviceNames) {
		List<NotificationProductItem> items = new ArrayList<>();
		PlanDraftView.Month second = monthAt(view, 1);
		if (second == null) {
			return items;
		}
		Set<Long> nextProductIds = new HashSet<>();
		for (PlanDraftView.Product product : second.products()) {
			nextProductIds.add(product.productId());
		}
		for (PlanDraftView.Product product : first.products()) {
			if (product.priceSource() == PriceSource.FREE || nextProductIds.contains(product.productId())) {
				continue;
			}
			items.add(toItem(product, serviceNames));
		}
		return items;
	}

	private static List<NotificationTitleItem> titlesOf(PlanDraftView.Month month, Map<Long, String> serviceNames) {
		List<NotificationTitleItem> items = new ArrayList<>();
		for (PlanDraftView.Assignment assignment : month.assignments()) {
			items.add(new NotificationTitleItem(assignment.title(), assignment.seasonNumber(),
					serviceNameOf(serviceNames, assignment.ottServiceId())));
		}
		return items;
	}

	private static NotificationProductItem toItem(PlanDraftView.Product product, Map<Long, String> serviceNames) {
		List<String> names = new ArrayList<>();
		for (Long serviceId : product.ottServiceIds()) {
			names.add(serviceNameOf(serviceNames, serviceId));
		}
		return new NotificationProductItem(product.productName(), names, product.appliedPrice());
	}

	private static String serviceNameOf(Map<Long, String> serviceNames, long ottServiceId) {
		String name = serviceNames.get(ottServiceId);
		if (name == null) {
			return "서비스 " + ottServiceId;
		}
		return name;
	}

	/** 한 줄 요약. 예: "11월에는 넷플릭스 스탠다드, 디즈니+ 스탠다드에 가입해 작품 3개를 볼 수 있어요." */
	private static String summaryOf(PlanDraftView.Month first, List<NotificationProductItem> join, int titleCount) {
		int monthValue = first.month().getMonthValue();
		if (join.isEmpty()) {
			return monthValue + "월에는 새로 가입할 서비스 없이 작품 " + titleCount + "개를 볼 수 있어요.";
		}
		StringBuilder names = new StringBuilder();
		for (NotificationProductItem item : join) {
			if (names.length() > 0) {
				names.append(", ");
			}
			names.append(item.productName());
		}
		return monthValue + "월에는 " + names + "에 가입해 작품 " + titleCount + "개를 볼 수 있어요.";
	}

	private static List<String> reasonsOf(Optional<PlanChangeSummary> summary) {
		List<String> reasons = new ArrayList<>();
		if (summary.isPresent()) {
			for (PlanChangeSummary.Reason reason : summary.get().reasons()) {
				reasons.add(reason.description());
			}
		}
		return reasons;
	}

	// 읽기 보조

	private PlanDraftView loadView(Plan plan) {
		List<Plan> plans = new ArrayList<>();
		plans.add(plan);
		return planViewLoader.load(plans).get(0);
	}

	private Map<Long, String> loadServiceNames() {
		Map<Long, String> names = new HashMap<>();
		for (OttService service : ottServiceQueryService.listOttServices()) {
			names.put(service.getId(), service.getName());
		}
		return names;
	}
}
