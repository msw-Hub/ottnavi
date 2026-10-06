# Task 016 작업 안내서 (백엔드 의존성·프로필·임시 보안 설정)

> `backend-dev` 에이전트가 2026-10-06에 작성한 안내서를 옮긴 것이다. 근거 수준: Maven Central 4.1.1 POM·BOM과 jar 확인(에이전트 보고), context7 문서(`c7`). 메인이 따로 재검증하지 않았으므로 구현 중 `compileJava`와 기동 로그로 확인한다.
> 기준 문서는 `docs/ROADMAP.md` Task 016이다. 브랜치는 `feature/b0-backend-setup`.

## 1. 작업 요약과 완료 기준

- `build.gradle`에 MVP 의존성을 모두 넣고, `application.yml`·`-local.yml`·`-prod.yml`을 만든다.
- security 스타터를 넣으면 모든 경로에 인증이 걸리므로 임시 `SecurityConfig`(전 경로 `permitAll`)를 둔다.
- Testcontainers(PostgreSQL·Redis) 통합 테스트 베이스를 만들고 기존 컨텍스트 로드 테스트를 그 위로 옮긴다.

| 완료 기준 (ROADMAP Task 016) | 확인 방법 |
|---|---|
| V-B: Testcontainers로 컨텍스트 로드 테스트 통과 | Docker Desktop을 켜고 `.\gradlew.bat build` |
| V-API: local 프로필 기동 시 Flyway·Batch·Security 오류 없음, 기본 로그인 폼·생성 비밀번호 없음 | `bootRun` 로그 확인, `/login` 호출 |

## 2. 단계 분할 (단계마다 멈춰서 확인하고 넘어간다)

### ① build.gradle 의존성

data-jpa를 넣는 순간 기존 `contextLoads`가 DB 없이 실패한다. 이 단계에서는 `build`를 돌리지 않고 컴파일만 확인한다(④에서 고친다).

| 좌표 | 버전 | 근거 |
|---|---|---|
| `spring-boot-starter-webmvc`, `-validation`, `-actuator`, `-data-jpa`, `-data-redis`, `-security` | Boot BOM | Central |
| `spring-boot-starter-security-oauth2-client` (`-oauth2-client`는 deprecated) | Boot BOM | c7 + Central |
| `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` | BOM (Flyway 12.4.0) | c7 + BOM |
| `spring-boot-starter-batch-jdbc` | BOM (Batch 6.0.5) | Central에서 4.1.1 아티팩트 존재 확인. context7에서는 아티팩트 이름을 찾지 못함 |
| `org.postgresql:postgresql` (runtimeOnly) | BOM 42.7.13 | BOM |
| `spring-cloud-starter-openfeign` + BOM `spring-cloud-dependencies` | 2025.1.3 (2025.1.2도 가능) | Central. 호환 검증기가 Boot 4.1.x 허용(jar 확인) |
| `springdoc-openapi-starter-webmvc-ui` | 3.1.1 | c7 + Central |
| `bucket4j_jdk17-core`, `-redis-common`, `-lettuce` | 8.21.0 | c7. `bucket4j_jdk17-redis`(집계 모듈)는 쓰지 않는다 |
| `shedlock-spring`, `shedlock-provider-jdbc-template` | 7.10.1 | c7. 7.x가 Boot 4.x로 테스트됨 |
| `jjwt-api`(implementation), `jjwt-impl`·`jjwt-jackson`(runtimeOnly) | 0.13.0 | c7 |
| 테스트: `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` | TC 2.0.5 (BOM) | c7 + BOM |
| 테스트: WireMock `wiremock-standalone` 또는 `wiremock-jetty12` | 3.13.2 | c7. 결정 1 참고 |
| 테스트 스타터 `-data-jpa-test`, `-security-test` (권장) | BOM | Boot 4는 테스트 스타터가 기능별로 분리됨 |

