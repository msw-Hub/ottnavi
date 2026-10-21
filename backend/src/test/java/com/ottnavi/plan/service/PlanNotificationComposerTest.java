package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.notification.service.model.MonthlyPayload;
import com.ottnavi.notification.service.model.NotificationProductItem;
import com.ottnavi.notification.service.model.SubscribeGuidePayload;
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.product.domain.enums.PriceSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 플랜을 알림 재료로 바꾸는 판정(Task 073)을 확인한다.
 * 막으려는 사고: 이미 구독 중이거나 무료인 서비스를 "가입"으로 안내하는 일, 계속 쓸 서비스에 해지 예약을 권하는 일,
 * 가입할 것이 없는데 가입 안내가 나가는 일, 이번 달이 아닌 플랜으로 안내가 나가는 일.
 * 기본 상황: 이번 달 11월. 0번 달(11월)에 상품이 주어지고, 1번 달(12월)에는 디즈니+(상품 2)만 있다.
 */
class PlanNotificationComposerTest {

	private static final LocalDate NOV = LocalDate.of(2026, 11, 1);
	private static final LocalDate DEC = LocalDate.of(2026, 12, 1);
	private static final Map<Long, String> NAMES = Map.of(1L, "넷플릭스", 5L, "디즈니+", 3L, "티빙");

	@Test
	void 새로_결제하는_상품만_가입으로_안내하고_이미_구독_중이거나_무료인_상품은_가입에서_뺀다() {
		// given
		PlanDraftView view = view(NOV, product(1L, "넷플릭스 스탠다드", 1L, 13_500, PriceSource.ADMIN),
				product(2L, "디즈니+ 스탠다드", 5L, 9_900, PriceSource.ALREADY_PAID),
				product(3L, "티빙 스탠다드", 3L, 0, PriceSource.FREE));

		// when
		Optional<SubscribeGuidePayload> payload = PlanNotificationComposer.buildSubscribeGuide("홍길동", view, NAMES, NOV);

		// then
		assertThat(payload).isPresent();
		assertThat(payload.get().joinProducts()).hasSize(1);
		NotificationProductItem item = payload.get().joinProducts().get(0);
		assertThat(item.productName()).isEqualTo("넷플릭스 스탠다드");
		assertThat(item.serviceNames()).containsExactly("넷플릭스");
		assertThat(item.appliedPrice()).isEqualTo(13_500);
		assertThat(payload.get().month()).isEqualTo("2026-11");
		assertThat(payload.get().userName()).isEqualTo("홍길동");
		assertThat(payload.get().titles().get(0).serviceName()).isEqualTo("넷플릭스");
	}

	@Test
	void 이번_달에만_있고_다음_달에는_없는_상품을_해지_예약으로_권하되_무료_상품과_계속_쓰는_상품은_권하지_않는다() {
		// given: 12월에는 디즈니+(상품 2)만 남는다
		PlanDraftView view = view(NOV, product(1L, "넷플릭스 스탠다드", 1L, 13_500, PriceSource.ADMIN),
				product(2L, "디즈니+ 스탠다드", 5L, 9_900, PriceSource.ALREADY_PAID),
				product(3L, "티빙 스탠다드", 3L, 0, PriceSource.FREE));

		// when
		Optional<SubscribeGuidePayload> payload = PlanNotificationComposer.buildSubscribeGuide("홍길동", view, NAMES, NOV);

		// then
		assertThat(payload).isPresent();
		List<String> names = new ArrayList<>();
		for (NotificationProductItem item : payload.get().cancelReserveProducts()) {
			names.add(item.productName());
		}
		assertThat(names).containsExactly("넷플릭스 스탠다드");
	}

	@Test
	void 이번_달_가입할_상품이_하나도_없으면_가입_안내를_만들지_않는다() {
		// given: 이미 구독 중인 상품과 무료 상품뿐이다
		PlanDraftView view = view(NOV, product(2L, "디즈니+ 스탠다드", 5L, 9_900, PriceSource.ALREADY_PAID),
				product(3L, "티빙 스탠다드", 3L, 0, PriceSource.FREE));

		// when
		Optional<SubscribeGuidePayload> payload = PlanNotificationComposer.buildSubscribeGuide("홍길동", view, NAMES, NOV);

		// then
		assertThat(payload).isEmpty();
	}

	@Test
	void 플랜의_첫_달이_이번_달이_아니면_가입_안내를_만들지_않는다() {
		// given: 플랜의 첫 달은 12월인데 이번 달은 11월이다
		PlanDraftView view = view(DEC, product(1L, "넷플릭스 스탠다드", 1L, 13_500, PriceSource.ADMIN));

		// when
		Optional<SubscribeGuidePayload> payload = PlanNotificationComposer.buildSubscribeGuide("홍길동", view, NAMES, NOV);

		// then
		assertThat(payload).isEmpty();
	}

	@Test
	void 월초_안내는_가입할_상품이_없어도_만들고_이미_구독_중인_상품을_이어_쓰는_상품으로_담는다() {
		// given
		PlanDraftView view = view(NOV, product(2L, "디즈니+ 스탠다드", 5L, 9_900, PriceSource.ALREADY_PAID));

		// when
		MonthlyPayload payload = PlanNotificationComposer.buildMonthly("홍길동", view, NAMES, List.of("가격이 바뀌었어요"));

		// then
		assertThat(payload.joinProducts()).isEmpty();
		assertThat(payload.keepProducts()).hasSize(1);
		assertThat(payload.keepProducts().get(0).productName()).isEqualTo("디즈니+ 스탠다드");
		assertThat(payload.changeReasons()).containsExactly("가격이 바뀌었어요");
		assertThat(payload.summary()).contains("11월에는 새로 가입할 서비스 없이");
	}

	/** 0번 달(firstMonth)에는 주어진 상품이, 1번 달(그 다음 달)에는 상품 2(디즈니+)만 있는 플랜을 만든다 */
	private static PlanDraftView view(LocalDate firstMonth, PlanDraftView.Product... firstProducts) {
		PlanDraftView.Month first = new PlanDraftView.Month(0, firstMonth, true, 13_500, List.of(firstProducts),
				List.of(assignment()));
		PlanDraftView.Month second = new PlanDraftView.Month(1, firstMonth.plusMonths(1), false, 0,
				List.of(product(2L, "디즈니+ 스탠다드", 5L, 9_900, PriceSource.ALREADY_PAID)), List.of());
		return new PlanDraftView(1L, PlanType.RECOMMENDED, firstMonth, SolverType.EXACT, 20_000, 600, 1, 1, 1, 1, 100,
				13_500, 30_000, 16_500, List.of(first, second), List.of(), LocalDate.of(2026, 11, 3));
	}

	private static PlanDraftView.Product product(long productId, String name, long serviceId, int price,
			PriceSource source) {
		return new PlanDraftView.Product(productId, name, List.of(serviceId), price, source);
	}

	private static PlanDraftView.Assignment assignment() {
		return new PlanDraftView.Assignment(1L, MediaType.MOVIE, 100L, "카지노", null, Priority.MUST, 1L, 120, 1, 1,
				false);
	}
}
