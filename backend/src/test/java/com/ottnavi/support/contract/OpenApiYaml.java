package com.ottnavi.support.contract;

import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** OpenAPI YAML 문자열을 Map 트리로 읽는다(SafeConstructor라 임의 클래스 생성 없음) */
public final class OpenApiYaml {

	private OpenApiYaml() {
	}

	/** YAML 문자열을 Map으로 파싱한다 */
	public static Map<String, Object> parse(String yaml) {
		Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
		if (!(loaded instanceof Map<?, ?>)) {
			throw new IllegalArgumentException("OpenAPI 문서의 최상위가 객체가 아니다");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> document = (Map<String, Object>) loaded;
		return document;
	}
}
