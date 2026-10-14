---
paths:
  - "backend/**"
---

> 공통 언어/주석 원칙은 `~/.claude/CLAUDE.md`(User instructions)를 따른다. 여기는 ottnavi 백엔드의 Java/Spring Boot 고유 스타일만 다룬다.
> 요구사항은 `docs/PRD.md`, 스키마는 `docs/ERD.md`, API 계약은 `docs/api/openapi.yaml`을 기준으로 한다.

- Javadoc은 한국어로 쓴다. 첫 줄에 "무엇을 하는지"를 요약하고, 이유나 "이런 입력 → 이런 출력" 예시가 필요하면 아래에 여러 줄로 덧붙인다. 읽는 사람이 배경을 몰라도 이해되는 것이 우선이다.
- 어려운 문법은 쓰지 않는다(2026-10-14 사용자 선호). 삼항 연산자(`a ? b : c`), 람다(`->`), 메서드 참조(`::`), `stream()` 체이닝 대신 `if`와 `for`로 풀어 쓴다. 단 Spring Security 설정처럼 라이브러리가 람다를 요구하는 설정 코드는 예외다. 이미 있는 코드는 일부러 바꾸지 않고 그 파일을 고칠 때 함께 바꾼다.

## 변수 스타일

- 변수/메서드명은 **camelCase**, 클래스명은 **PascalCase**를 따른다.
- ID 필드는 `xxxId` 형태로 통일한다 (예: `userId`, `watchUnitId`). TMDB에서 온 외부 ID는 `tmdbId`, `providerId`처럼 출처를 이름에 드러낸다.
- boolean 필드/메서드는 `isXxx` 형태로 짓는다 (예: `isRuntimeEstimated`). 단, DB 컬럼명이 `in_standard`처럼 정해진 경우는 컬럼명을 따른다.
- static final 상수 및 에러 코드 문자열은 **SCREAMING_SNAKE_CASE**로 짓는다 (예: `DEFAULT_EPISODE_RUNTIME_MIN`, `TITLE_NOT_FOUND`).
- 축약어는 관례적으로 굳은 것만 허용한다 (`dto`, `e`/`ex`는 예외 변수명, `req`/`res`는 지양하고 의미가 드러나는 이름을 우선).
- 도메인 용어는 ERD의 영문 용어로 통일한다 (`title`, `watchUnit`, `availability`, `ottService`, `product`, `plan`). 의미가 바로 드러나지 않는 용어는 코드 옆 주석으로 한국어 설명을 붙인다 (예: `WatchUnit // 시청 단위: 영화 1편 또는 시즌 1개`).

## 주석 스타일 (Java 고유)

- public 메서드 위에는 Javadoc을 한국어로 붙인다. 첫 줄은 한 줄 요약이고, 이유나 입력 → 출력 예시는 아래에 이어 쓴다. 매개변수 설명이 필요하면 `@param`으로 추가한다.
- **여러 단계로 이뤄진 메서드는 단계마다 번호를 붙인 한 줄 주석으로 흐름을 나눈다**(2026-10-14 사용자 요청). 대상은 Processor의 `process`, Tasklet의 `execute`, 서비스의 저장·판정 메서드처럼 읽는 순서대로 일이 이어지는 메서드다. 번호는 메서드 안에서 1부터 센다. 주석은 "이 코드가 무엇인가"가 아니라 "이 단계가 흐름에서 맡은 일"을 쓴다. 한두 줄짜리 자명한 메서드와 getter에는 달지 않는다. 형식:

  ```java
  // 1. 제공처 목록을 모을 공간
  Map<Integer, DiscoveredProvider> merged = new LinkedHashMap<>();

  // 2. 영화와 드라마 제공처 목록을 각각 조회
  for (TmdbMediaType mediaType : TmdbMediaType.values()) {
      ...
      // 3. 응답이 없으면 다음 매체로
      ...
  }

  // 4. 처음 발견한 제공처를 DB에 저장
  ```

  반복문·분기 안의 단계는 바깥 번호에 이어서 센다(들여쓰기가 깊어져도 번호는 위에서 아래로 증가한다). 이미 있는 코드는 일부러 고치지 않고 그 파일을 고칠 때 함께 단다.
