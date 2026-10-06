package com.ottnavi.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.support.ApiDocs;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.contract.OpenApiContractComparator;
import com.ottnavi.support.contract.OpenApiYaml;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * springdoc이 생성한 명세(/v3/api-docs.yaml)와 계약(docs/api/openapi.yaml)을 대조한다(TECH T-5, CI 필수).
 * 실패하면 차이 목록을 보고 계약과 코드 중 맞는 쪽을 고친다. 비교 규칙은 OpenApiContractComparator 참고.
 */
@AutoConfigureMockMvc
class OpenApiContractTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 생성된_명세가_계약과_일치한다() throws Exception {
		String generatedYaml = mockMvc.perform(get("/v3/api-docs.yaml"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		Map<String, Object> generated = OpenApiYaml.parse(generatedYaml);
		Map<String, Object> contract = OpenApiYaml.parse(Files.readString(ApiDocs.resolve("openapi.yaml")));

		// 계약이 3.0.x라 생성 쪽도 3.0으로 맞춘다(springdoc.api-docs.version 설정이 빠지면 3.1.0이 나온다)
		assertThat(String.valueOf(generated.get("openapi"))).startsWith("3.0");
		List<String> differences = OpenApiContractComparator.compare(contract, generated);
		assertThat(differences)
				.as("계약과 구현의 차이:%n%s", String.join(System.lineSeparator(), differences))
				.isEmpty();
	}
}
