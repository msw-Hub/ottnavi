package com.ottnavi.support;

import org.springframework.context.annotation.Import;

@Import(TestcontainersConfig.class)
public abstract class IntegrationTestSupport {
    // 빈 목킹이 필요하면 @MockitoBean(org.springframework.test.context.bean.override.mockito)을 쓴다
}