package com.ottnavi.global.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * 추적(span)에 개인정보가 실리지 않게 관측 결과를 걸러내는 필터다.
 *
 * 예외 메시지·스택은 이 필터로 지울 수 없다(예외는 관측이 끝나기 전에 span에 기록된다). SanitizingSpanExporter가 내보내기 직전에 지운다.
 *
 * 규칙(2026-11-01 결정):
 * 1. 낮은 카디널리티 값(요청 방식 method, 경로 템플릿 uri, 응답 상태 status, 결과 outcome, 예외 종류)만 남기고
 *    높은 카디널리티 값은 모두 지운다. 실제 경로·전체 주소(쿼리 포함)·SQL 같은 값이 여기에 들어오기 때문이다.
 * 2. 이름이 알려진 식별 속성(클라이언트 주소, 사용자 에이전트, 사용자 id 등)은 낮은 카디널리티에서도 지운다.
 *
 * 예: 요청 GET /api/public/titles/movie/603?q=홍길동 → span에는 method=GET, uri=/api/public/titles/{mediaType}/{tmdbId},
 * status=200만 남는다.
 */
public class TracingPrivacyFilter implements ObservationFilter {

	/** 낮은 카디널리티에 섞여 들어와도 지우는 식별 속성 이름이다. */
	private static final List<String> BLOCKED_KEYS = List.of(
			"http.url", "url.full", "url.query", "url.path",
			"client.address", "client.ip", "net.peer.ip", "net.sock.peer.addr", "http.client_ip",
			"net.peer.name", "net.peer.port", "net.sock.peer.port", "db.user",   // Redis·DB 서버 주소와 계정(사용자 정보는 아니지만 인프라 정보라 싣지 않는다)
			"user_agent.original", "http.user_agent",
			"enduser.id", "user.id", "user.name", "user.email");

	@Override
	public Observation.Context map(Observation.Context context) {
		// 1. 높은 카디널리티 값은 이름을 모아 둔 뒤 모두 지운다(순회 중에 지우지 않으려고 복사해서 쓴다)
		List<String> highNames = new ArrayList<>();
		for (KeyValue keyValue : context.getHighCardinalityKeyValues()) {
			highNames.add(keyValue.getKey());
		}
		for (String name : highNames) {
			context.removeHighCardinalityKeyValue(name);
		}

		// 2. 식별 속성 이름은 낮은 카디널리티에서도 지운다
		for (String name : BLOCKED_KEYS) {
			context.removeLowCardinalityKeyValue(name);
		}

		return context;
	}

}
