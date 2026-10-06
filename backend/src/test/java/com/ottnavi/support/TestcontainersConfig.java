package com.ottnavi.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {   // TC 2: org.testcontainers.postgresql.PostgreSQLContainer (비제네릭)
        return new PostgreSQLContainer("postgres:17.6-alpine");   // 로컬·운영과 같은 버전
    }

    @Bean
    @ServiceConnection(name = "redis")   // 범용 컨테이너는 이름으로 Redis 연결 정보를 만든다
    @SuppressWarnings("resource")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:8.6-alpine").withExposedPorts(6379);
    }
}