```groovy
ext { set('springCloudVersion', '2025.1.3') }

dependencies {
	// 웹·검증·모니터링
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	// DB·마이그레이션
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation 'org.flywaydb:flyway-database-postgresql'
	runtimeOnly 'org.postgresql:postgresql'
	// Redis
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
	// 보안·JWT (jjwt-jackson은 Jackson 2라 런타임에만 둔다, TECH 6절)
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-client'
	implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
	// 배치·락
	implementation 'org.springframework.boot:spring-boot-starter-batch-jdbc'
	implementation 'net.javacrumbs.shedlock:shedlock-spring:7.10.1'
	implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1'
	// 외부 연동·요청 제한·API 문서
	implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
	implementation 'com.bucket4j:bucket4j_jdk17-core:8.21.0'
	implementation 'com.bucket4j:bucket4j_jdk17-redis-common:8.21.0'
	implementation 'com.bucket4j:bucket4j_jdk17-lettuce:8.21.0'
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'
	// Lombok (결정 2)
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'

	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
	testImplementation 'org.springframework.boot:spring-boot-testcontainers'
	testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
	testImplementation 'org.testcontainers:testcontainers-postgresql'
	testImplementation 'org.wiremock:wiremock-standalone:3.13.2'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

dependencyManagement {
	imports { mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}" }
}
```

메일·Thymeleaf·GreenMail은 넣지 않는다(Task 072).

```powershell
cd backend; .\gradlew.bat compileJava compileTestJava
.\gradlew.bat dependencyInsight --dependency jackson-databind --configuration runtimeClasspath   # Jackson 2는 jjwt-jackson·springdoc 경유로만 들어와야 한다
```

### ② application*.yml

YAML만 쓴다(`.properties` 금지).

| 파일 | 내용 |
|---|---|
| `backend/src/main/resources/application.yml` | 공통 값. 접속 정보는 넣지 않는다 |
| `backend/src/main/resources/application-local.yml` (신규) | compose와 같은 기본값 |
| `backend/src/main/resources/application-prod.yml` (신규) | `${ENV_VAR}`만 쓰고 기본값은 두지 않는다(누락 시 기동 실패로 바로 드러나게) |

```yaml
# application.yml
spring:
  application:
    name: ottnavi
  datasource:
    hikari:
      maximum-pool-size: 5        # Supabase Session Pooler 한도 고려
  jpa:
    open-in-view: false           # 권장(로드맵 목록에는 없음)
    hibernate:
      ddl-auto: validate
  batch:
    jdbc:
      initialize-schema: never    # BATCH_* 테이블은 Flyway V4(Task 033)가 만든다
    job:
      enabled: false              # 기동 시 Job 자동 실행 금지, 관리자 API로만 실행
server:
  forward-headers-strategy: framework   # Cloudtype은 Boot가 아는 클라우드가 아니라 기본값이 NONE(TECH 4절)
```

```yaml
# application-local.yml (DB_URL은 일부러 참조하지 않는다. 운영 DB에 붙는 사고 방지, ROADMAP Task 015 기록)
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:${DB_PORT:5432}/${DB_NAME:ottnavi}   # compose가 127.0.0.1에만 바인딩
    username: ${DB_USERNAME:ottnavi}
    password: ${DB_PASSWORD:ottnavi}
  data:
    redis:
      host: 127.0.0.1
      port: ${REDIS_PORT:6379}
```

```yaml
# application-prod.yml
spring:
  datasource:
    url: ${DB_URL}                # Supabase Session Pooler 5432
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
springdoc:
  api-docs:
    enabled: false                # 운영에서 불필요한 기능은 끈다(ROADMAP 4.6)
  swagger-ui:
    enabled: false
server:
  tomcat:
    threads:
      max: 50                     # 값은 미정, 결정 4
```

Boot 4.1.1 메타데이터 jar로 확인한 키: `spring.batch.jdbc.initialize-schema`, `spring.batch.job.enabled`, `spring.data.redis.host/port/password/ssl.enabled`. `server.forward-headers-strategy`는 TECH 4절(c7) 근거.

확인은 ③ 뒤에 한 번에 한다(이 시점에는 security 기본 체인이 살아 있어 로그인 폼이 나온다). 지금은 `compileJava`만 통과하면 된다.

### ③ 임시 SecurityConfig

