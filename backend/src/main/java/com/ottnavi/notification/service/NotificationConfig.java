package com.ottnavi.notification.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 알림 발송기 설정 등록(app.notification.*) */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {
}
