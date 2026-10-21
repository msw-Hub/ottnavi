package com.ottnavi.infra.mail;

import com.ottnavi.infra.mail.exception.MailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP로 메일을 보내는 구현체(app.mail.provider=smtp). 접속 정보와 타임아웃 3종은 spring.mail.*에 있다
 * (타임아웃이 없으면 응답 없는 서버에서 스레드가 무기한 대기한다, TECH 5절).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "smtp")
public class SmtpMailClient implements MailClient {

	private final JavaMailSender mailSender;
	private final AppMailProperties properties;

	@Override
	public void send(OutgoingMail mail) {
		try {
			// 1. 발신·수신·제목·HTML 본문을 UTF-8로 채운다
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(new InternetAddress(properties.from(), properties.fromName(), "UTF-8"));
			helper.setTo(mail.to());
			helper.setSubject(mail.subject());
			helper.setText(mail.htmlBody(), true);

			// 2. 보낸다
			mailSender.send(message);
		} catch (MessagingException | UnsupportedEncodingException | MailException e) {
			// 메시지에는 원인 종류와 내용을 담는다(outbox last_error에 기록됨). 로그에는 이 메시지를 그대로 찍지 않는다(주소가 섞일 수 있음)
			throw new MailSendException("SMTP 발송 실패: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
		}
	}
}
