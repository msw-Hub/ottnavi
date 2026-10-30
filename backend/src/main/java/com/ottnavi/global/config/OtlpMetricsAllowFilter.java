package com.ottnavi.global.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import java.util.Set;

/**
 * Grafana Cloud로 보낼 지표를 허용 목록으로 거르는 필터다.
 * 허용 이름이 아니면 거부해 시계열 수(무료 한도)를 줄이고, 개인정보 성격의 태그 키가 붙은 지표는 이름이 허용이어도 거부한다.
 *
 * 예: ratelimit.rejected(태그 bucket) → 통과 / llm.call → 통과 / jvm.memory.used → 통과 / app.unlisted.metric → 거부 / ratelimit.rejected(태그 ip) → 거부
 */
public class OtlpMetricsAllowFilter implements MeterFilter {

	// 이름이 정확히 같을 때만 허용하는 지표
	private static final Set<String> ALLOWED_NAMES = Set.of(
			"ottnavi.process.rss",             // 프로세스 RSS 메모리(SystemMemoryMetrics)
			"ottnavi.container.memory.used",   // 컨테이너 메모리 사용량(SystemMemoryMetrics)
			"ratelimit.rejected",              // 요청 제한 거부 횟수
			"ratelimit.store_error",           // 요청 제한 저장소(Redis) 오류 횟수
			"process.cpu.usage",
			"system.cpu.usage",
			"http.server.requests",            // uri 태그는 경로 템플릿
			"tmdb.call",
			"plan.calculate",
			"notification.send",
			"llm.call",                        // 줄거리 번역 호출 횟수(GeminiClient, 태그 operation·result)
			"llm.call.duration");              // 줄거리 번역 호출 한 번에 걸린 시간

	// 이 접두사로 시작하면 허용하는 지표
	private static final String[] ALLOWED_PREFIXES = {
			"jvm.memory.",
			"jvm.gc.",
			"jvm.threads.",
			"hikaricp.connections",
			"spring.batch.job"
	};

	// 값에 개인정보가 들어갈 수 있는 태그 키. 지금 코드에는 없지만 실수로 붙는 것을 막는 안전장치다
	private static final Set<String> FORBIDDEN_TAG_KEYS = Set.of(
			"userId", "user_id", "user.id", "ip", "client_ip", "email", "query");

	@Override
	public MeterFilterReply accept(Meter.Id id) {
		// 1. 개인정보 성격의 태그 키가 있으면 이름과 상관없이 거부
		for (Tag tag : id.getTagsAsIterable()) {
			if (FORBIDDEN_TAG_KEYS.contains(tag.getKey())) {
				return MeterFilterReply.DENY;
			}
		}

		// 2. 정확히 일치하는 허용 이름이면 통과
		String name = id.getName();
		if (ALLOWED_NAMES.contains(name)) {
			return MeterFilterReply.ACCEPT;
		}

		// 3. 허용 접두사로 시작하면 통과
		for (String prefix : ALLOWED_PREFIXES) {
			if (name.startsWith(prefix)) {
				return MeterFilterReply.ACCEPT;
			}
		}

		// 4. 나머지는 거부(허용 목록 방식)
		return MeterFilterReply.DENY;
	}
}