- 필드 옆에 도메인 의미가 명확하지 않은 경우 한국어로 짧게 부연한다 (예: `private Integer billingDay; // 결제일, 기본 1일`).
- 관련 있는 핸들러/메서드 여러 개를 묶을 때는 구분용 한 줄 주석으로 섹션을 나눈다 (예: `// TMDB 연동 예외`).
- 계산 엔진의 규칙(목적함수 우선순위, 배정 규칙, 기본 러닝타임 등)을 구현한 곳에는 PRD 5.4의 어느 규칙인지 주석으로 남긴다.
- 그 외 공용 주석 원칙(왜 설명, 죽은 코드 처리, 강조 키워드 등)은 User instructions를 따른다.

## 코딩 스타일

**Controller**
- `@RestController` + `@RequiredArgsConstructor` + `@Slf4j` 조합을 기본으로 쓴다.
- Swagger 문서화가 필요하면 `@Tag`를 붙이고, 문서 어노테이션이 많아지면 별도 인터페이스(`XxxControllerInterface`)로
  분리해서 `@Override`로 구현한다 — 컨트롤러 본체는 로직에만 집중시키기 위함.
- 예외는 컨트롤러에서 직접 잡지 않고 `GlobalExceptionHandler`로 위임, 성공 응답만 `CommonResponse.success(data)`로 감싼다.
- 공개 API는 `/api/public/**`, 로그인 필요 API는 `/api/**`, 관리자 API는 `/api/admin/**`로 경로를 나눈다. 보안 설정과 요청 제한이 이 경로 규칙에 의존한다. 단 인증 경로(`/api/auth/**`, `/api/oauth2/**`, `/api/login/**`)는 이 3분류의 예외다. 로그인 시작·콜백·재발급·로그아웃은 보안 설정에서 따로 허용하고, 요청 제한도 공개 API와 같은 버킷을 적용한다.
- 안 쓰는 엔드포인트는 주석으로 남기지 않고 삭제한다. 기록은 git 이력에 남고, 주석 처리된 엔드포인트는 OpenAPI 계약과 실제 코드의 차이를 만든다.

**API 계약 (OpenAPI)**
- `docs/api/openapi.yaml`이 프론트와의 계약이다. 엔드포인트, 요청·응답 필드, 오류 코드를 바꾸면 같은 커밋에서 이 파일도 함께 고친다.
- springdoc이 생성한 명세와 `openapi.yaml`이 다르면 CI에서 실패한다. 어느 쪽이 맞는지 판단해 한쪽을 고친다.
- 응답 DTO 필드명은 계약의 필드명과 정확히 같게 둔다. 프론트는 orval로 이 계약에서 타입을 생성한다.

**Service**
- 클래스에 `@Transactional(readOnly = true)`를 기본으로 걸고, 쓰기 메서드에만 개별 `@Transactional`을 붙인다.
- 커스텀 예외는 `throw e`로 그대로 전파하고 `GlobalExceptionHandler`가 최종 처리한다. 서비스 메서드마다 `try/catch`로 실패 지표를 세지 않는다(2026-10-14 사용자 결정). 실패 지표는 외부 호출 창구(예: `TmdbClient`의 `tmdb.call`)나 예외 핸들러 한곳에서 센다. 업무상 알아야 하는 실패(예: 수집 중 작품 건너뜀)는 `log.warn`으로 남긴다.
- 로그 심각도를 구분해서 찍는다: `log.info`(정상 흐름) / `log.warn`(예상 가능한 실패) / `log.error`(예외 스택 포함).
- 더티 체킹처럼 암묵적으로 동작하는 부분은 그 이유를 주석으로 명시한다.
- 외부 호출(TMDB, 메일, LLM)은 트랜잭션 안에서 하지 않는다. 외부 응답을 먼저 받고, DB 반영만 트랜잭션으로 묶는다.

**예외 처리 (RFC 9457 ProblemDetail 기준)**
- 커스텀 예외는 `RuntimeException` 상속, `(String message)` / `(String message, Throwable cause)` 두 생성자만 둔다.
- `GlobalExceptionHandler`(`@RestControllerAdvice`, 필요시 `ResponseEntityExceptionHandler` 확장)에서 타입별
  `@ExceptionHandler`가 `ProblemDetail`을 반환한다 — `Map<String, Object>`를 직접 조립하던 방식은 쓰지 않는다.
