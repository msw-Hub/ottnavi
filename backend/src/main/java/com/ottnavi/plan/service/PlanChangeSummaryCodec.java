package com.ottnavi.plan.service;

import com.ottnavi.plan.domain.rule.PlanChangeSummary;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 변경 요약(PlanChangeSummary)과 plan.change_summary(TEXT) 사이의 JSON 변환. Jackson 3(tools.jackson)을 쓴다.
 * JSON에는 형식 버전 v가 들어 있어 형식이 바뀌어도 옛 기록을 구분할 수 있다.
 *
 * <pre>
 * {"v":1,"summary":"11월 구독이 …","reasons":[{"reasonType":"PRICE_CHANGED","description":"…","watchUnitId":null,"ottServiceId":5}],
 *  "monthChanges":[{"month":"2026-11","isConfirmed":true,…}],"titleChanges":[…]}
 * </pre>
 */
@Slf4j
@Component
public class PlanChangeSummaryCodec {

	// 알 수 없는 필드는 무시한다(나중 버전이 필드를 더해도 옛 코드가 읽을 수 있게)
	private final ObjectMapper mapper = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	/** 변경 요약을 저장용 JSON 문자열로 바꾼다 */
	public String write(PlanChangeSummary summary) {
		return mapper.writeValueAsString(summary);
	}

	/**
	 * 저장된 JSON을 변경 요약으로 읽는다. 비어 있거나 알 수 없는 버전이거나 깨진 JSON이면 빈 값이다.
	 * 한 건이 깨졌다고 목록 조회 전체가 실패하지 않게 예외 대신 로그를 남기고 빈 값을 돌려준다.
	 */
	public Optional<PlanChangeSummary> read(String json) {
		// 1. 값이 없으면 기록이 없는 플랜이다
		if (json == null || json.isBlank()) {
			return Optional.empty();
		}
		try {
			// 2. 먼저 버전만 보고, 지원하지 않는 버전은 읽지 않는다
			JsonNode root = mapper.readTree(json);
			int version = root.path("v").asInt(-1);
			if (version != PlanChangeSummary.CURRENT_VERSION) {
				log.warn("지원하지 않는 변경 요약 버전이다: v={}", version);
				return Optional.empty();
			}

			// 3. 현재 버전이면 값으로 읽는다
			return Optional.of(mapper.treeToValue(root, PlanChangeSummary.class));
		} catch (JacksonException e) {
			log.error("변경 요약 JSON을 읽지 못했다", e);
			return Optional.empty();
		}
	}
}
