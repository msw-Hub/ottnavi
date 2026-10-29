package com.ottnavi.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.security.ProblemDetailAccessDeniedHandler;
import com.ottnavi.global.security.ProblemDetailAuthenticationEntryPoint;
import com.ottnavi.global.security.SecurityConfig;
import com.ottnavi.support.OAuth2LoginTestConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외 유형별 ProblemDetail 응답을 검증한다(V-B).
 * 테스트 전용 컨트롤러는 중첩 클래스로 두어 @SpringBootTest 컴포넌트 스캔(계약 대조)에 섞이지 않게 한다.
 */
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorTestController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
		OAuth2LoginTestConfig.class,
		GlobalExceptionHandlerTest.ErrorTestController.class, GlobalExceptionHandlerTest.TestSecurityChain.class})
class GlobalExceptionHandlerTest {

	private static final String BASE = "/test/errors";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 정상_응답은_CommonResponse로_감싼다() throws Exception {
		mockMvc.perform(get(BASE + "/ok"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("기생충"));
	}

	@Test
	void 도메인_예외는_ErrorCode_상태와_예외_메시지로_응답한다() throws Exception {
		expectProblem(mockMvc.perform(get(BASE + "/business")), 404, "TITLE_NOT_FOUND")
				.andExpect(jsonPath("$.title").value("Not Found"))
				.andExpect(jsonPath("$.detail").value("작품을 찾을 수 없습니다: movie/999"))
				.andExpect(jsonPath("$.instance").value(BASE + "/business"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void 본문_검증_실패는_VALIDATION_FAILED와_필드_오류_목록을_준다() throws Exception {
		ResultActions result = mockMvc.perform(post(BASE + "/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"monthlyBudget\":-1}")); // name 누락(@NotBlank만 위반), 예산 음수

		expectProblem(result, 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.instance").value(BASE + "/body"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(2)))
				.andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("name", "monthlyBudget")))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'monthlyBudget')].message").value("0 이상이어야 합니다."));
	}

	@Test
	void 한_필드가_제약_두_개를_어기면_같은_필드로_두_건이_온다() throws Exception {
		ResultActions result = mockMvc.perform(post(BASE + "/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"!\",\"monthlyBudget\":1000}"));

		expectProblem(result, 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.fieldErrors", hasSize(2)))
				.andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("name", "name")))
				.andExpect(jsonPath("$.fieldErrors[*].message",
						containsInAnyOrder("이름은 2자 이상이어야 합니다.", "이름은 한글·영문만 쓸 수 있습니다.")));
	}

	@Test
	void 쿼리_파라미터_제약_위반은_파라미터명으로_필드_오류를_준다() throws Exception {
		expectProblem(mockMvc.perform(get(BASE + "/param").param("size", "0")), 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("1 이상이어야 합니다."));
	}

	@Test
	void 필수_쿼리_파라미터_누락은_VALIDATION_FAILED다() throws Exception {
		expectProblem(mockMvc.perform(get(BASE + "/param")), 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("필수 값입니다."));
	}

