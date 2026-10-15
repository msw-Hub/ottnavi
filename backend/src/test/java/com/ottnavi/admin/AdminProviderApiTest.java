package com.ottnavi.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 제공처 매핑 관리자 API 통합 시험(Task 046, Docker 필요). 시드(MAPPED 8행)는 건드리지 않고 시험이 넣은 행만 지운다 */
@AutoConfigureMockMvc
class AdminProviderApiTest extends IntegrationTestSupport {

	private static final String URL = "/api/admin/providers";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	/** 관리자 API는 ADMIN Access Token이 필요하다(Task 053) */
	private String adminBearer() {
		return new TestTokens(jwtTokenProvider).adminBearer();
	}

	@BeforeEach
	void 시험_전에_시드만_남긴다() {
		// 다른 시험이 남긴 미매핑 행이 첫 줄 정렬 확인을 흐리지 않게 한다(다른 수집 시험과 같은 방식)
		jdbcTemplate.update("DELETE FROM tmdb_provider WHERE mapping_state <> 'MAPPED'");
		jdbcTemplate.update("DELETE FROM tmdb_provider WHERE provider_id IN (990001, 990002)");
	}

	@AfterEach
	void 시험_행을_지운다() {
		jdbcTemplate.update("DELETE FROM tmdb_provider WHERE provider_id IN (990001, 990002)");
	}

	private void insertProvider(int providerId, String mappingState) {
		jdbcTemplate.update("INSERT INTO tmdb_provider (provider_id, tmdb_name, mapping_state, tier, first_seen_at) "
				+ "VALUES (?, 'Test Provider', ?, 'STANDARD', now())", providerId, mappingState);
	}

	@Test
	void 미매핑이_맨_앞에_오고_매핑하면_재조회에_반영된다() throws Exception {
		// given
		insertProvider(990001, "UNMAPPED");
		insertProvider(990002, "IGNORED");

		// when
		// then
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.providers[0].providerId").value(990001))
				.andExpect(jsonPath("$.data.providers[0].mappingState").value("UNMAPPED"))
				.andExpect(jsonPath("$.data.providers[0].ottServiceId").doesNotExist());

		Long ottServiceId = jdbcTemplate.queryForObject("SELECT min(id) FROM ott_service", Long.class);
		mockMvc.perform(patch(URL + "/990001").header(HttpHeaders.AUTHORIZATION, adminBearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"mappingState\":\"MAPPED\",\"ottServiceId\":" + ottServiceId + ",\"tier\":\"AD\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mappingState").value("MAPPED"))
				.andExpect(jsonPath("$.data.ottServiceId").value(ottServiceId))
				.andExpect(jsonPath("$.data.tier").value("AD"));

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, adminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.providers[?(@.providerId == 990001)].mappingState").value("MAPPED"))
				.andExpect(jsonPath("$.data.providers[?(@.providerId == 990001)].ottServiceId").value(ottServiceId.intValue()))
				.andExpect(jsonPath("$.data.providers[?(@.providerId == 990002)].mappingState").value("IGNORED"));
	}

	@Test
	void MAPPED인데_ottServiceId가_없으면_400_VALIDATION_FAILED다() throws Exception {
		// given
		insertProvider(990001, "UNMAPPED");

		// when
		// then
		mockMvc.perform(patch(URL + "/990001").header(HttpHeaders.AUTHORIZATION, adminBearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"mappingState\":\"MAPPED\",\"ottServiceId\":null,\"tier\":\"STANDARD\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("ottServiceId"));
	}

	@Test
	void 없는_providerId면_404_RESOURCE_NOT_FOUND다() throws Exception {
		// given
		// when
		// then
		mockMvc.perform(patch(URL + "/990009").header(HttpHeaders.AUTHORIZATION, adminBearer()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"mappingState\":\"IGNORED\",\"ottServiceId\":null,\"tier\":\"STANDARD\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
	}
}
