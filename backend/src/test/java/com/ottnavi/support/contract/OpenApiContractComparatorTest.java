package com.ottnavi.support.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 계약 대조 비교기 단위 테스트. 실제 openapi.yaml은 건드리지 않고 아래 텍스트 블록 픽스처로 검증한다.
 * 기준 픽스처: 구현된 operation 1개(listOttServices) + x-planned operation 1개(getTitleDetail).
 */
class OpenApiContractComparatorTest {

	private static final String OTT_SERVICES = "/public/ott-services";
	private static final String TITLE_DETAIL = "/public/titles/{mediaType}/{tmdbId}";

	// 계약: 3.0.3, servers /api 기준 상대 경로, 이름 붙은 스키마 $ref, allOf 사용
	private static final String CONTRACT = """
			openapi: 3.0.3
			info: { title: OTT내비 API, version: 0.1.0 }
			servers:
			  - url: /api
			paths:
			  /public/ott-services:
			    get:
			      operationId: listOttServices
			      tags: [product]
			      description: 서비스 목록
			      parameters:
			        - $ref: '#/components/parameters/PageParam'
			      responses:
			        '200':
			          description: 성공
			          content:
			            application/json:
			              schema:
			                $ref: '#/components/schemas/CommonResponseOttServiceList'
			        '400':
			          $ref: '#/components/responses/BadRequest'
			  /public/titles/{mediaType}/{tmdbId}:
			    get:
			      operationId: getTitleDetail
			      x-planned: true
			      tags: [catalog]
			      responses:
			        '200':
			          description: 성공
			components:
			  parameters:
			    PageParam:
			      name: page
			      in: query
			      required: false
			      schema: { type: integer, format: int32, minimum: 0 }
			  responses:
			    BadRequest:
			      description: 오류
			  schemas:
			    CommonResponseOttServiceList:
			      type: object
			      required: [success, data]
			      properties:
			        success: { type: boolean }
			        data:
			          type: array
			          items:
			            $ref: '#/components/schemas/OttService'
			    OttService:
			      allOf:
			        - $ref: '#/components/schemas/OttServiceBase'
			        - type: object
			          required: [serviceCode]
			          properties:
			            serviceCode:
			              type: string
			              enum: [NETFLIX, TVING, WAVVE]
			      example: { serviceCode: NETFLIX, name: 넷플릭스 }
			    OttServiceBase:
			      type: object
			      required: [name]
			      properties:
			        name: { type: string, description: 서비스 이름 }
			        monthlyPrice: { type: integer, format: int32, nullable: true }
			""";

	// springdoc 생성 결과 흉내: 3.0.1, 절대 servers, /api 접두사, 제네릭 이름, 평면 구조, 오류 응답 자동 추가
	private static final String GENERATED = """
			openapi: 3.0.1
			info: { title: OpenAPI definition, version: v0 }
			servers:
			  - url: http://localhost
			    description: Generated server url
			paths:
			  /api/public/ott-services:
			    get:
			      operationId: listOttServices
			      tags: [product]
			      parameters:
			        - name: page
			          in: query
			          required: false
			          schema: { type: integer, format: int32 }
			      responses:
			        '200':
			          description: OK
			          content:
			            application/json:
			              schema:
			                $ref: '#/components/schemas/CommonResponseListOttServiceResponse'
			        '500':
			          description: Internal Server Error
			          content:
			            '*/*':
			              schema:
			                $ref: '#/components/schemas/ProblemDetail'
			components:
			  schemas:
			    CommonResponseListOttServiceResponse:
			      type: object
			      required: [data, success]
			      properties:
			        success: { type: boolean }
			        data:
			          type: array
			          items:
			            $ref: '#/components/schemas/OttServiceResponse'
			    OttServiceResponse:
			      type: object
			      required: [name, serviceCode]
			      properties:
			        serviceCode:
			          type: string
			          enum: [WAVVE, NETFLIX, TVING]
			        name: { type: string, example: 넷플릭스 }
			        monthlyPrice: { type: integer, format: int32, nullable: true }
			    ProblemDetail:
			      type: object
			      properties:
			        title: { type: string }
			""";

	@Test
	void x_planned_operation은_구현되지_않아도_비교에서_제외된다() {
		// 기준 픽스처: 계약의 getTitleDetail(x-planned)은 생성 쪽에 없다.
		// 접두사·스키마 이름·allOf·enum 순서·버전·servers·example·오류 응답 차이도 모두 무시돼야 한다
		assertThat(compare(contract(), generated())).isEmpty();
	}

