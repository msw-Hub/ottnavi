package com.ottnavi.infra.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송 설정(app.mail.*). SMTP 접속 정보는 Boot 표준 spring.mail.*에 있다.
 *
 * @param provider 발송 방식. smtp(실제 발송) 또는 log(로그만 남김, local 기본)
 * @param from     발신 주소(MAIL_FROM)
 * @param fromName 발신자 표시 이름
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(String provider, String from, String fromName) {
}
