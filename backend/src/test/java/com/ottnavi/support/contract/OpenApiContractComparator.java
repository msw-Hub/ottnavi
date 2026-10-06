package com.ottnavi.support.contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 계약(docs/api/openapi.yaml)과 springdoc 생성 명세를 비교해 차이 목록을 만든다(TECH T-5).
 *
 * <ul>
 *   <li>비교: operation 존재, operationId, tags, 파라미터, 요청 본문, 2xx 응답의 스키마(type·format·nullable·enum·required·properties·items)</li>
 *   <li>제외: openapi 버전, info, servers, example, description, 4xx·5xx 응답(springdoc이 ControllerAdvice 응답을 자동으로 붙이므로)</li>
 *   <li>스키마는 이름이 아니라 $ref를 풀어 구조로 비교한다(springdoc 제네릭 이름과 계약 이름이 달라도 된다)</li>
 *   <li>계약 path는 servers[0].url(/api)을 뺀 상대 경로이므로 생성 쪽 path에서 그 접두사를 떼고 비교한다</li>
 *   <li>계약에서 x-planned: true인 operation은 제외하고, 생성 쪽에 이미 있으면 표시를 지우라고 실패시킨다</li>
 * </ul>
 */
public final class OpenApiContractComparator {

	public static final String X_PLANNED = "x-planned";
	private static final Set<String> HTTP_METHODS =
			Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

	private final Map<String, Object> contract;
	private final Map<String, Object> generated;
	private final List<String> differences = new ArrayList<>();

	private OpenApiContractComparator(Map<String, Object> contract, Map<String, Object> generated) {
		this.contract = contract;
		this.generated = generated;
	}

	/** 두 명세의 차이 목록을 반환한다. 비어 있으면 일치한다 */
	public static List<String> compare(Map<String, Object> contract, Map<String, Object> generated) {
		OpenApiContractComparator comparator = new OpenApiContractComparator(contract, generated);
		comparator.compareOperations();
		return comparator.differences;
	}

	// operation 단위 비교

	private void compareOperations() {
		String prefix = contractPathPrefix();
		Map<String, Map<String, Object>> contractOperations = new LinkedHashMap<>();
		Set<String> plannedOperations = new HashSet<>();
		collectOperations(contract, "").forEach((key, operation) -> {
			if (Boolean.TRUE.equals(operation.get(X_PLANNED))) {
				plannedOperations.add(key);
			} else {
				contractOperations.put(key, operation);
			}
		});
		Map<String, Map<String, Object>> generatedOperations = collectOperations(generated, prefix);

		generatedOperations.forEach((key, operation) -> {
			if (plannedOperations.contains(key)) {
				differences.add("[" + key + "] 구현됐는데 계약에 x-planned: true가 남아 있다. 표시를 지운다");
			} else if (!contractOperations.containsKey(key)) {
				differences.add("[" + key + "] 계약에 없는 operation이 구현돼 있다");
			}
		});
		contractOperations.forEach((key, operation) -> {
			Map<String, Object> generatedOperation = generatedOperations.get(key);
			if (generatedOperation == null) {
				differences.add("[" + key + "] 계약에 있지만 구현되지 않았다(미구현이면 x-planned: true를 단다)");
			} else {
				compareOperation(key, operation, generatedOperation);
			}
		});
	}

	/** 계약 servers[0].url이 상대 경로면 그 값을 접두사로 쓴다(예: /api) */
	private String contractPathPrefix() {
		List<Object> servers = list(contract.get("servers"));
		if (servers.isEmpty()) {
			return "";
		}
		Object url = map(servers.get(0)).get("url");
		if (url instanceof String text && text.startsWith("/")) {
			return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
		}
		return "";
	}

	/** "GET /path" 형식 키로 operation을 모은다. 경로 단위 parameters는 operation에 합친다 */
	private Map<String, Map<String, Object>> collectOperations(Map<String, Object> document, String prefix) {
		Map<String, Map<String, Object>> operations = new LinkedHashMap<>();
		map(document.get("paths")).forEach((path, pathItemValue) -> {
			Map<String, Object> pathItem = map(pathItemValue);
			String normalizedPath = stripPrefix(path, prefix);
			pathItem.forEach((method, operationValue) -> {
				if (!HTTP_METHODS.contains(method)) {
					return;
				}
				Map<String, Object> operation = new LinkedHashMap<>(map(operationValue));
				List<Object> parameters = new ArrayList<>(list(pathItem.get("parameters")));
				parameters.addAll(list(operation.get("parameters")));
				operation.put("parameters", parameters);
				operations.put(method.toUpperCase() + " " + normalizedPath, operation);
			});
		});
		return operations;
	}

	private String stripPrefix(String path, String prefix) {
		if (!prefix.isEmpty() && (path.equals(prefix) || path.startsWith(prefix + "/"))) {
			String stripped = path.substring(prefix.length());
			return stripped.isEmpty() ? "/" : stripped;
		}
		return path;
	}

