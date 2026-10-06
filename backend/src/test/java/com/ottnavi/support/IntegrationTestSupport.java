package com.ottnavi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 통합 테스트 공통 베이스: Testcontainers(PostgreSQL·Redis)로 컨텍스트를 띄운다. 통합 테스트는 이 클래스를 상속한다 */
@SpringBootTest
@Import(TestcontainersConfig.class)
public abstract class IntegrationTestSupport {
    // 빈 목킹이 필요하면 @MockitoBean(org.springframework.test.context.bean.override.mockito)을 쓴다
}