파일: `backend/src/main/java/com/ottnavi/global/security/SecurityConfig.java`

```java
/**
 * 임시 보안 설정: 전 경로 허용.
 * TODO Task 053에서 실제 규칙(/api/public/**·인증 경로 허용, 그 외 /api/** 인증, /api/admin/** ADMIN)으로 교체한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** 세션 없이 모든 요청을 허용하는 임시 필터 체인을 등록한다 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}
}
```

- 생성 비밀번호가 안 나오는 근거: Boot 4.1.1의 `UserDetailsServiceAutoConfiguration`은 `ClientRegistrationRepository` 클래스가 클래스패스에 있으면 빠진다. oauth2-client 스타터가 이 클래스를 넣는다(jar 조건 확인).
- OAuth2 등록(`spring.security.oauth2.client.registration.google.*`)은 이번에 넣지 않는다(결정 3).

**확인 (② + ③ 함께, V-API)**

```powershell
docker compose -f infra/docker-compose.yml up -d
cd backend; .\gradlew.bat bootRun --args="--spring.profiles.active=local"
# 다른 PowerShell 창에서
Invoke-WebRequest -Uri http://localhost:8080/actuator/health -SkipHttpErrorCheck   # 200, status UP(db·redis 포함)
Invoke-WebRequest -Uri http://localhost:8080/login -SkipHttpErrorCheck            # 404여야 한다(로그인 폼 없음)
```

로그에서 볼 것: `Using generated security password`가 없어야 한다 / Flyway `No migrations found` 계열은 WARN 정도(V1은 Task 019) / Batch·Hibernate validate 예외가 없어야 한다.

### ④ Testcontainers 베이스와 컨텍스트 로드 테스트

| 파일 | 할 일 |
|---|---|
| `backend/src/test/java/com/ottnavi/support/TestcontainersConfig.java` (신규, 위치는 제안) | 컨테이너 빈 + `@ServiceConnection` |
| `backend/src/test/java/com/ottnavi/support/IntegrationTestSupport.java` (신규, 제안) | `@SpringBootTest` + `@Import`. 통합 테스트가 상속한다 |
| `backend/src/test/java/com/ottnavi/OttnaviApplicationTests.java` | 위 베이스를 상속하도록 수정 |

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {   // TC 2: org.testcontainers.postgresql.PostgreSQLContainer (비제네릭)
		return new PostgreSQLContainer("postgres:17.6-alpine");   // 로컬·운영과 같은 버전
	}

	@Bean
	@ServiceConnection(name = "redis")   // 범용 컨테이너는 이름으로 Redis 연결 정보를 만든다
	GenericContainer<?> redis() {
		return new GenericContainer<>("redis:8.6-alpine").withExposedPorts(6379);
	}
}
```

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
public abstract class IntegrationTestSupport {
	// 빈 목킹이 필요하면 @MockitoBean(org.springframework.test.context.bean.override.mockito)을 쓴다
}
```

- TC 2.0.5의 새 `org.testcontainers.postgresql.PostgreSQLContainer`를 쓴다(옛 `org.testcontainers.containers.*`는 쓰지 않는다).
- Boot 4.1.1의 `RedisContainerConnectionDetailsFactory`는 이름 `redis`인 컨테이너와 `com.redis.testcontainers.RedisContainer`를 지원한다. 후자를 쓰려면 `com.redis:testcontainers-redis`(BOM 2.2.4)를 추가한다.
- 컨테이너 빈 방식은 테스트 컨텍스트 캐시를 공유해 클래스마다 새로 뜨지 않는다(문서 기준, 실측 안 함).

**확인 (V-B)**: Docker Desktop을 켠 상태에서

```powershell
cd backend; .\gradlew.bat test --tests "com.ottnavi.OttnaviApplicationTests"
.\gradlew.bat build
```

WireMock 충돌 여부를 함께 보려면 `WireMockServer`를 켜고 끄기만 하는 임시 테스트를 하나 돌려 본다(결정 1).

## 3. 버전 함정과 주의점

