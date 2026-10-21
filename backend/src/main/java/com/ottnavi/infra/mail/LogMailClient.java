package com.ottnavi.infra.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제로 보내지 않고 로그만 남기는 구현체(app.mail.provider=log). SMTP 계정이 없는 local에서 쓴다.
 * 수신 주소는 개인정보라 로그에 남기지 않고 제목만 남긴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "log", matchIfMissing = true)
public class LogMailClient implements MailClient {

	@Override
	public void send(OutgoingMail mail) {
		log.info("메일 발송을 건너뛰고 로그만 남긴다(provider=log): 제목={}", mail.subject());
	}
}
