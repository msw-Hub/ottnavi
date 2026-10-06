package com.ottnavi.support.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** OpenAPI YAML 문자열을 Map 트리로 읽는다(SafeConstructor라 임의 클래스 생성 없음) */
public final class OpenApiYaml {

	private OpenApiYaml() {
	}

	/** YAML 문자열을 Map으로 파싱한다. 모든 Map 키는 문자열로 정규화한다 */
	public static Map<String, Object> parse(String yaml) {
		Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
		if (!(loaded instanceof Map<?, ?>)) {
			throw new IllegalArgumentException("OpenAPI 문서의 최상위가 객체가 아니다");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> document = (Map<String, Object>) normalizeKeys(loaded);
		return document;
	}

	/**
	 * 따옴표 없는 숫자 키(예: `200:`)는 SnakeYAML이 Integer로 읽는다. 생성 명세(JSON 유래)의 키는 항상 문자열이라
	 * 조회 시 키 타입이 어긋나지 않도록 트리 전체의 Map 키를 문자열로 맞춘다
	 */
	private static Object normalizeKeys(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> normalized = new LinkedHashMap<>();
			map.forEach((key, child) -> normalized.put(String.valueOf(key), normalizeKeys(child)));
			return normalized;
		}
		if (value instanceof List<?> list) {
			List<Object> normalized = new ArrayList<>();
			list.forEach(child -> normalized.add(normalizeKeys(child)));
			return normalized;
		}
		return value;
	}
}