| 함정 | 대응 | 출처 |
|---|---|---|
| Flyway는 `spring-boot-starter-flyway`가 없으면 돌지 않는다. PostgreSQL은 `flyway-database-postgresql`도 필요 | 둘 다 추가 | TECH 6절, c7 |
| `spring-boot-starter-batch`만 넣으면 메모리 모드 | `-batch-jdbc` 사용 | TECH 6절, Central |
| `spring-boot-starter-oauth2-client`는 deprecated | `-security-oauth2-client` | c7 |
| Jackson 3(`tools.jackson.*`)과 jjwt-jackson의 Jackson 2가 섞임 | jjwt-jackson은 runtimeOnly. 앱 코드에 `com.fasterxml.jackson.databind` import 금지(어노테이션 패키지는 유지). BOM이 `jackson-2-bom` 2.21.5 관리 | TECH 6절, BOM |
| `@MockBean` 제거 | `@MockitoBean` | TECH 6절 |
| Testcontainers 2에서 아티팩트·패키지 변경 | `testcontainers-postgresql`, `testcontainers-junit-jupiter`, `org.testcontainers.postgresql.*` | TECH 6절, jar |
| Boot 4는 테스트 스타터가 기능별로 분리(`@DataJpaTest`는 `-data-jpa-test`, `@WithMockUser`는 `-security-test`) | 필요한 것만 추가 | Central POM |
| WireMock과 Jetty: Boot BOM이 Jetty를 12.1.12로 강제. 기본 `wiremock`(Jetty 11)은 깨지고, `wiremock-jetty12` 3.13.2는 12.0.30 기준 빌드 | 셰이딩된 `wiremock-standalone`이 가장 안전. jetty12를 쓰면 기동 테스트로 확인 | c7, BOM |
| bucket4j-lettuce는 lettuce 6.1.8을 provided로 두는데 Boot 관리 버전은 Lettuce 7.5.2 | 동작 여부 **확인 불가**. 요청 제한 구현 Task에서 검증 | Central POM |
| Spring Cloud 호환성 검증기가 안 맞는 Boot 버전이면 기동을 막음 | 2025.1.2·2025.1.3은 4.1.x 허용(확인됨). 검증기를 끄는 설정은 쓰지 않는다 | jar |
| Windows에서 `localhost`가 `::1`로 먼저 풀릴 수 있음. compose는 `127.0.0.1`에만 바인딩 | local URL에 `127.0.0.1` 사용 | 도출 |
| `ddl-auto: validate`는 엔티티가 없어서 지금은 통과. FK의 ON DELETE는 검사하지 않음 | 해당 Task의 테스트로 검증(TECH T-1) | TECH |

## 4. 함께 갱신할 문서와 파일

| 대상 | 내용 |
|---|---|
| `docs/ROADMAP.md` Task 016 | ⬜ → ✅, `기록:` 추가(실제 사용 버전, 결정 결과, V-B·V-API 결과) |
| `docs/ROADMAP.md` 16행 | "미생성: application-{local,prod}.yml" 삭제 |
| `docs/ROADMAP.md` 27행 | batch-jdbc 아티팩트 이름은 Maven Central에서 확인했다고 갱신. 2절 진행 현황 표도 갱신 |
| `docs/ROADMAP.md` 4.2 | Flyway 번호 규칙은 그대로. 이번 Task는 마이그레이션이 없고 V1은 Task 019 |
| `docs/TECH.md` 6절 | 새 함정 후보: WireMock의 Jetty BOM 강제(12.1 대 12.0), Boot 4 테스트 스타터 분리, Redis `GenericContainer`의 `@ServiceConnection(name="redis")` |
| 루트 `CLAUDE.md` "저장소 현황"·명령 주석 | "의존성이 webmvc뿐", "local 프로필은 Task 016에서 만든다" 문구 갱신 |
| `.env.example` | 지금 목록으로 충분(DB_URL, REDIS_* 있음). 결정에 따라 `SPRING_PROFILES_ACTIVE`, `JAVA_TOOL_OPTIONS`, `REDIS_SSL_ENABLED`의 이름만 추가 |

## 5. 사용자 결정이 필요한 것

