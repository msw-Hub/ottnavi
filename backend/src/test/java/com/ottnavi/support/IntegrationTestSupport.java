package com.ottnavi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 통합 테스트 공통 베이스: Testcontainers(PostgreSQL·Redis)로 컨텍스트를 띄운다. 통합 테스트는 이 클래스를 상속한다.
 * 요청 제한(app.rate-limit.enabled)은 기본으로 끈다. 같은 컨텍스트를 공유하는 테스트들이 한 IP(127.0.0.1)의
 * 한도를 나눠 쓰면 순서에 따라 429가 나기 때문이다. 요청 제한을 검증하는 테스트만 서브클래스에서 다시 켠다.
 * 매월 재계산 자동 시작(app.plan-recalc.auto-start)도 기본으로 끈다. 수집 Job을 실행하는 테스트가 재계산 Job을 몰래 시작하면 데이터가 섞인다. 자동 시작을 검증하는 테스트만 다시 켠다.
 * 알림 발송 스케줄러(app.notification.scheduler-enabled)도 기본으로 끈다. 켜 두면 스케줄러가 다른 테스트가 넣은 알림 행을 몰래 집어 간다. 스케줄러를 검증하는 테스트만 다시 켠다.
 * 구독 안내의 즉시 발송(app.notification.immediate-send-enabled)도 기본으로 끈다. 켜 두면 저장 테스트마다 비동기 스레드가 메일을 보내려 한다. 즉시 발송을 검증하는 테스트만 다시 켠다.
 * 메일은 SMTP 구현체를 쓰되 접속 대상을 GreenMail(127.0.0.1:3025, 인증·암호화 없음)로 둔다. GreenMail은 알림 테스트 클래스만 띄운다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "app.rate-limit.enabled=false",
        "app.plan-recalc.auto-start=false",
        "app.notification.scheduler-enabled=false",
        "app.notification.immediate-send-enabled=false",
        "app.mail.provider=smtp",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false"})
public abstract class IntegrationTestSupport {
    // 빈 목킹이 필요하면 @MockitoBean(org.springframework.test.context.bean.override.mockito)을 쓴다
}