	@Test
	void x_planned_표시가_없는데_구현되지_않으면_미구현으로_실패한다() {
		Map<String, Object> contract = contract();
		operation(contract, TITLE_DETAIL).remove(OpenApiContractComparator.X_PLANNED);

		assertThat(compare(contract, generated()))
				.singleElement().asString().contains("GET " + TITLE_DETAIL).contains("구현되지 않았다");
	}

	@Test
	void x_planned인데_이미_구현돼_있으면_표시를_지우라고_실패한다() {
		Map<String, Object> generated = generated();
		paths(generated).put("/api" + TITLE_DETAIL, Map.of("get", Map.of("operationId", "getTitleDetail")));

		assertThat(compare(contract(), generated))
				.singleElement().asString().contains("x-planned");
	}

	@Test
	void 계약에_없는_operation이_구현돼_있으면_실패한다() {
		Map<String, Object> generated = generated();
		paths(generated).put("/api/test/errors/ok", Map.of("get", Map.of("operationId", "ok")));

		assertThat(compare(contract(), generated))
				.singleElement().asString().contains("GET /test/errors/ok").contains("계약에 없는");
	}

	@Test
	void operationId가_다르면_실패한다() {
		Map<String, Object> generated = generated();
		operation(generated, "/api" + OTT_SERVICES).put("operationId", "getOttServices");

		assertThat(compare(contract(), generated)).singleElement().asString().contains("operationId");
	}

	@Test
	void tags가_다르면_실패한다() {
		Map<String, Object> generated = generated();
		operation(generated, "/api" + OTT_SERVICES).put("tags", List.of("ott-service-controller"));

		assertThat(compare(contract(), generated)).singleElement().asString().contains("tags");
	}

	@Test
	void required가_다르면_실패한다() {
		Map<String, Object> generated = generated();
		schema(generated, "OttServiceResponse").put("required", List.of("serviceCode"));

		assertThat(compare(contract(), generated)).singleElement().asString().contains("required");
	}

	@Test
	void enum_값이_다르면_실패한다() {
		Map<String, Object> generated = generated();
		property(generated, "OttServiceResponse", "serviceCode").put("enum", List.of("NETFLIX", "TVING"));

		assertThat(compare(contract(), generated)).singleElement().asString().contains("serviceCode enum");
	}

	@Test
	void nullable이_다르면_실패한다() {
		Map<String, Object> generated = generated();
		property(generated, "OttServiceResponse", "monthlyPrice").remove("nullable");

		assertThat(compare(contract(), generated)).singleElement().asString().contains("monthlyPrice nullable");
	}

	@Test
	void 필드_타입이나_이름이_다르면_실패한다() {
		Map<String, Object> generated = generated();
		property(generated, "OttServiceResponse", "monthlyPrice").put("format", "int64");
		Map<String, Object> properties = map(schema(generated, "OttServiceResponse").get("properties"));
		properties.put("serviceName", properties.remove("name"));

		assertThat(compare(contract(), generated))
				.anySatisfy(diff -> assertThat(diff).contains("monthlyPrice format"))
				.anySatisfy(diff -> assertThat(diff).contains("properties"));
	}

	@Test
	void 응답_media_type이_다르면_실패한다() {
		Map<String, Object> generated = generated();
		Map<String, Object> content = map(map(map(operation(generated, "/api" + OTT_SERVICES).get("responses")).get("200"))
				.get("content"));
		content.put("*/*", content.remove("application/json"));

		assertThat(compare(contract(), generated)).singleElement().asString().contains("media type");
	}

	// 픽스처 도우미 (매번 새로 파싱해 테스트끼리 상태를 공유하지 않는다)

	private static List<String> compare(Map<String, Object> contract, Map<String, Object> generated) {
		return OpenApiContractComparator.compare(contract, generated);
	}

	private static Map<String, Object> contract() {
		return OpenApiYaml.parse(CONTRACT);
	}

	private static Map<String, Object> generated() {
		return OpenApiYaml.parse(GENERATED);
	}

	private static Map<String, Object> paths(Map<String, Object> document) {
		return map(document.get("paths"));
	}

	private static Map<String, Object> operation(Map<String, Object> document, String path) {
		return map(map(paths(document).get(path)).get("get"));
	}

	private static Map<String, Object> schema(Map<String, Object> document, String name) {
		return map(map(map(document.get("components")).get("schemas")).get(name));
	}

	private static Map<String, Object> property(Map<String, Object> document, String schemaName, String propertyName) {
		return map(map(schema(document, schemaName).get("properties")).get(propertyName));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}
}
