package com.ottnavi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 실행 설정(Task 073). 플랜 저장 직후 구독 안내 알림을 요청 스레드와 따로 만들고 보내는 데 쓴다.
 * 별도 Executor 빈은 두지 않고 Spring Boot 자동 구성 실행기를 쓴다(스레드 수·이름은 spring.task.execution.*).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
