package com.ottnavi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 통합 테스트 공통 베이스: Testcontainers(PostgreSQL·Redis)로 컨텍스트를 띄운다. 통합 테스트는 이 클래스를 상속한다.
 * 요청 제한(app.rate-limit.enabled)은 기본으로 끈다. 같은 컨텍스트를 공유하는 테스트들이 한 IP(127.0.0.1)의
 * 한도를 나눠 쓰면 순서에 따라 429가 나기 때문이다. 요청 제한을 검증하는 테스트만 서브클래스에서 다시 켠다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "app.rate-limit.enabled=false")
public abstract class IntegrationTestSupport {
    // 빈 목킹이 필요하면 @MockitoBean(org.springframework.test.context.bean.override.mockito)을 쓴다
}