- `ProblemDetail.forStatusAndDetail(status, message)`로 만들고, 도메인 전용 필드는 `setProperty("errorCode", "...")`로
  추가한다 (표준 필드를 오버라이드하지 않고 properties map만 확장).
- 에러 코드는 SCREAMING_SNAKE_CASE, HTTP 상태 코드는 에러 성격에 맞게 정확히 매핑한다(400/401/403/404/409/422/429/500/502 등).
- 요청 제한(Bucket4j) 초과도 같은 형식의 429 ProblemDetail로 응답한다 (`errorCode`: `RATE_LIMIT_EXCEEDED`).
- TMDB 호출 실패는 502로 매핑하되, 검색처럼 DB 결과로 대체할 수 있는 경우는 예외를 던지지 않고 DB 결과만 반환한다.
- 응답 Content-Type은 `application/problem+json`으로 자동 처리되어 에러 응답임을 헤더만으로 구분 가능하다.

**Entity**
- `@Getter`만 사용하고 **Setter는 열지 않는다** — 상태 변경은 도메인 메서드(`updateXxx()`, `applyProviders()` 등)로만 한다.
- 서로 연동되는 컬럼(예: `availability`의 `status`와 `in_standard`/`in_ad_tier`)은 하나의 도메인 메서드에서만 함께 변경한다.
- 기본 생성자는 `protected`로 막아 JPA 프록시 생성만 허용한다.
- 필수 필드만 받는 생성자와 선택 필드까지 받는 생성자를 오버로드로 함께 둔다.
- 공통 타임스탬프(`createdAt`/`updatedAt`)는 베이스 엔티티를 상속해서 처리한다.
- PK는 `Long id` + `GenerationType.IDENTITY`를 기본으로 한다. 예외는 외부 ID를 그대로 PK로 쓰는 `tmdb_provider`, `genre`, 부모의 PK를 그대로 쓰는 `user_setting`, 복합 PK인 `product_component`·`title_genre`다.
- enum은 `@Enumerated(EnumType.STRING)`으로 저장한다.
- 연관관계는 단방향 `@ManyToOne(fetch = LAZY)`를 기본으로 하고, 양방향은 꼭 필요할 때만 둔다.
- 파생 표시값은 `getXxxString()` 같은 메서드로 엔티티에 둔다.
- 엔티티 생성은 정적 팩토리 `create(...)`로 하고, 상태 변경은 도메인 메서드로만 한다.
- DB `DEFAULT`는 Hibernate가 INSERT에 쓰지 않으므로 `create`가 기본값을 직접 채운다.
- 복합 기본키는 키 클래스(`@Embeddable`, `Serializable`, `equals/hashCode`) + 엔티티의 `@EmbeddedId` + 연관 `@MapsId`로 만든다. 키 클래스에는 리포지토리를 만들지 않는다.
- NUMERIC 컬럼은 `BigDecimal`로 매핑한다.

**DTO**
- Request/Response 모두 **record**로 작성한다 (불변).
- Request는 Bean Validation 어노테이션에 한국어 메시지를 붙인다 (`@NotNull(message = "...")`).
- Response는 `static XxxResponse from(Entity entity)` 팩토리 메서드를 두고, 필드가 많으면 `@Builder`를 병행한다.
- Swagger 노출이 필요한 DTO는 `@Schema(description, example)` + 클래스 상단 `@param` Javadoc을 단다.
- TMDB 응답 DTO는 `infra/tmdb` 안에만 두고, 도메인 계층으로 그대로 넘기지 않는다. 필요한 값만 도메인 모델로 변환한다.

**Repository**
- 기본은 `JpaRepository`의 메서드 쿼리와 `@Query`(JPQL)로 해결한다.
- QueryDSL은 조건이 자주 바뀌는 조회(검색, 관리자 목록 등)가 생겼을 때만 도입한다. 도입하면 `XxxRepositoryCustom` + `XxxRepositoryImpl`로 분리하고,
  `io.github.openfeign.querydsl` 포크를 쓴다 (원본 `com.querydsl`은 관리 중단). Spring Boot 4.1 / Hibernate 7 호환 버전을 확인한 뒤 추가한다.
