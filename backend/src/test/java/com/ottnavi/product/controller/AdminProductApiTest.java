package com.ottnavi.product.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 관리자 상품·가격 API 통합 시험(Task 059, Docker 필요). 시험이 만든 상품(이름 접두사 TASK059)만 지운다 */
@AutoConfigureMockMvc
class AdminProductApiTest extends IntegrationTestSupport {

	private static final String URL = "/api/admin/products";
	private static final String NAME_PREFIX = "TASK059 ";
	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private String adminBearer() {
		return new TestTokens(jwtTokenProvider).adminBearer();
	}

	@BeforeEach
	@AfterEach
	void 시험이_만든_상품을_지운다() {
		jdbcTemplate.update("DELETE FROM product_price WHERE product_id IN "
				+ "(SELECT id FROM subscription_product WHERE name LIKE 'TASK059%')");
		jdbcTemplate.update("DELETE FROM product_component WHERE product_id IN "
				+ "(SELECT id FROM subscription_product WHERE name LIKE 'TASK059%')");
		jdbcTemplate.update("DELETE FROM subscription_product WHERE name LIKE 'TASK059%'");
	}

	private long ottServiceId() {
		return jdbcTemplate.queryForObject("SELECT min(id) FROM ott_service", Long.class);
	}

	private String createBody(String name, String productType, long serviceId, long monthlyPrice, LocalDate from) {
		return "{\"name\":\"" + name + "\",\"productType\":\"" + productType + "\",\"ottServiceId\":" + serviceId
				+ ",\"monthlyPrice\":" + monthlyPrice + ",\"effectiveFrom\":\"" + from + "\"}";
	}

	private String priceBody(long monthlyPrice, LocalDate from) {
		return "{\"monthlyPrice\":" + monthlyPrice + ",\"effectiveFrom\":\"" + from + "\"}";
	}

