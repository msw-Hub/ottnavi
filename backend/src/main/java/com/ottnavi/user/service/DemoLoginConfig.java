package com.ottnavi.user.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 데모 계정 로그인 설정 등록(app.demo-login.*) */
@Configuration
@EnableConfigurationProperties(DemoLoginProperties.class)
public class DemoLoginConfig {
}
