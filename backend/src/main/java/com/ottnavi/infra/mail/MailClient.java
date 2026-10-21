package com.ottnavi.infra.mail;

/**
 * 메일 발송 창구(포트). 구현체는 SMTP(SmtpMailClient)와 로그만 남기는 시험용(LogMailClient)이 있고
 * app.mail.provider로 고른다. HTTPS 메일 API가 필요해지면 이 인터페이스의 구현체를 하나 더 만들어 교체한다(TECH 5절).
 */
public interface MailClient {

	/**
	 * 메일 한 통을 보낸다. 보내지 못하면 MailSendException을 던진다.
	 * 호출하는 쪽은 DB 트랜잭션 밖에서 불러야 한다(외부 호출 규칙).
	 */
	void send(OutgoingMail mail);
}