	@Test
	void 경로_변수_타입_불일치는_VALIDATION_FAILED다() throws Exception {
		expectProblem(mockMvc.perform(get(BASE + "/items/abc")), 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.fieldErrors[0].field").value("itemId"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("형식이 올바르지 않습니다."));
	}

	@Test
	void 깨진_JSON_본문은_INVALID_REQUEST이고_파서_메시지를_노출하지_않는다() throws Exception {
		ResultActions result = mockMvc.perform(post(BASE + "/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":"));

		expectProblem(result, 400, "INVALID_REQUEST")
				.andExpect(jsonPath("$.detail").value(ErrorCode.INVALID_REQUEST.getMessage()))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void 본문의_날짜_형식이_틀리면_VALIDATION_FAILED와_해당_필드를_준다() throws Exception {
		// given
		String content = "{\"targetDate\":\"2026-13-45\",\"tier\":\"ALL\"}";

		// when
		ResultActions result = mockMvc.perform(post(BASE + "/date-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content(content));

		// then
		expectProblem(result, 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("targetDate"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("형식이 올바르지 않습니다."));
	}

	@Test
	void 본문의_enum에_모르는_값이_오면_해당_필드_오류를_준다() throws Exception {
		// given
		String content = "{\"targetDate\":\"2026-10-01\",\"tier\":\"UNKNOWN\"}";

		// when
		ResultActions result = mockMvc.perform(post(BASE + "/date-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content(content));

		// then
		expectProblem(result, 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.fieldErrors[0].field").value("tier"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("형식이 올바르지 않습니다."));
	}

	@Test
	void 깨진_JSON은_필드_오류_없이_INVALID_REQUEST다() throws Exception {
		// given
		String content = "{\"targetDate\":";

		// when
		ResultActions result = mockMvc.perform(post(BASE + "/date-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content(content));

		// then
		expectProblem(result, 400, "INVALID_REQUEST")
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void 중첩_객체와_배열_경로는_점과_대괄호_표기로_필드명이_된다() throws Exception {
		// given
		String content = "{\"items\":[{\"targetDate\":\"2026-10-01\"},{\"targetDate\":\"2026-13-45\"}]}";

		// when
		ResultActions result = mockMvc.perform(post(BASE + "/nested-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content(content));

		// then
		expectProblem(result, 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("items[1].targetDate"));
	}

	@Test
	void 본문_전체가_객체가_아니어서_경로가_비면_INVALID_REQUEST다() throws Exception {
		// given
		String content = "[1, 2]";

		// when
		ResultActions result = mockMvc.perform(post(BASE + "/date-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content(content));

		// then
		expectProblem(result, 400, "INVALID_REQUEST")
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void FieldValidationException은_지정한_필드와_메시지로_400을_준다() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(BASE + "/field-validation"));

		// then
		expectProblem(result, 400, "VALIDATION_FAILED")
				.andExpect(jsonPath("$.detail").value(ErrorCode.VALIDATION_FAILED.getMessage()))
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("targetDate"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("미래 날짜는 쓸 수 없습니다."));
	}

	@Test
	@WithMockUser // /api/**는 로그인이 필요해 인증된 사용자로 호출해야 404까지 간다
	void 없는_경로는_RESOURCE_NOT_FOUND다() throws Exception {
		expectProblem(mockMvc.perform(get("/api/does-not-exist")), 404, "RESOURCE_NOT_FOUND")
				.andExpect(jsonPath("$.instance").value("/api/does-not-exist"));
	}

	@Test
	void 지원하지_않는_메서드는_METHOD_NOT_ALLOWED와_Allow_헤더를_준다() throws Exception {
		expectProblem(mockMvc.perform(post(BASE + "/ok")), 405, "METHOD_NOT_ALLOWED")
				.andExpect(header().string("Allow", containsString("GET")));
	}

	@Test
	void 지원하지_않는_Content_Type은_원래_상태_415와_INVALID_REQUEST다() throws Exception {
		ResultActions result = mockMvc.perform(post(BASE + "/body")
				.contentType(MediaType.TEXT_PLAIN)
				.content("name"));

		expectProblem(result, 415, "INVALID_REQUEST");
	}

	@Test
	void 예상하지_못한_예외는_INTERNAL_ERROR이고_내부_메시지를_노출하지_않는다() throws Exception {
		expectProblem(mockMvc.perform(get(BASE + "/runtime")), 500, "INTERNAL_ERROR")
				.andExpect(jsonPath("$.detail").value(ErrorCode.INTERNAL_ERROR.getMessage()))
				.andExpect(content().string(not(containsString("내부 비밀"))));
	}

	@Test
	void 인증_실패_401은_WARN_로그를_남기지_않는다(CapturedOutput output) throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(BASE + "/unauthorized"));

		// then
		expectProblem(result, 401, "UNAUTHORIZED");
		assertThat(output.getAll()).doesNotContain("요청 처리 실패");
	}

	@Test
	void 권한_부족_403은_WARN_로그를_남긴다(CapturedOutput output) throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(BASE + "/forbidden"));

		// then
		expectProblem(result, 403, "FORBIDDEN");
		assertThat(output.getAll()).contains("요청 처리 실패").contains("errorCode=FORBIDDEN");
	}

	@Test
	void 요청_제한_초과는_429_RATE_LIMIT_EXCEEDED와_Retry_After_헤더를_준다() throws Exception {
		expectProblem(mockMvc.perform(get(BASE + "/rate-limit")), 429, "RATE_LIMIT_EXCEEDED")
				.andExpect(header().string("Retry-After", "7"))
				.andExpect(jsonPath("$.detail").value(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()));
	}

	/** 공통 ProblemDetail 형식(상태, problem+json, errorCode)을 검사한다 */
	private ResultActions expectProblem(ResultActions result, int status, String errorCode) throws Exception {
		return result
				.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.errorCode").value(errorCode));
	}

	// 테스트 전용 요청·응답·예외·컨트롤러

	record TestRequest(
			@NotBlank(message = "이름은 필수입니다.")
			@Size(min = 2, message = "이름은 2자 이상이어야 합니다.")
			@Pattern(regexp = "[가-힣a-zA-Z]*", message = "이름은 한글·영문만 쓸 수 있습니다.")
			String name,
			@NotNull(message = "예산은 필수입니다.")
			@Min(value = 0, message = "0 이상이어야 합니다.")
			Integer monthlyBudget) {
	}

	record TestResponse(String name) {
	}

	enum SampleEnum {
		ALL
	}

	record DateBody(LocalDate targetDate, SampleEnum tier) {
	}

	record NestedItem(LocalDate targetDate) {
	}

	record NestedBody(List<NestedItem> items) {
	}

	/** 예외 변환 시험 경로(/test/errors/**)는 인가 규칙이 아니라 핸들러를 보려는 것이라 운영 체인보다 먼저 전부 허용한다 */
	@TestConfiguration
	static class TestSecurityChain {

		@Bean
		@Order(0)
		SecurityFilterChain testErrorsChain(HttpSecurity http) throws Exception {
			http.securityMatcher(BASE + "/**")
					.csrf(csrf -> csrf.disable())
					.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
			return http.build();
		}
	}

	static class TestTitleNotFoundException extends BusinessException {

		TestTitleNotFoundException(String message) {
			super(message);
		}

		@Override
		public ErrorCode getErrorCode() {
			return ErrorCode.TITLE_NOT_FOUND;
		}
	}

	@RestController
	@RequestMapping(BASE)
	static class ErrorTestController {

		@GetMapping("/ok")
		CommonResponse<TestResponse> ok() {
			return CommonResponse.success(new TestResponse("기생충"));
		}

		@GetMapping("/business")
		CommonResponse<TestResponse> business() {
			throw new TestTitleNotFoundException("작품을 찾을 수 없습니다: movie/999");
		}

		@GetMapping("/rate-limit")
		CommonResponse<TestResponse> rateLimit() {
			throw new RateLimitExceededException("요청 제한 초과", 7);
		}

		@PostMapping("/body")
		CommonResponse<TestResponse> body(@Valid @RequestBody TestRequest request) {
			return CommonResponse.success(new TestResponse(request.name()));
		}

		@GetMapping("/param")
		CommonResponse<Integer> param(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") int size) {
			return CommonResponse.success(size);
		}

		@GetMapping("/items/{itemId}")
		CommonResponse<Long> item(@PathVariable Long itemId) {
			return CommonResponse.success(itemId);
		}

		@PostMapping("/date-body")
		CommonResponse<TestResponse> dateBody(@RequestBody DateBody request) {
			return CommonResponse.success(new TestResponse(String.valueOf(request.targetDate())));
		}

		@PostMapping("/nested-body")
		CommonResponse<TestResponse> nestedBody(@RequestBody NestedBody request) {
			return CommonResponse.success(new TestResponse("ok"));
		}

		@GetMapping("/field-validation")
		CommonResponse<TestResponse> fieldValidation() {
			throw new FieldValidationException("targetDate", "미래 날짜는 쓸 수 없습니다.");
		}

		@GetMapping("/unauthorized")
		CommonResponse<TestResponse> unauthorized() {
			throw new UnauthorizedException("인증이 필요합니다.");
		}

		@GetMapping("/forbidden")
		CommonResponse<TestResponse> forbidden() {
			throw new ForbiddenException("권한이 없습니다.");
		}

		@GetMapping("/runtime")
		CommonResponse<TestResponse> runtime() {
			throw new IllegalStateException("내부 비밀 메시지");
		}
	}
}
