package com.ottnavi.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.product.service.MyServicePriceService;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.user.service.model.SubscriptionState;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 내 서비스 요금 API를 실제 보안 체인과 DB로 확인한다(Task 060, Docker Desktop 필요).
 * 막으려는 사고: 내가 고친 금액이 관리자 요금표나 다른 사용자에게 번지는 일, 판매 종료·없는 상품의 금액이 저장되는 일,
 * 지운 뒤에도 고친 금액이 적용되는 일, 같은 사용자의 동시 수정이 500(유니크 위반)이 되는 일.
 * 시험이 만든 상품(이름 접두사 TASK060)만 지운다.
 */
@AutoConfigureMockMvc
class MyServicePriceApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me/service-prices";
	private static final String NAME_PREFIX = "TASK060 ";
	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private UserRepository userRepository;
	@Autowired private OttServiceRepository ottServiceRepository;
	@Autowired private SubscriptionProductRepository subscriptionProductRepository;
	@Autowired private ProductComponentRepository productComponentRepository;
	@Autowired private ProductPriceRepository productPriceRepository;
	@Autowired private UserSettingService userSettingService;
	@Autowired private MyServicePriceService myServicePriceService;
	@Autowired private JdbcTemplate jdbcTemplate;

	private List<OttService> services;

	@BeforeEach
	void 초기화() {
		cleanUp();
		services = ottServiceRepository.findAllByOrderByDisplayOrderAsc();
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	/** 이 시험이 만든 상품과 그 상품을 참조하는 행만 FK 순서대로 지운다 */
	private void cleanUp() {
		String productIds = "SELECT id FROM subscription_product WHERE name LIKE 'TASK060%'";
		jdbcTemplate.update("DELETE FROM user_price_override WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM product_price WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM product_component WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM subscription_product WHERE name LIKE 'TASK060%'");
	}

	@Test
	void 비로그인은_401이다() throws Exception {
		// given
		// when
		// then
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(put(PATH + "/1").contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPrice\":1000}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(PATH + "/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void 기본_요금_조회부터_수정_지우기까지_이어지고_관리자_요금은_그대로다() throws Exception {
		// given: 현재 적용 중인 요금(12000)과 미래 요금 이력(15000)이 있는 판매 중 상품
		long productId = saveProduct("정상", services.get(0), true, TODAY.minusDays(30), 12000, TODAY.plusDays(10), 15000);
		String bearer = bearerOfNewUser();

		// when & then 1: 기본 요금은 현재 적용 중인 요금이고 고친 금액이 없다
		Map<String, Object> line = readLine(bearer, productId);
		assertThat(number(line, "adminPrice")).isEqualTo(12000);
		assertThat(line.get("userPrice")).isNull();
		assertThat(number(line, "appliedPrice")).isEqualTo(12000);
		assertThat(line.get("priceSource")).isEqualTo("ADMIN");
		assertThat(line.get("subscriptionStatus")).isEqualTo("NOT_SUBSCRIBED");
		assertThat(line.get("productName")).isEqualTo(NAME_PREFIX + "정상");
		assertThat(line.get("effectiveFrom")).isEqualTo(TODAY.minusDays(30).toString());
		assertThat(number(line, "ottServiceId")).isEqualTo(services.get(0).getId());

		// when & then 2: 9900으로 고치면 바뀐 항목이 돌아온다
		mockMvc.perform(put(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPrice\":9900}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.productId").value(productId))
				.andExpect(jsonPath("$.data.adminPrice").value(12000))
				.andExpect(jsonPath("$.data.userPrice").value(9900))
				.andExpect(jsonPath("$.data.appliedPrice").value(9900))
				.andExpect(jsonPath("$.data.priceSource").value("USER_OVERRIDE"));

		// when & then 3: 다시 조회하면 USER_OVERRIDE이고 관리자 요금표는 그대로다
		line = readLine(bearer, productId);
		assertThat(number(line, "adminPrice")).isEqualTo(12000);
		assertThat(number(line, "userPrice")).isEqualTo(9900);
		assertThat(line.get("priceSource")).isEqualTo("USER_OVERRIDE");
		List<Integer> adminPrices = jdbcTemplate.queryForList(
				"SELECT monthly_price FROM product_price WHERE product_id = ? ORDER BY effective_from", Integer.class, productId);
		assertThat(adminPrices).containsExactly(12000, 15000);

		// when & then 4: 한 번 더 고치면 같은 행의 금액만 바뀐다(upsert)
		mockMvc.perform(put(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPrice\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userPrice").value(0))
				.andExpect(jsonPath("$.data.appliedPrice").value(0));
		assertThat(overrideCount(productId)).isEqualTo(1);

		// when & then 5: 지우면 기본 요금으로 돌아온다
		mockMvc.perform(delete(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isNoContent());
		line = readLine(bearer, productId);
		assertThat(line.get("userPrice")).isNull();
		assertThat(number(line, "appliedPrice")).isEqualTo(12000);
		assertThat(line.get("priceSource")).isEqualTo("ADMIN");
	}

	@Test
	void 한_사용자가_고친_금액은_다른_사용자에게_보이지_않는다() throws Exception {
		// given
		long productId = saveProduct("격리", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		String userA = bearerOfNewUser();
		String userB = bearerOfNewUser();

		// when
		mockMvc.perform(put(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, userA)
						.contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPrice\":5000}"))
				.andExpect(status().isOk());

		// then: A는 고친 금액, B는 관리자 요금이고, B의 되돌리기는 A의 금액을 지우지 못한다
		assertThat(readLine(userA, productId).get("priceSource")).isEqualTo("USER_OVERRIDE");
		assertThat(readLine(userB, productId).get("priceSource")).isEqualTo("ADMIN");
		mockMvc.perform(delete(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, userB))
				.andExpect(status().isNoContent());
		assertThat(number(readLine(userA, productId), "userPrice")).isEqualTo(5000);
	}

	@Test
	void 판매_종료나_없는_상품은_목록에서_빠지고_수정과_지우기는_404다() throws Exception {
		// given
		long inactiveId = saveProduct("종료", services.get(0), false, TODAY.minusDays(3), 12000, null, 0);
		String bearer = bearerOfNewUser();

		// when & then: 목록에 없다
		String body = mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> found = JsonPath.parse(body).read("$.data.services[?(@.productId == " + inactiveId + ")]");
		assertThat(found).isEmpty();

		// when & then: 판매 종료 상품과 없는 상품은 PUT·DELETE 모두 404 SERVICE_PRICE_NOT_FOUND
		long[] unusable = {inactiveId, 999999999L};
		for (long productId : unusable) {
			mockMvc.perform(put(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer)
							.contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPrice\":1000}"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errorCode").value("SERVICE_PRICE_NOT_FOUND"));
			mockMvc.perform(delete(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errorCode").value("SERVICE_PRICE_NOT_FOUND"));
		}
		assertThat(overrideCount(inactiveId)).isZero();
	}

	@Test
	void 현재_적용_중인_요금이_없는_상품은_목록에서_빠지고_수정과_지우기는_404다() throws Exception {
		// given: 모든 요금 이력이 미래인 판매 중 상품(관리자 API 규칙으로는 만들 수 없는 비정상 데이터)
		long productId = saveProduct("미래", services.get(0), true, TODAY.plusDays(5), 12000, null, 0);
		String bearer = bearerOfNewUser();

		// when
		String body = mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		// then
		List<Map<String, Object>> found = JsonPath.parse(body).read("$.data.services[?(@.productId == " + productId + ")]");
		assertThat(found).isEmpty();
		mockMvc.perform(put(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPrice\":1000}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("SERVICE_PRICE_NOT_FOUND"));
		mockMvc.perform(delete(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("SERVICE_PRICE_NOT_FOUND"));
		assertThat(overrideCount(productId)).isZero();
	}

	@Test
	void 고친_금액이_없어도_지우기는_204이고_몇_번을_불러도_같다() throws Exception {
		// given
		long productId = saveProduct("멱등", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		String bearer = bearerOfNewUser();

		// when & then
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(delete(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer))
					.andExpect(status().isNoContent());
		}
	}

	@Test
	void 금액이_범위를_벗어나거나_없으면_400_VALIDATION_FAILED다() throws Exception {
		// given
		long productId = saveProduct("검증", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		String bearer = bearerOfNewUser();
		String[] bodies = {"{\"monthlyPrice\":-1}", "{\"monthlyPrice\":1000001}", "{}"};

		// when & then
		for (String body : bodies) {
			mockMvc.perform(put(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer)
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		}
		assertThat(overrideCount(productId)).isZero();
	}

	@Test
	void 금액이_소수면_버림으로_통과하지_않고_400_VALIDATION_FAILED다() throws Exception {
		// given
		long productId = saveProduct("소수", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		String bearer = bearerOfNewUser();

		// when & then
		mockMvc.perform(put(PATH + "/" + productId).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPrice\":9900.5}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		assertThat(overrideCount(productId)).isZero();
	}

	@Test
	void 내_설정의_이용_상태가_요금에_반영된다() throws Exception {
		// given: 첫 서비스는 SUBSCRIBED, 둘째 서비스는 UNKNOWN, 셋째 서비스는 행이 없다
		long first = saveProduct("구독", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		long second = saveProduct("모름", services.get(1), true, TODAY.minusDays(3), 9000, null, 0);
		long third = saveProduct("미구독", services.get(2), true, TODAY.minusDays(3), 7000, null, 0);
		User user = createUser();
		List<SubscriptionState> states = new ArrayList<>();
		states.add(new SubscriptionState(services.get(0).getId(), ServiceSubscriptionStatus.SUBSCRIBED, "스탠다드", 1));
		states.add(new SubscriptionState(services.get(1).getId(), ServiceSubscriptionStatus.UNKNOWN, null, null));
		userSettingService.updateSettings(user.getId(), 20000, 900, WatchPreset.NORMAL, false, states);
		String bearer = new TestTokens(jwtTokenProvider).bearer(user.getId(), UserRole.USER);

		// when & then
		assertThat(readLine(bearer, first).get("subscriptionStatus")).isEqualTo("SUBSCRIBED");
		assertThat(readLine(bearer, second).get("subscriptionStatus")).isEqualTo("UNKNOWN");
		assertThat(readLine(bearer, third).get("subscriptionStatus")).isEqualTo("NOT_SUBSCRIBED");
	}

	@Test
	void 모드에_따라_계산_후보가_달라지고_번들은_서비스_목록과_미구독으로_나온다() throws Exception {
		// given: 첫 서비스에 표준 단품(12000)과 저가형 단품(7000), 첫째·둘째 서비스를 묶은 표준 번들(16000)
		long standard = saveProduct("표준", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		long lowCost = saveProduct("저가", services.get(0), true, TODAY.minusDays(3), 7000, null, 0, true);
		long bundle = saveBundle("번들", services.get(0), services.get(1), false, 16000);
		User offUser = createUser();
		userSettingService.updateSettings(offUser.getId(), 20000, 900, WatchPreset.NORMAL, false, new ArrayList<>());
		User onUser = createUser();
		userSettingService.updateSettings(onUser.getId(), 20000, 900, WatchPreset.NORMAL, true, new ArrayList<>());
		String offBearer = new TestTokens(jwtTokenProvider).bearer(offUser.getId(), UserRole.USER);
		String onBearer = new TestTokens(jwtTokenProvider).bearer(onUser.getId(), UserRole.USER);

		// when & then 1: 모드 끔은 표준 단품과 표준 번들이 후보다
		assertThat(readLine(offBearer, standard).get("isCalcCandidate")).isEqualTo(true);
		assertThat(readLine(offBearer, lowCost).get("isCalcCandidate")).isEqualTo(false);
		assertThat(readLine(offBearer, lowCost).get("isLowCost")).isEqualTo(true);
		Map<String, Object> bundleLine = readLine(offBearer, bundle);
		assertThat(bundleLine.get("isCalcCandidate")).isEqualTo(true);
		assertThat(bundleLine.get("productType")).isEqualTo("BUNDLE");
		assertThat(bundleLine.get("ottServiceId")).isNull();
		assertThat(bundleLine.get("ottServiceIds")).isEqualTo(
				List.of(services.get(0).getId().intValue(), services.get(1).getId().intValue()));
		assertThat(bundleLine.get("subscriptionStatus")).isEqualTo("NOT_SUBSCRIBED");

		// when & then 2: 모드 켬은 가장 싼 저가형 단품이 후보다
		assertThat(readLine(onBearer, standard).get("isCalcCandidate")).isEqualTo(false);
		assertThat(readLine(onBearer, lowCost).get("isCalcCandidate")).isEqualTo(true);
	}

	@Test
	void 목록은_서비스_ID_순이다() throws Exception {
		// given: 서비스 ID가 큰 쪽을 먼저 등록한다
		saveProduct("순서2", services.get(2), true, TODAY.minusDays(3), 7000, null, 0);
		saveProduct("순서1", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		String bearer = bearerOfNewUser();

		// when
		String body = mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		// then
		List<Integer> serviceIds = JsonPath.parse(body).read("$.data.services[*].ottServiceId");
		for (int i = 1; i < serviceIds.size(); i++) {
			assertThat(serviceIds.get(i)).isGreaterThanOrEqualTo(serviceIds.get(i - 1));
		}
	}

	@Test
	void 같은_사용자의_동시_수정도_행_하나로_끝나고_500이_나지_않는다() throws Exception {
		// given: 고친 금액이 아직 없는 상태에서 두 요청이 동시에 들어온다
		long productId = saveProduct("동시", services.get(0), true, TODAY.minusDays(3), 12000, null, 0);
		User user = createUser();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<Void>> futures = new ArrayList<>();
		try {
			for (int t = 0; t < 2; t++) {
				int price = 1000 + t;
				futures.add(pool.submit(() -> {
					start.await();
					myServicePriceService.set(user.getId(), productId, price);
					return null;
				}));
			}

			// when
			start.countDown();
			for (Future<Void> future : futures) {
				future.get(); // 예외가 있으면 여기서 던져져 시험이 실패한다
			}

			// then
			assertThat(overrideCount(productId)).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}

	// 데이터 준비

	/**
	 * 단품 상품과 구성, 첫 요금 이력(과 선택 요금 이력 하나)을 저장하고 상품 ID를 돌려준다.
	 * 둘째 요금 이력은 secondFrom이 null이 아닐 때만 만든다.
	 */
	private long saveProduct(String name, OttService service, boolean isActive, LocalDate firstFrom, int firstPrice,
			LocalDate secondFrom, int secondPrice) {
		return saveProduct(name, service, isActive, firstFrom, firstPrice, secondFrom, secondPrice, false);
	}

	/** 번들 상품과 구성(서비스 둘), 오늘부터 적용되는 요금 이력 하나를 저장하고 상품 ID를 돌려준다 */
	private long saveBundle(String name, OttService first, OttService second, boolean isLowCost, int price) {
		SubscriptionProduct product = subscriptionProductRepository.save(
				SubscriptionProduct.create(NAME_PREFIX + name, ProductType.BUNDLE, isLowCost));
		productComponentRepository.save(ProductComponent.create(product, first, ProviderTier.STANDARD));
		productComponentRepository.save(ProductComponent.create(product, second, ProviderTier.STANDARD));
		productPriceRepository.save(ProductPrice.create(product, price, TODAY.minusDays(3), Instant.now()));
		return product.getId();
	}

	private long saveProduct(String name, OttService service, boolean isActive, LocalDate firstFrom, int firstPrice,
			LocalDate secondFrom, int secondPrice, boolean isLowCost) {
		SubscriptionProduct product = SubscriptionProduct.create(NAME_PREFIX + name, ProductType.SINGLE, isLowCost);
		if (!isActive) {
			product.deactivate();
		}
		product = subscriptionProductRepository.save(product);
		productComponentRepository.save(ProductComponent.create(product, service, ProviderTier.STANDARD));
		productPriceRepository.save(ProductPrice.create(product, firstPrice, firstFrom, Instant.now()));
		if (secondFrom != null) {
			productPriceRepository.save(ProductPrice.create(product, secondPrice, secondFrom, Instant.now()));
		}
		return product.getId();
	}

	/** 목록을 조회해 상품 ID가 같은 항목을 돌려준다 */
	private Map<String, Object> readLine(String bearer, long productId) throws Exception {
		String body = mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> found = JsonPath.parse(body).read("$.data.services[?(@.productId == " + productId + ")]");
		assertThat(found).hasSize(1);
		return found.get(0);
	}

	/** JSON 숫자를 long으로 읽는다(JsonPath가 Integer·Long 중 무엇을 줄지에 상관없이 비교하려고) */
	private long number(Map<String, Object> line, String key) {
		return ((Number) line.get(key)).longValue();
	}

	private int overrideCount(long productId) {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM user_price_override WHERE product_id = ?", Integer.class, productId);
	}

	private String bearerOfNewUser() {
		return new TestTokens(jwtTokenProvider).bearer(createUser().getId(), UserRole.USER);
	}

	private User createUser() {
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		return userRepository.save(User.create(sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
	}
}