	private void compareOperation(String key, Map<String, Object> contractOp, Map<String, Object> generatedOp) {
		String location = "[" + key + "]";
		compareValue(location + " operationId", contractOp.get("operationId"), generatedOp.get("operationId"));
		compareValue(location + " tags", stringSet(contractOp.get("tags")), stringSet(generatedOp.get("tags")));
		compareParameters(location, contractOp, generatedOp);
		compareRequestBody(location, contractOp.get("requestBody"), generatedOp.get("requestBody"));
		compareSuccessResponses(location, map(contractOp.get("responses")), map(generatedOp.get("responses")));
	}

	private void compareParameters(String location, Map<String, Object> contractOp, Map<String, Object> generatedOp) {
		Map<String, Map<String, Object>> contractParams = parametersByKey(contract, contractOp);
		Map<String, Map<String, Object>> generatedParams = parametersByKey(generated, generatedOp);
		compareValue(location + " 파라미터 목록", new TreeSet<>(contractParams.keySet()), new TreeSet<>(generatedParams.keySet()));
		contractParams.forEach((name, contractParam) -> {
			Map<String, Object> generatedParam = generatedParams.get(name);
			if (generatedParam == null) {
				return;
			}
			String paramLocation = location + " 파라미터 " + name;
			compareValue(paramLocation + " required", bool(contractParam.get("required")), bool(generatedParam.get("required")));
			compareSchema(paramLocation, contractParam.get("schema"), generatedParam.get("schema"), new HashSet<>());
		});
	}

	/** "in:name" 키로 파라미터를 모은다($ref는 components/parameters에서 푼다) */
	private Map<String, Map<String, Object>> parametersByKey(Map<String, Object> document, Map<String, Object> operation) {
		Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();
		for (Object value : list(operation.get("parameters"))) {
			Map<String, Object> parameter = resolve(document, map(value));
			parameters.put(parameter.get("in") + ":" + parameter.get("name"), parameter);
		}
		return parameters;
	}

	private void compareRequestBody(String location, Object contractValue, Object generatedValue) {
		if (contractValue == null || generatedValue == null) {
			compareValue(location + " 요청 본문 존재", contractValue != null, generatedValue != null);
			return;
		}
		Map<String, Object> contractBody = resolve(contract, map(contractValue));
		Map<String, Object> generatedBody = resolve(generated, map(generatedValue));
		compareValue(location + " 요청 본문 required", bool(contractBody.get("required")), bool(generatedBody.get("required")));
		compareContent(location + " 요청 본문", map(contractBody.get("content")), map(generatedBody.get("content")));
	}

	/** 2xx 응답만 비교한다. 오류 응답 형식은 GlobalExceptionHandlerTest가 검증한다 */
	private void compareSuccessResponses(String location, Map<String, Object> contractResponses,
			Map<String, Object> generatedResponses) {
		Set<String> contractCodes = successCodes(contractResponses);
		Set<String> generatedCodes = successCodes(generatedResponses);
		compareValue(location + " 성공 응답 코드", contractCodes, generatedCodes);
		for (String code : contractCodes) {
			if (!generatedCodes.contains(code)) {
				continue;
			}
			Map<String, Object> contractResponse = resolve(contract, map(contractResponses.get(code)));
			Map<String, Object> generatedResponse = resolve(generated, map(generatedResponses.get(code)));
			compareContent(location + " 응답 " + code, map(contractResponse.get("content")), map(generatedResponse.get("content")));
		}
	}

