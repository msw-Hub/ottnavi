package com.ottnavi.infra.analytics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Google Analytics 연동 설정 등록(app.analytics.*). 둘 다 비었는지·하나만 있는지 검사는 AnalyticsProperties가 한다 */
@Configuration
@EnableConfigurationProperties(AnalyticsProperties.class)
public class AnalyticsConfig {
}
