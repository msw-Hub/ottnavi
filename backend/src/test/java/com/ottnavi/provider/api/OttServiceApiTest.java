package com.ottnavi.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.OriginSecretFilter;
import com.ottnavi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서비스 목록 API와 오리진 비밀 헤더 필터(T-8 ①), 기본 no-store(T-8 ②)를 검증한다(Task 019 V-B).
 * 테스트도 기본 local 프로필이라 필터가 꺼져 있으므로 이 클래스에서만 켠다(컨텍스트가 따로 뜬다).
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
		"app.origin-secret.enabled=true",
		"app.origin-secret.value=" + OttServiceApiTest.SECRET})
class OttServiceApiTest extends IntegrationTestSupport {

	static final String SECRET = "test-origin-secret";
	private static final String URL = "/api/public/ott-services";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 올바른_헤더면_서비스_7개를_정렬_순서대로_반환한다() throws Exception {
		mockMvc.perform(get(URL).header(OriginSecretFilter.HEADER_NAME, SECRET))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(7))
				.andExpect(jsonPath("$.data[*].code").value(contains(
						"NETFLIX", "DISNEY_PLUS", "APPLE_TV_PLUS", "TVING", "WAVVE", "WATCHA", "COUPANG_PLAY")))
				.andExpect(jsonPath("$.data[0].ottServiceId").isNumber())
				.andExpect(jsonPath("$.data[0].name").value("넷플릭스"))
				.andExpect(jsonPath("$.data[0].logoPath").value("/rK1KljqmbvO9HQa1PBFLILWah72.png"))
				.andExpect(jsonPath("$.data[0].displayOrder").value(1))
				.andExpect(jsonPath("$.data[0].dataQuality").value("NORMAL"))
				.andExpect(jsonPath("$.data[6].dataQuality").value("INSUFFICIENT"));
	}

	@Test
	void API_응답에는_no_store_캐시_헤더가_붙는다() throws Exception {
		mockMvc.perform(get(URL).header(OriginSecretFilter.HEADER_NAME, SECRET))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
	}

	@Test
	void 오리진_비밀_헤더가_없으면_403_FORBIDDEN이다() throws Exception {
		mockMvc.perform(get(URL))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
				.andExpect(jsonPath("$.instance").value(URL));
	}

	@Test
	void 오리진_비밀_헤더가_다르면_403_FORBIDDEN이다() throws Exception {
		mockMvc.perform(get(URL).header(OriginSecretFilter.HEADER_NAME, SECRET + "x"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		mockMvc.perform(get(URL).header(OriginSecretFilter.HEADER_NAME, ""))
				.andExpect(status().isForbidden());
	}

	@Test
	void api_밖의_헬스체크는_헤더_없이_통과한다() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@Test
	void 진입_요청의_IP_헤더를_로그로_남긴다(CapturedOutput output) throws Exception {
		mockMvc.perform(get(URL)
						.header(OriginSecretFilter.HEADER_NAME, SECRET)
						.header("x-real-ip", "1.2.3.4")
						.header("x-forwarded-for", "5.6.7.8, 9.9.9.9")
						.header("x-forwarded-host", "ottnavi.example"))
				.andExpect(status().isOk());

		// x-real-ip는 원본, x-forwarded-for·host는 ForwardedHeaderFilter가 반영한 remoteAddr(첫 값)·serverName으로 남는다
		assertThat(output.getOut())
				.contains("x-real-ip=1.2.3.4")
				.contains("remoteAddr=5.6.7.8")
				.contains("serverName=ottnavi.example");
	}
}
