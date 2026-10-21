package com.ottnavi.notification.service;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.model.RenderedMail;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 유형과 payload(JSON)로 메일 제목과 HTML 본문을 만든다. 템플릿은 resources/templates/mail/ 에 있다.
 * 예: MONTHLY + {"userName":"홍길동","summary":"11월에는 ..."} → 제목 "[OTT내비] 이번 달 구독 플랜을 확인해 주세요", 본문 HTML
 * 본문 맨 아래에는 어느 유형이든 "확인된 범위" 고지, 출처(TMDB·JustWatch), 데이터 기준일, 알림 끄는 방법이 붙는다(PRD 화면 표시 규칙).
 */
@Component
@RequiredArgsConstructor
public class NotificationMailRenderer {

	private static final String DEFAULT_USER_NAME = "회원";

	private final SpringTemplateEngine templateEngine;
	private final Clock clock;
	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	/** 유형별 제목과 본문을 만든다. payload가 JSON이 아니면 예외를 던지고, 호출한 발송기가 실패로 기록한다 */
	public RenderedMail render(NotificationType type, String payloadJson) {
		// 1. payload JSON을 템플릿에서 읽을 수 있는 Map으로 바꾼다
		Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {
		});

		// 2. 인사말 이름과 데이터 기준일을 정한다(payload에 없으면 기본값)
		String userName = DEFAULT_USER_NAME;
		Object payloadUserName = payload.get("userName");
		if (payloadUserName != null && !payloadUserName.toString().isBlank()) {
			userName = payloadUserName.toString();
		}
		String dataDate = LocalDate.now(clock).toString();
		Object payloadDataDate = payload.get("dataDate");
		if (payloadDataDate != null && !payloadDataDate.toString().isBlank()) {
			dataDate = payloadDataDate.toString();
		}

		// 3. 유형별 템플릿에 값을 채워 HTML을 만든다
		Context context = new Context(Locale.KOREAN);
		Map<String, Object> variables = new HashMap<>();
		variables.put("payload", payload);
		variables.put("userName", userName);
		variables.put("dataDate", dataDate);
		context.setVariables(variables);
		String htmlBody = templateEngine.process(templateName(type), context);

		// 4. 제목은 유형별 고정 문구
		return new RenderedMail(subject(type), htmlBody);
	}

	private String templateName(NotificationType type) {
		if (type == NotificationType.SUBSCRIBE_GUIDE) {
			return "mail/subscribe-guide";
		}
		if (type == NotificationType.PLAN_CHANGE) {
			return "mail/plan-change";
		}
		return "mail/monthly";
	}

	private String subject(NotificationType type) {
		if (type == NotificationType.SUBSCRIBE_GUIDE) {
			return "[OTT내비] 저장한 구독 플랜 안내";
		}
		if (type == NotificationType.PLAN_CHANGE) {
			return "[OTT내비] 구독 플랜이 바뀌었어요";
		}
		return "[OTT내비] 이번 달 구독 플랜을 확인해 주세요";
	}
}