쉬림프 요약과 ROADMAP은 실질적으로 같다. 단 ROADMAP 4.6(214행)은 JVM 옵션 원칙을 "Task 016의 `prod` 프로필과 실행 옵션에서 정한다"고 하고, 그 안에 운영 springdoc 끄기와 Tomcat 스레드·연결 풀 제한이 들어 있다. 이 부분을 포함할지 Task 020·021로 넘길지 정해야 한다.

| # | 결정 | 에이전트 권장 |
|---|---|---|
| 1 | WireMock 아티팩트: `wiremock-standalone` vs TECH 6절의 `wiremock-jetty12` | standalone |
| 2 | Lombok: ROADMAP 목록엔 없지만 `backend.md`가 `@RequiredArgsConstructor`·`@Slf4j`·`@Getter`를 전제 | 지금 추가(BOM 1.18.46) |
| 3 | OAuth2 Google 등록·`JWT_SECRET` 참조 시점: 지금 prod에 넣으면 첫 배포(Task 021)에서 값이 없어 기동 실패 가능 | Task 053으로 미룸 |
| 4 | prod 튜닝 값(Tomcat `threads.max`, JVM 힙, yml 대 Cloudtype 실행 옵션): 문서에 수치 없음 | 정해야 함 |
| 5 | `spring.profiles.default: local`: 프로필 없이 실행하면 DataSource가 없어 기동 실패 | 두면 편함(운영에서 빠뜨려도 127.0.0.1 연결 실패로 곧바로 드러남) |
| 6 | Redis health: Redis Cloud DB가 삭제된 채 Task 021을 하면 `/actuator/health`가 DOWN → Cloudtype 헬스체크 실패 가능 | `management.health.redis.enabled` 조정 여부를 Task 020 Redis 확인 결과와 함께 판단 |
| 7 | `spring.application.name`: 현재 `OttnaviApplication`(Initializr 기본값) | `ottnavi`로 변경(사소) |

**확인하지 못한 것**(기동 로그로 확인): Flyway `No migrations found` WARN, OpenFeign이 함께 가져오는 loadbalancer 캐시 경고, BATCH_* 테이블 없이 Batch JDBC 자동 구성이 기동을 막지 않는지(문서상 Job 실행 시에만 필요), bucket4j-lettuce와 Lettuce 7 호환성.

## 6. 검수 요청 때 볼 체크리스트

- [ ] 스타터 이름이 4.x 기준인가: `security-oauth2-client`, `flyway` + `flyway-database-postgresql`, `batch-jdbc`. 메일·Thymeleaf·GreenMail은 없는가
- [ ] jjwt 범위가 api=implementation, impl·jackson=runtimeOnly인가. Spring Cloud BOM이 2025.1.2 이상인가
- [ ] 버전을 직접 적은 라이브러리(springdoc, bucket4j, shedlock, jjwt, wiremock)만 버전이 있고, BOM이 관리하는 것에는 버전을 적지 않았는가
- [ ] 설정이 YAML뿐인가(`.properties` 없음). prod에 하드코딩 값이나 기본값 없이 `${ENV}`만 있는가. local이 `DB_URL`을 참조하지 않는가
- [ ] `initialize-schema: never`, `job.enabled: false`, `ddl-auto: validate`, Hikari 5, `forward-headers-strategy: framework`가 있는가
- [ ] `SecurityConfig`가 `global/security`에 있는가. STATELESS이고 csrf·formLogin·httpBasic이 꺼져 있으며 `permitAll`인가. "Task 053에서 교체" 주석과 한국어 Javadoc이 있는가
- [ ] 테스트에 H2가 없는가. 이미지 태그가 compose와 같은가(17.6, 8.6). `@MockBean`을 쓰지 않았는가
- [ ] 실행 결과: `.\gradlew.bat build` 통과(Docker Desktop 켬). local `bootRun` 로그에 생성 비밀번호가 없고 `/login`이 404이며 health가 UP인가
- [ ] ROADMAP Task 016 `기록:`, 16·27행, CLAUDE.md 저장소 현황을 갱신했는가

프론트 쪽으로 넘길 변경은 없다.