- DTO로 바로 받아야 하는 조회는 `Projections`나 record 생성자 표현식으로 엔티티를 거치지 않고 매핑한다.
- outbox처럼 동시 처리 대상을 가져오는 조회는 `@Lock(PESSIMISTIC_WRITE)` + 락 타임아웃 힌트 `-2`로 `FOR UPDATE SKIP LOCKED`를 쓴다.

**계산 엔진 (`engine` 패키지)**
- Spring 어노테이션, JPA 엔티티, Lombok 이외의 프레임워크 의존을 넣지 않는다. 순수 Java로만 작성한다.
- 입력과 출력은 엔진 전용 record(시청 단위, 상품, 계산 조건, 결과)로 정의한다. 엔티티와의 변환은 `plan` 서비스가 맡는다.
- 완전탐색과 그리디는 같은 인터페이스를 구현해 같은 입력으로 비교할 수 있게 한다.
- 모든 규칙은 정답이 알려진 테스트 케이스로 검증한다.

**배치 (`collect` 패키지)**
- 수집은 Spring Batch Job으로 작성하고, JobRepository는 JDBC 방식(PostgreSQL)을 쓴다. 메타데이터 테이블은 Flyway 스크립트로 만든다.
- Reader/Writer는 `JpaPagingItemReader`, `JpaItemWriter`를 우선 쓴다.
- Job은 GitHub Actions cron이 관리자 API를 호출해 실행한다. Job 파라미터에 날짜를 넣어 같은 날 중복 실행을 막는다.
- TMDB 호출은 Bucket4j로 초당 40회 미만으로 제한하고, 429 응답을 받으면 백오프 후 재시도한다.
- `@Scheduled` 작업에는 ShedLock(`@SchedulerLock`)을 붙인다.

**테스트**
- 기능 하나의 1차 개발이 끝났을 때, 단순하지 않은 기능(분기·계산·상태 전이·외부 연동·동시성이 있는 로직)이라고 판단되면 단위 테스트 작성을 사용자에게 먼저 권유한다. 권유 시 어떤 케이스(정상, 경계, 실패)를 검증할지 한두 줄로 함께 제안하고, 사용자가 동의하면 작성한다.
- 단순 getter/setter, 위임만 하는 코드, 단순 CRUD처럼 자명한 코드는 권유하지 않는다. 사용자가 묻지 않았는데 테스트를 먼저 작성하지 않는다.
- 테스트 작성 스타일(2026-10-14 확정): 테스트 메서드 이름은 무엇을 확인하는지 한국어 문장으로 쓴다(`@DisplayName`을 따로 달지 않는다). 본문은 `// given`, `// when`, `// then` 세 구역 주석으로 나누고, 테스트 하나는 한 가지 사실만 확인한다. 클래스가 커져 그룹이 필요할 때만 `@Nested`로 묶는다. 문법은 위의 "어려운 문법은 쓰지 않는다"를 테스트에도 적용한다.
- 단위 테스트: JUnit 5 + AssertJ + Mockito. 계산 엔진은 Spring 컨텍스트 없이 테스트한다.
- 통합 테스트: Testcontainers(PostgreSQL, Redis)를 쓴다. H2는 쓰지 않는다 — `NULLS NOT DISTINCT`, 부분 인덱스, `JSONB`, `SKIP LOCKED` 등 PostgreSQL 전용 기능을 검증할 수 없기 때문이다.
- TMDB 연동은 WireMock으로 테스트하고, 응답 샘플 JSON은 `src/test/resources/tmdb/`에 둔다. 테스트에서 실제 TMDB를 호출하지 않는다.
- 스프링 빈 목킹은 `@MockitoBean`을 쓴다 (`@MockBean`은 제거됨).

## 추가 선호사항 (Java/Spring 고유)

- 실패/성공 지표는 Micrometer `MeterRegistry`의 Counter/Timer로 남긴다 (예: `tmdb.call`, `plan.calculate`, `notification.send`). 배포 환경에서는 Grafana Cloud로 전송된다.
- 가변 DTO보다 불변 record, 에러도 200으로 뭉뚱그리기보다 상태 코드를 정확히 매핑, 테스트 없는 코드보다 단위 테스트가 있는 코드를 지향한다.
- 다음 패턴은 지양하고 아래 대안을 따른다:
  - 로그 추적: 수동 `MDC.put("spanId", ...)` 대신 **Micrometer Tracing**이 traceId/spanId를 자동
    생성·전파하게 하고, 비즈니스 맥락(userId 등)만 MDC에 최소한으로 남긴다.
  - 예외 응답: 커스텀 `Map<String, Object>` 대신 **`ProblemDetail`(RFC 9457)**을 사용한다.
  - QueryDSL 의존성: `com.querydsl`(관리 중단) 대신 **`io.github.openfeign.querydsl`**을 사용한다.
  - 스키마 변경: `ddl-auto=update` 대신 **Flyway 마이그레이션**을 사용한다.