	private ResultActions create(String name, LocalDate from) throws Exception {
		return mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, adminBearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(NAME_PREFIX + name, "SINGLE", ottServiceId(), 12000, from)));
	}

	/** 상품을 등록하고 응답 본문 문자열을 돌려준다 */
	private String createAndRead(String name, LocalDate from) throws Exception {
		return create(name, from).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
	}

	private ResultActions addPrice(long productId, long monthlyPrice, LocalDate from) throws Exception {
		return mockMvc.perform(post(URL + "/" + productId + "/prices").header(HttpHeaders.AUTHORIZATION, adminBearer())
				.contentType(MediaType.APPLICATION_JSON).content(priceBody(monthlyPrice, from)));
	}

	private ResultActions updatePrice(long productId, long priceId, long monthlyPrice, LocalDate from) throws Exception {
		return mockMvc.perform(patch(URL + "/" + productId + "/prices/" + priceId)
				.header(HttpHeaders.AUTHORIZATION, adminBearer())
				.contentType(MediaType.APPLICATION_JSON).content(priceBody(monthlyPrice, from)));
	}

	@Test
	void 등록_추가_수정_확인_삭제_목록의_정상_경로가_이어진다() throws Exception {
		// given
		String created = createAndRead("정상", TODAY.minusDays(30));
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);
		long firstPriceId = JsonPath.parse(created).read("$.data.prices[0].priceId", Long.class);

		// when
		// then
		// 1. 등록: 단품, 서비스 ID, 판매 중, 첫 줄 1개
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.products[?(@.productId == " + productId + ")].productType").value("SINGLE"))
				.andExpect(jsonPath("$.data.products[?(@.productId == " + productId + ")].isActive").value(true));

		// 2. 줄 추가(미래 시작일 허용): 응답은 상품 전체이고 시작일 오름차순
		String added = addPrice(productId, 14500, TODAY.plusDays(10))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.prices", hasSize(2)))
				.andExpect(jsonPath("$.data.prices[0].priceId").value(firstPriceId))
				.andExpect(jsonPath("$.data.prices[1].monthlyPrice").value(14500))
				.andReturn().getResponse().getContentAsString();
		long secondPriceId = JsonPath.parse(added).read("$.data.prices[1].priceId", Long.class);

		// 3. 마지막 줄 수정
		updatePrice(productId, secondPriceId, 15000, TODAY.plusDays(20))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.prices[1].monthlyPrice").value(15000))
				.andExpect(jsonPath("$.data.prices[1].effectiveFrom").value(TODAY.plusDays(20).toString()));

		// 4. 오늘 적용 중인 첫 줄 확인함
		mockMvc.perform(post(URL + "/" + productId + "/prices/" + firstPriceId + "/verify")
						.header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.prices[0].monthlyPrice").value(12000));

		// 5. 마지막 줄 삭제 후 목록에서 한 줄만 남는다
		mockMvc.perform(delete(URL + "/" + productId + "/prices/" + secondPriceId)
						.header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.products[?(@.productId == " + productId + ")].prices.length()").value(1));
	}

	@Test
	void 이름과_판매_여부를_바꾸고_판매_종료_상품도_목록에_남는다() throws Exception {
		// given
		String created = createAndRead("수정", TODAY);
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);

		// when
		mockMvc.perform(patch(URL + "/" + productId).header(HttpHeaders.AUTHORIZATION, adminBearer())
						.contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(false))
				.andExpect(jsonPath("$.data.name").value(NAME_PREFIX + "수정"));

		// then
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.products[?(@.productId == " + productId + ")].isActive").value(false));
	}

	@Test
	void 같은_시작일로_줄을_추가하면_409_PRICE_START_DATE_DUPLICATE다() throws Exception {
		// given
		String created = createAndRead("중복", TODAY.minusDays(5));
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);

		// when
		// then
		addPrice(productId, 13000, TODAY.minusDays(5))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PRICE_START_DATE_DUPLICATE"));
	}

	@Test
	void 마지막_줄보다_이른_시작일이면_409_PRICE_START_DATE_NOT_AFTER_LAST다() throws Exception {
		// given
		String created = createAndRead("순서", TODAY.minusDays(5));
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);

		// when
		// then
		addPrice(productId, 13000, TODAY.minusDays(10))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PRICE_START_DATE_NOT_AFTER_LAST"));
	}

	@Test
	void 마지막_줄이_아닌_줄을_수정하면_409_PRICE_NOT_LATEST다() throws Exception {
		// given
		String created = createAndRead("최신", TODAY.minusDays(20));
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);
		long firstPriceId = JsonPath.parse(created).read("$.data.prices[0].priceId", Long.class);
		addPrice(productId, 13000, TODAY.plusDays(5)).andExpect(status().isCreated());

		// when
		// then
		updatePrice(productId, firstPriceId, 9000, TODAY.minusDays(20))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PRICE_NOT_LATEST"));
	}

	@Test
	void 하나뿐인_줄을_삭제하면_409_PRICE_LAST_ROW_CANNOT_DELETE다() throws Exception {
		// given
		String created = createAndRead("삭제", TODAY);
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);
		long priceId = JsonPath.parse(created).read("$.data.prices[0].priceId", Long.class);

		// when
		// then
		mockMvc.perform(delete(URL + "/" + productId + "/prices/" + priceId)
						.header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PRICE_LAST_ROW_CANNOT_DELETE"));
	}

	@Test
	void 예정_줄을_확인하면_409_PRICE_NOT_CURRENT다() throws Exception {
		// given
		String created = createAndRead("예정", TODAY.minusDays(3));
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);
		String added = addPrice(productId, 13000, TODAY.plusDays(7)).andReturn().getResponse().getContentAsString();
		long futurePriceId = JsonPath.parse(added).read("$.data.prices[1].priceId", Long.class);

		// when
		// then
		mockMvc.perform(post(URL + "/" + productId + "/prices/" + futurePriceId + "/verify")
						.header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PRICE_NOT_CURRENT"));
	}

	@Test
	void 첫_줄의_시작일이_미래면_409이고_상품과_구성이_남지_않는다() throws Exception {
		// given
		// when
		// then
		create("미래", TODAY.plusDays(1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PRICE_FIRST_START_DATE_IN_FUTURE"));
		Integer products = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM subscription_product WHERE name LIKE 'TASK059%'", Integer.class);
		Integer components = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM product_component c JOIN subscription_product p ON p.id = c.product_id "
						+ "WHERE p.name LIKE 'TASK059%'", Integer.class);
		assertThat(products).isZero();
		assertThat(components).isZero();
	}

	@Test
	void 입력이_잘못되면_400_VALIDATION_FAILED다() throws Exception {
		// given
		String blankName = createBody("   ", "SINGLE", ottServiceId(), 12000, TODAY);
		String bundle = createBody(NAME_PREFIX + "번들", "BUNDLE", ottServiceId(), 12000, TODAY);
		String missingService = createBody(NAME_PREFIX + "서비스", "SINGLE", 999999, 12000, TODAY);
		String tooExpensive = createBody(NAME_PREFIX + "금액", "SINGLE", ottServiceId(), 1000001, TODAY);

		// when
		// then
		String[] bodies = {blankName, bundle, missingService, tooExpensive};
		for (String body : bodies) {
			mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, adminBearer())
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		}
	}

	@Test
	void 빈_수정_요청은_400이고_없는_상품은_404다() throws Exception {
		// given
		String created = createAndRead("빈수정", TODAY);
		long productId = JsonPath.parse(created).read("$.data.productId", Long.class);

		// when
		// then
		mockMvc.perform(patch(URL + "/" + productId).header(HttpHeaders.AUTHORIZATION, adminBearer())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		mockMvc.perform(patch(URL + "/999999999").header(HttpHeaders.AUTHORIZATION, adminBearer())
						.contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":true}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void USER는_403이고_토큰이_없으면_401이다() throws Exception {
		// given
		String userBearer = new TestTokens(jwtTokenProvider).userBearer();

		// when
		// then
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, userBearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		mockMvc.perform(get(URL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}
}
