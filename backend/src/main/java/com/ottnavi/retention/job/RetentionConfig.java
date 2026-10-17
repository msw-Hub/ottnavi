package com.ottnavi.retention.job;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 데이터 정리 설정 등록. 2단계에서 Job 조립(RetentionJobConfig)이 이 패키지에 더해진다 */
@Configuration
@EnableConfigurationProperties(RetentionProperties.class)
public class RetentionConfig {
}
