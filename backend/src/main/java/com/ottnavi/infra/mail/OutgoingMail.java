package com.ottnavi.infra.mail;

/**
 * 보낼 메일 한 통. 발신자는 설정(app.mail.from)에서 정하므로 담지 않는다.
 *
 * @param to       수신 주소
 * @param subject  제목
 * @param htmlBody HTML 본문
 */
public record OutgoingMail(String to, String subject, String htmlBody) {
}
