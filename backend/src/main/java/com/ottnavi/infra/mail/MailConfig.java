package com.ottnavi.infra.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 메일 설정 등록(app.mail.*) */
@Configuration
@EnableConfigurationProperties(AppMailProperties.class)
public class MailConfig {
}