## 개발 환경

- Java 17, Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3)
- 로컬 개발은 `infra/docker-compose.yml`의 PostgreSQL 17.6, Redis 8.6을 쓴다(운영 Supabase·Redis Cloud와 같은 버전). 접속 정보는 `DB_NAME`·`DB_USERNAME`·`DB_PASSWORD`·`DB_PORT`·`REDIS_PORT` 변수로 바꿀 수 있고, 없으면 로컬 전용 기본값(`ottnavi`)을 쓴다. 포트는 `127.0.0.1`에만 열린다. 프로필은 `local` / `prod`로 나눈다.
- 설정 파일은 YAML로 통일한다: `application.yml`, `application-local.yml`, `application-prod.yml`(`.properties`와 섞지 않는다. 둘이 함께 있으면 같은 키는 properties가 우선한다). 로컬 DB 접속 값은 `application-local.yml`에 compose와 같은 기본값을 `${DB_USERNAME:ottnavi}` 형태로 둬서 `.env` 없이도 실행되게 한다.
- production 환경변수는 하드코딩하지 않고 `${ENV_VAR}` 형태로 주입한다 (DB 접속 정보, OAuth 클라이언트 키,
  JWT 시크릿, TMDB API 키, 관리자 배치 토큰, SMTP 계정 등). 필요한 변수 이름은 루트 `.env.example`에 함께 추가한다.
- 운영 DB(Supabase)는 Session Pooler(포트 5432) 주소로 접속하고, 연결 풀 크기는 약 5로 제한한다.
- 스키마는 Flyway(`src/main/resources/db/migration`)로 관리한다. 개발 초기에는 `ddl-auto=create`로 엔티티를 다듬을 수 있지만, 운영은 `ddl-auto=validate`로 둔다.
- Jackson 3는 패키지가 `tools.jackson.*`로 바뀌었다. 예전 코드의 `com.fasterxml.jackson.databind` import를 그대로 옮기지 않는다 (어노테이션 패키지 `com.fasterxml.jackson.annotation`은 유지).

## 기술 스택

이 프로젝트에서 확정한 스택이다. 새 라이브러리를 추가할 때는 Spring Boot 4.1 호환 여부를 먼저 확인한다.

- **언어/프레임워크**: Java 17, Spring Boot 4.1, Gradle
- **DB/ORM**: PostgreSQL(Supabase), Spring Data JPA, Flyway, QueryDSL(필요할 때만, `io.github.openfeign.querydsl`)
- **인증**: Spring Security, OAuth2 Client(Google), JWT(jjwt). Refresh Token 저장은 Redis(TTL)를 잠정 기준으로 하며, 확정은 PRD 11절 21번 결정을 따른다
- **캐시/요청 제한**: Redis(Lettuce), Spring Cache, Bucket4j
- **외부 연동**: Spring Cloud OpenFeign(2025.1.2 이상, 클라이언트별 timeout 개별 설정)
- **배치/락**: Spring Batch 6(JDBC JobRepository), ShedLock(JDBC)
- **메일**: Spring Mail + Gmail SMTP를 쓴다(Cloudtype 발신 가능 여부는 PRD 11절 11번, 2026-10-09 Task 022 시험 성공으로 확정). 발송은 SMTP·HTTPS 메일 API 구현체를 교체할 수 있게 인터페이스로 분리한다. Thymeleaf 템플릿 (2단계)
- **LLM**: Spring AI 2.0 + Gemini 무료 등급, 관리자 기능 전용 (3단계)
- **모니터링**: Spring Actuator, Micrometer(OTLP) + Loki4j → Grafana Cloud
- **API 문서**: springdoc-openapi
- **테스트**: JUnit 5, AssertJ, Mockito, Testcontainers(PostgreSQL, Redis), WireMock, GreenMail

