package com.ottnavi.global.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/** OpenFeign 활성화. 클라이언트별 타임아웃은 application.yml(spring.cloud.openfeign.client.config.*)에 둔다. */
@Configuration
@EnableFeignClients(basePackages = "com.ottnavi.infra")   // 외부 연동 클라이언트는 infra 아래에만 둔다(backend.md 패키지 구조)
public class FeignConfig {
    // ErrorDecoder(429·5xx → 재시도 예외)와 Retryer(지수 백오프, Retry-After 우선)는 TMDB 클라이언트와 함께 Task 042에서 추가한다(TECH 3절)
}