	private Set<String> successCodes(Map<String, Object> responses) {
		return responses.keySet().stream()
				.map(String::valueOf)
				.filter(code -> code.startsWith("2"))
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private void compareContent(String location, Map<String, Object> contractContent, Map<String, Object> generatedContent) {
		compareValue(location + " media type", new TreeSet<>(contractContent.keySet()), new TreeSet<>(generatedContent.keySet()));
		contractContent.forEach((mediaType, contractMedia) -> {
			Object generatedMedia = generatedContent.get(mediaType);
			if (generatedMedia != null) {
				compareSchema(location + " " + mediaType, map(contractMedia).get("schema"), map(generatedMedia).get("schema"),
						new HashSet<>());
			}
		});
	}

	// 스키마 구조 비교

	/** 스키마를 재귀적으로 비교한다. visited는 자기 참조 스키마의 무한 재귀를 막는다 */
	private void compareSchema(String location, Object contractValue, Object generatedValue, Set<String> visited) {
		if (contractValue == null || generatedValue == null) {
			compareValue(location + " 스키마 존재", contractValue != null, generatedValue != null);
			return;
		}
		String visitKey = refName(contractValue) + "|" + refName(generatedValue);
		if (!visitKey.equals("null|null") && !visited.add(visitKey)) {
			return;
		}
		Map<String, Object> contractSchema = normalize(contract, map(contractValue));
		Map<String, Object> generatedSchema = normalize(generated, map(generatedValue));

		compareValue(location + " type", contractSchema.get("type"), generatedSchema.get("type"));
		compareValue(location + " format", contractSchema.get("format"), generatedSchema.get("format"));
		compareValue(location + " nullable", bool(contractSchema.get("nullable")), bool(generatedSchema.get("nullable")));
		compareValue(location + " enum", stringSet(contractSchema.get("enum")), stringSet(generatedSchema.get("enum")));
		compareValue(location + " required", stringSet(contractSchema.get("required")), stringSet(generatedSchema.get("required")));

		Map<String, Object> contractProperties = map(contractSchema.get("properties"));
		Map<String, Object> generatedProperties = map(generatedSchema.get("properties"));
		compareValue(location + " properties", new TreeSet<>(contractProperties.keySet()), new TreeSet<>(generatedProperties.keySet()));
		contractProperties.forEach((name, contractProperty) -> {
			if (generatedProperties.containsKey(name)) {
				compareSchema(location + "." + name, contractProperty, generatedProperties.get(name), new HashSet<>(visited));
			}
		});

		if (contractSchema.containsKey("items") || generatedSchema.containsKey("items")) {
			compareSchema(location + "[]", contractSchema.get("items"), generatedSchema.get("items"), new HashSet<>(visited));
		}
		Object contractAdditional = contractSchema.get("additionalProperties");
		Object generatedAdditional = generatedSchema.get("additionalProperties");
		if (contractAdditional instanceof Map<?, ?> && generatedAdditional instanceof Map<?, ?>) {
			compareSchema(location + "{}", contractAdditional, generatedAdditional, new HashSet<>(visited));
		} else {
			compareValue(location + " additionalProperties", contractAdditional != null, generatedAdditional != null);
		}
	}

	/** $ref를 풀고 allOf를 하나로 합친다. properties만 있고 type이 없으면 object로 본다 */
	private Map<String, Object> normalize(Map<String, Object> document, Map<String, Object> schema) {
		Map<String, Object> resolved = new LinkedHashMap<>(resolve(document, schema));
		List<Object> allOf = list(resolved.remove("allOf"));
		if (!allOf.isEmpty()) {
			Map<String, Object> properties = new LinkedHashMap<>(map(resolved.get("properties")));
			Set<String> required = new LinkedHashSet<>(stringSet(resolved.get("required")));
			for (Object part : allOf) {
				Map<String, Object> partSchema = normalize(document, map(part));
				properties.putAll(map(partSchema.get("properties")));
				required.addAll(stringSet(partSchema.get("required")));
				partSchema.forEach((key, value) -> {
					if (!key.equals("properties") && !key.equals("required")) {
						resolved.putIfAbsent(key, value);
					}
				});
			}
			resolved.put("properties", properties);
			resolved.put("required", new ArrayList<>(required));
		}
		if (!resolved.containsKey("type") && resolved.containsKey("properties")) {
			resolved.put("type", "object");
		}
		return resolved;
	}

	/** "#/components/{종류}/{이름}" 형식 $ref를 같은 문서 안에서 푼다(연쇄 $ref 포함) */
	private Map<String, Object> resolve(Map<String, Object> document, Map<String, Object> node) {
		Map<String, Object> current = node;
		int guard = 0;
		while (current.get("$ref") instanceof String ref && guard++ < 20) {
			if (!ref.startsWith("#/")) {
				throw new IllegalArgumentException("외부 $ref는 지원하지 않는다: " + ref);
			}
			Object target = document;
			for (String segment : ref.substring(2).split("/")) {
				target = map(target).get(segment.replace("~1", "/").replace("~0", "~"));
			}
			if (target == null) {
				differences.add("$ref 대상을 찾을 수 없다: " + ref);
				return Map.of();
			}
			current = map(target);
		}
		return current;
	}

	private String refName(Object schema) {
		Object ref = map(schema).get("$ref");
		return ref == null ? "null" : ref.toString();
	}

	// 값 도우미

	private void compareValue(String location, Object contractValue, Object generatedValue) {
		if (!Objects.equals(contractValue, generatedValue)) {
			differences.add(location + " 다름: 계약=" + contractValue + ", 생성=" + generatedValue);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return value instanceof Map<?, ?> mapValue ? (Map<String, Object>) mapValue : Map.of();
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(Object value) {
		return value instanceof List<?> listValue ? (List<Object>) listValue : List.of();
	}

	private static boolean bool(Object value) {
		return Boolean.TRUE.equals(value);
	}

	/** 순서와 값 타입(문자열·숫자)에 상관없이 비교하려고 문자열 집합으로 바꾼다 */
	private static Set<String> stringSet(Object value) {
		if (!(value instanceof Collection<?> values)) {
			return Set.of();
		}
		return values.stream().map(String::valueOf).collect(Collectors.toCollection(TreeSet::new));
	}
}