## 아키텍처

도메인별 패키지 안에 계층을 두는 구조(Layered per Domain)를 따른다. 패키지는 `docs/ERD.md`의 영역과 대응한다.

```
com.ottnavi
├── global/
│   ├── config/                # JPA, Redis, Feign, Batch, CORS 설정
│   ├── security/              # JWT, OAuth2 핸들러, 인증/인가 필터
│   ├── ratelimit/             # Bucket4j 필터
│   ├── error/                 # GlobalExceptionHandler, 커스텀 예외
│   └── common/                # 공통 응답(CommonResponse), 베이스 엔티티
├── infra/                     # 외부 시스템 연동
│   ├── tmdb/                  # OpenFeign 클라이언트, TMDB 응답 DTO
│   ├── mail/                  # 메일 발송 (2단계)
│   └── llm/                   # Spring AI 연동 (3단계)
├── {도메인}/                  # catalog, provider, product, user, wishlist, plan, notification
│   ├── controller/            # 컨트롤러 (문서 어노테이션이 많아지면 같은 폴더에 XxxControllerInterface)
│   ├── dto/                   # 프론트(JSON)와 주고받는 틀
│   │   ├── request/
│   │   └── response/
│   ├── service/               # 서비스 (흐름 조율: 트랜잭션, 도메인 호출)
│   │   └── model/             # 서버 안에서 서비스와 호출하는 쪽이 주고받는 값 묶음(record)
│   └── domain/                # 엔티티 영역
│       ├── entity/            # 엔티티와 복합 키 클래스
│       ├── repository/        # JPA 리포지토리
│       ├── enums/             # enum
│       └── rule/              # 순수 Java 규칙(판정·계산, 프레임워크 의존 없음. catalog)
├── engine/                    # 계산 엔진 (순수 Java, 프레임워크 의존 없음)
├── collect/                   # Spring Batch 수집 (업무 도메인이 아니라 Job 실행 입구라 domain·service 폴더가 없다)
│   ├── job/                   # Job 조립, Job 파라미터
│   └── step/                  # Step 조립, Reader·Writer·Tasklet
└── admin/                     # 관리자 전용 API (각 도메인 서비스를 호출)
```

- 각 도메인은 controller → service → domain 순으로 단방향 의존한다.
- `domain/rule`은 순수 업무 규칙, `service`는 흐름 조율(트랜잭션, 도메인 호출)이다. rule 클래스는 입력값만 받아 판단 결과를 돌려주고 DB·TMDB를 부르지 않으므로 DB 없이 테스트한다(예: `AvailabilityJudge`는 시청 단위 종류·한국 제공처·서비스 목록·제공처 대응표를 받아 서비스별 제공 상태를 돌려준다). 이전 프로젝트에서 서비스 메서드 안의 `if`문이던 판단을 따로 뺀 것이다. 각 rule 클래스 위에는 "이런 입력 → 이런 출력" 예시를 Javadoc으로 단다.
- `service/model`은 서버 안에서 서비스와 호출하는 쪽이 주고받는 값 묶음이고, `dto`는 프론트(JSON)와 주고받는 틀이다(이름을 겹치지 않게 `model`을 쓴다).
- `enum`은 자바 예약어라 패키지 이름이 될 수 없으므로 하위 패키지는 `enums`를 쓴다.
- 다른 도메인의 데이터가 필요하면 그 도메인의 서비스를 호출한다. 다른 도메인의 리포지토리를 직접 쓰지 않는다.
- 외부 API/LLM 호출 책임은 `infra`에 두고, 도메인 서비스는 `infra`의 클라이언트를 호출해 결과를 도메인 모델로 변환한다. 프롬프트 조립은 `infra/llm` 안에서 `XxxPromptService`로 분리한다.
- `plan`은 `engine`을 호출하는 유일한 도메인이다. `engine`은 다른 패키지를 참조하지 않는다.
- 공통 응답/예외/보안/요청 제한은 전부 `global`에 모아 도메인 간 중복을 없앤다.
- 패키지를 만들거나 옮기거나 지우거나 역할이 바뀌면 `docs/PACKAGE_MAP.md`를 같은 PR에서 고친다(폴더 지도와 보조 설명).
