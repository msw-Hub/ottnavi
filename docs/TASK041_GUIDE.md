# Task 041 작업 안내서 (TMDB Feign 클라이언트)

> 2026-10-11 작성. 기준 문서는 `docs/ROADMAP.md` Task 041(소비자: 042·043·044·050), TECH 3절(TMDB 호출 제한)·6절(호환성 함정)·T-2·T-3, PRD 5.5·FR-01~03·FR-18·6절 라이선스 행, ERD(`title`·`watch_unit`·`availability`·`tmdb_provider`), Task 004 기록(KR 제공처 ID), TASK003(TMDB 약관), `.claude/rules/backend.md`, `docs/TASK024_GUIDE.md`(Feign 공통 설정)이다.
> 이 Task는 사용자가 직접 작성한다. Claude(또는 `backend-dev` 에이전트)는 검수를 돕는다. 1~4절의 코드 조각은 구조를 설명하는 스케치지만, **5절(구현 순서)의 코드는 파일 단위 전체 코드**라 그대로 옮겨 쓰도록 썼다(2026-10-11 개정). 다만 저장소에 만들어 컴파일·실행해 본 것은 아니다. 라이브러리 시그니처는 소스 jar와 대조했고, 대조하지 못한 곳은 코드 옆에 `확인 필요(컴파일 시 확인)`·`확인 필요(실행 시 확인)`로 표시했다.
> 근거 수준 표시 (이 문서 전체에서 같은 뜻으로 쓴다).
>
> | 표시 | 뜻 |
> |---|---|
> | `c7` | context7으로 공식 문서를 확인함 (Spring Boot 4.1.0, Spring Cloud OpenFeign, Bucket4j, WireMock, Micrometer, TMDB 공식 문서 `developer.themoviedb.org`) |
> | 소스 확인 | 이 저장소가 실제로 쓰는 버전의 라이브러리 소스 jar를 로컬 Gradle 캐시에서 읽어 확인함 (feign-core 13.6.1, spring-cloud-openfeign-core 5.0.3, bucket4j 8.21.0, wiremock-standalone 3.13.2 jar 목록). 읽기만 했고 **실행해 보지는 않았다** |
> | 프로젝트 문서 | 이 저장소의 PRD·TECH·ROADMAP·TASK003·Task 004 기록에 적혀 있음 |
> | 확인 필요 | 위 방법으로 확인하지 못함. 이 Task의 테스트나 TMDB 공식 문서 직접 확인으로 해결한다 |

## 1. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 이 문서 읽는 법 | **5절은 파일 단위 전체 코드다.** 단계(①~⑦)마다 건드리는 파일 표 → 전체 코드 → 앞 단계와의 연결 → 테스트 → 확인할 것·흔한 오류 순서다. 예외 4종을 ①로 앞당기는 등 단계 순서를 조금 재배치했다(5.0.1) |
| 목적 | TMDB를 부르는 **하나의 통로**를 만든다. 인증, 호출 속도 제한, 실패 시 재시도, 호출 지표를 이 통로에서 한 번만 처리해서 042·043·044·050이 신경 쓰지 않게 한다 |
| 태그·선행 | [B] · 선행 Task 024(완료, Feign 공통 설정과 `spring.cloud.openfeign.client.config.tmdb` 타임아웃) · PRD B3, 5.5 |
| 브랜치 | `feature/b3-tmdb-client` (`develop`에서, 이미 만들어져 있음) |
| 시기 | MVP 2주차(10-15~10-21)의 041~048 중 첫 번째. 042·043이 이 Task를 기다린다 |
| 확정된 결정 (7절) | 버킷 `capacity 10 / 초당 25 / 최대 대기 2초`(D1), 재시도 429·5xx 3회·타임아웃 2회(D2), 404는 `TmdbNotFoundException`(D3), 가벼운 테스트 + 통합 테스트 1건(D4), 얇은 래퍼 `TmdbClient`(D5), 샘플 JSON은 가짜 값(D6), `TMDB_API_KEY` = 읽기 액세스 토큰(D7). 남은 미확정 없음 |
| 만드는 것 | `infra/tmdb` 패키지: Feign 인터페이스(10개 호출, **전체 제공처 목록 `listProviders` 포함**), 소비자용 래퍼, 응답 DTO, 인증 인터셉터, Bucket4j 로컬 버킷, `ErrorDecoder`, `Retryer`, `tmdb.call` 카운터, 설정 프로퍼티(`app.tmdb.*`), **TMDB 429 → `EXTERNAL_API_ERROR`(502) 매핑**, WireMock 테스트와 샘플 JSON(`src/test/resources/tmdb/`), 통합 테스트 1건. 코드 주석 정정 2곳(`application.yml`, `FeignConfig.java`: "Task 042" → 041) |
| 만들지 않는 것 | 제공 상태 판정·러닝타임 계산·시즌 0 제외(Task 042), 수집 Job·discover Step·`tmdb_provider` 저장(Task 043. 7곳 외 제공처를 "미매핑"으로 저장하는 일도 043의 몫이고, 041은 전체 목록을 받아 오는 호출까지), 시즌·상세 수집 Step(Task 044), `title`·`watch_unit` 등 엔티티와 Flyway(Task 033), 공개 요청 제한(IP별 Redis 버킷, Task 049), 검색 API(Task 050). 이 Task는 **TMDB 응답을 DTO로 받아 오는 데까지**다. DTO를 우리 도메인 모델로 바꾸는 일은 소비하는 Task의 몫이다 |
| 계약·프론트 영향 | 없다. `openapi.yaml`·`error-codes.md`·`ErrorCode`·`frontend/`를 바꾸지 않는다 |

| 완료 기준 (ROADMAP V-B) | 확인 방법 |
|---|---|
| 정상 응답이 DTO로 매핑된다 | `TmdbClientTest` 정상 케이스 |
| 429 후 재시도로 성공한다 | WireMock 시나리오(429 → 200) 테스트 |
| 타임아웃이 설정대로 동작하고 포기한다 | WireMock 지연 응답 테스트 |
| **실제 TMDB 호출이 없다** | 5.9절: `TMDB_API_KEY`를 비운 셸에서 테스트가 통과한다 |
| (공통) `develop` 대상 PR에서 `backend-ci`·`frontend-ci` 통과 | PR 화면 |

확인 명령 (PowerShell, 워크트리 기준):

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.*"   # 이 Task의 테스트만 (통합 테스트 1건 때문에 Docker Desktop을 켠다. 나머지는 Docker 없이 돈다, 5.7절·7절 D4)
.\gradlew.bat clean build                               # 마지막에 전체 (다른 테스트가 Testcontainers를 쓰므로 Docker Desktop을 켠다)
```

## 2. 왜 이렇게 설계하나

1. **TMDB 호출은 한 곳으로 모은다.** 소비자가 넷(042 판정, 043 후보 수집, 044 상세·시즌, 050 검색)이다. 각자 Feign을 직접 부르면 재시도·한도·지표가 제각각이 된다. 속도 제한과 재시도는 **Feign 인터페이스 앞뒤(인터셉터·`Retryer`)** 에, 지표와 예외 변환은 **얇은 래퍼 한 곳**에 둔다(5.2절, D5).
2. **외부 호출은 트랜잭션 밖이 원칙이다**(`backend.md`, TECH T-2). 예외는 **수집 배치뿐**이다. 배치 chunk 안에서 TMDB를 부르면 그동안 DB 연결(풀 약 5개, `application.yml`의 `maximum-pool-size: 5`)을 잡고 있다. 그래서 이 Task에서 **한 번의 호출이 최악에 얼마나 오래 걸릴지**(타임아웃 × 재시도 횟수 + 대기)를 설정으로 제한한다(5.5절 표). 검색 단건 수집(Task 050)은 배치가 아니므로 트랜잭션 밖에서 부른다. 이 규칙을 지키는 일은 호출하는 쪽의 몫이고, 이 Task는 호출이 **빨리 끝나거나 빨리 포기하게** 만든다.
3. **호출 제한은 두 겹으로 나뉜다. 이 Task는 한 겹뿐이다.**
   - 이 Task의 **로컬 버킷**: TMDB가 알려 준 한도(초당 약 40회, `c7` TMDB Rate Limiting 문서)를 **서버 전체**가 넘지 않게 하는 안전망이다. 한 JVM 안의 메모리 버킷이다(TECH 3절: Redis 버킷을 쓰면 TMDB 호출마다 Redis 연산이 생겨 Redis Cloud 무료 초당 100 ops를 수집이 다 쓴다).
   - Task 049의 **IP별 Redis 버킷**(일반 분당 60회, 단건 수집 분당 10회, R11-12): 익명 방문자 한 명이 TMDB 호출을 마구 일으키지 못하게 하는 장치다. 소비 쪽(049·050)이 만든다. 이 Task에서 만들지 않는다.
   - 둘은 **다른 문제**다. 단건 수집 요청이 049를 통과한 뒤에도 이 Task의 로컬 버킷을 지나므로(TECH 3절), 배치가 한도를 많이 쓰고 있으면 사용자 요청이 잠깐 기다리거나 실패할 수 있다. 이때 사용자에게 어떻게 보일지(DB 결과만 표시, FR-01)는 050이 정한다.
4. **재시도할 실패와 안 할 실패를 나눈다.** 일시적인 실패(429 요청 과다, 5xx 서버 오류, 연결·읽기 타임아웃)만 다시 시도한다. 인증 실패(401)·잘못된 요청(4xx)·없는 리소스(404)는 몇 번을 다시 해도 같으므로 바로 알린다. 재시도하면 TMDB 한도와 DB 연결을 더 쓰기만 한다.
5. **토큰은 로그에 남기지 않는다.** 요청 헤더 `Authorization: Bearer ...`에 토큰이 들어간다. `application.yml`의 `loggerLevel: basic`은 요청 줄·상태·시간만 남기고 헤더는 남기지 않는다. 그리고 **Feign의 `Request` 객체를 문자열로 찍지 않는다**(`Request.toString()`이 헤더 전체를 출력한다, 소스 확인).
6. **Jackson 3 + 실제 TMDB 응답의 현실**: 응답에는 우리가 쓰지 않는 필드가 수십 개 있고, `first_air_date`가 `""`(빈 문자열)로 오는 경우가 있다(`c7` TMDB 문서 예시). 6절 함정 1·2를 먼저 읽는다.

## 3. 사전 조사 결과

### 3.1 Spring Cloud OpenFeign (`c7` + 소스 확인)

| 사실 | 근거 |
|---|---|
| 클라이언트별 설정은 `spring.cloud.openfeign.client.config.<이름>`이고 `<이름>`은 `@FeignClient`의 `value`/`name`/`contextId`와 같아야 한다. `connectTimeout`·`readTimeout`(ms)·`loggerLevel`·`errorDecoder`·`retryer`·`requestInterceptors`·`dismiss404`·`defaultRequestHeaders` 등을 줄 수 있다 | `c7` |
| 설정 속성과 `@Configuration` 빈이 같은 값을 정하면 **속성이 이긴다**(`default-to-properties=false`로 뒤집을 수 있음). 그래서 타임아웃·로그 수준은 yml에만 둔다(TASK024와 같은 방침) | `c7` |
| 기본 `Retryer`는 `Retryer.NEVER_RETRY`다. **재시도하려면 `Retryer` 빈을 직접 등록해야 한다**(Feign 자체 기본값과 다름) | `c7` |
| `Retryer`·`ErrorDecoder`·`Collection<RequestInterceptor>`·`Capability` 같은 빈은 해당 클라이언트의 설정 클래스(`@FeignClient(configuration = ...)`)에서 등록하면 그 클라이언트에 적용된다. `@Configuration`을 붙인 설정 클래스가 컴포넌트 스캔에 잡히면 **모든 Feign 클라이언트에 적용**되므로 스캔되지 않게 하라는 경고가 있다 | `c7` |
| `url`을 `@FeignClient(url = "${...}")`처럼 **플레이스홀더**로 줄 수 있다. 어노테이션에 url이 있으면 로드밸런싱을 쓰지 않는다 | `c7` |
| 기본 디코더는 Spring 7의 `HttpMessageConverters.forClient()`와 Boot 4의 `ClientHttpMessageConvertersCustomizer`를 쓴다. 즉 **Jackson 3 컨버터를 탈 구조**다. 다만 실제 JSON 매핑이 되는지는 **실행해 보지 않았다** → 이 Task의 첫 테스트가 확인 | 소스 확인(`FeignClientsConfiguration`, `FeignHttpMessageConverters`) |
| 별도 HTTP 클라이언트 의존성(HttpClient5 등)이 `build.gradle`에 없고, 로드밸런서 쪽 기본이 `new Client.Default(null, null)`이다. 따라서 **Feign 기본 클라이언트(HttpURLConnection 기반)** 를 쓰는 것으로 보인다 | 소스 확인(`DefaultFeignLoadBalancerConfiguration`). 실제로 타임아웃이 그 클라이언트에 적용되는지는 5.6절 `TmdbTimeoutTest`(T9)로 확인 |
| `Capability` 타입 빈은 클라이언트에 자동으로 붙는다 (이 Task는 쓰지 않음) | 소스 확인(`FeignClientFactoryBean`) |

### 3.2 Feign이 재시도를 다루는 방식 (소스 확인, feign-core 13.6.1)

이 Task의 핵심이라 자세히 적는다. 아래는 모두 소스를 읽어 확인한 내용이다.

1. 호출이 실패하면 **`RetryableException`** 이 던져질 때만 `Retryer`가 개입한다. `Retryer.continueOrPropagate(e)`가 (a) 기다렸다가 그냥 반환하면 **다시 시도**하고 (b) 예외를 던지면 **포기**한다.
2. `Retryer`는 호출마다 `clone()`된다. 즉 시도 횟수 같은 **상태를 필드에 가져도 된다**(호출마다 새 인스턴스).
3. `RetryableException`이 되는 경우는 둘이다.
   - `ErrorDecoder`가 `RetryableException`을 돌려줄 때. Feign 기본 `ErrorDecoder.Default`는 **`Retry-After` 헤더가 있는 응답만** `RetryableException`으로 감싼다(상태 코드는 따지지 않는다). 헤더가 없으면 일반 `FeignException`이라 재시도되지 않는다. → 429·5xx를 재시도하려면 `ErrorDecoder`를 직접 만들어야 한다.
   - **`IOException`(연결 실패, 읽기 타임아웃, 연결 끊김)** 이 나면 Feign이 알아서 `RetryableException`(status = -1)으로 감싼다. → **타임아웃도 `Retryer`를 거친다.** 즉 `readTimeout: 5000`이어도 시도 3회면 최악 15초 이상을 쓴다(5.5절).
4. `Retry-After`는 `ErrorDecoder.Default`가 해석한다. 초 단위 숫자("2")와 HTTP 날짜 형식을 모두 읽어 **절대 시각(epoch ms)** 으로 바꿔 `RetryableException.retryAfter()`에 넣는다. Feign 기본 `Retryer.Default`는 이 값이 있으면 "그때까지 기다리되 `maxPeriod`로 자른다"(길게 요구해도 잘라서 기다린 뒤 다시 시도한다).
5. 요청 인터셉터(`RequestInterceptor`)는 **시도할 때마다** 실행된다(`executeAndDecode`가 매번 `targetRequest(template)`를 부름). 이 성질로 "**재시도도 버킷 토큰을 쓴다**"가 자연스럽게 된다(6절 함정 6).
6. 헤더를 같은 값으로 다시 `template.header(...)`해도 값이 중복되지 않는다(내부가 `LinkedHashSet`). 인터셉터를 시도마다 실행해도 `Authorization`이 두 번 붙지 않는다.
7. 응답을 DTO로 바꾸다 실패하면(JSON 형식 불일치 등) `DecodeException`(`FeignException` 하위)이 나온다. `RetryableException`이 아니라서 재시도되지 않는다.

### 3.3 Bucket4j 8.21.0 (`c7` + 소스 확인)

```java
Bucket bucket = Bucket.builder()
    .addLimit(limit -> limit.capacity(10).refillGreedy(25, Duration.ofSeconds(1)))
    .build();
bucket.tryConsume(1);                                  // 토큰이 없으면 즉시 false (기다리지 않음)
bucket.asBlocking().tryConsume(1, Duration.ofSeconds(2)); // 최대 2초까지 기다려서 얻으면 true. InterruptedException을 던진다
```

- 버킷이란: **"토큰이 든 통"** 이다. 호출 한 번에 토큰 한 개를 쓰고, 통은 정해진 속도로 다시 채워진다(`refillGreedy`는 시간에 비례해 조금씩 채움). 토큰이 없으면 호출을 못 한다.
- `tryConsumeAndReturnRemaining`은 `ConsumptionProbe`를 돌려줘서 "실패하면 몇 나노초 뒤에 가능"(`getNanosToWaitForRefill`)까지 알려 준다(`c7`). 차단 API(`asBlocking`)는 소스에서 `tryConsume(long, Duration)`·`tryConsume(long, long)`·`consume(long)`·`tryConsumeUninterruptibly(...)`가 있음을 확인했다.
- **함정: 통의 크기(capacity)가 순간 폭주를 허용한다.** 통이 가득 차 있으면 `capacity`개를 한꺼번에 쓸 수 있으므로, 어떤 1초 구간에서도 최대 호출 수는 약 `capacity + refill 속도 × 1초`다. 한도 40 아래를 지키려면 **둘의 합**이 40 미만이어야 한다(확정값 5.3절·7절 D1: 10 + 25 = 35).

### 3.4 WireMock (wiremock-standalone 3.13.2) (`c7` + 소스 확인)

- 응답 지연: `aResponse().withFixedDelay(2000)`. 고정 지연이 읽기 타임아웃보다 길면 클라이언트가 타임아웃으로 끊는다.
- 시나리오(같은 URL이 처음엔 429, 다음엔 200): `inScenario("이름").whenScenarioStateIs(Scenario.STARTED).willReturn(...).willSetStateTo("다음 상태")`.
- 호출 횟수 검증: `verify(exactly(3), getRequestedFor(urlPathEqualTo("...")))`. 헤더·쿼리 검증은 `.withHeader(...)`·`.withQueryParam(...)`.
- JUnit 5 확장 `WireMockExtension`, `wireMockConfig().dynamicPort()`: `c7` 확인. standalone jar 안에 `junit5/WireMockExtension`, `http/Fault`, `WireMockServer`가 있음을 확인했다(소스 확인).
- **Windows 주의**: `Fault.CONNECTION_RESET_BY_PEER`는 `c7` 문서에 "Windows에서는 연결이 리셋되지 않고 멈출 가능성이 크다"고 적혀 있다. 이 프로젝트 개발 환경이 Windows 11이므로 **연결 끊김 테스트는 쓰지 않고**, 지연 응답으로 타임아웃을 시험한다.
- **TECH 6절의 Jetty 이슈**(WireMock 기본 아티팩트가 Jetty 11이라 Boot BOM의 Jetty 12.1과 충돌)는 Task 016에서 셰이딩된 `wiremock-standalone`을 골라 피했다. 2026-10-11 `WireMockSmokeTest`로 기동을 확인했다(5.0단계, tests=1 failures=0). TECH 6절 해당 행은 이 실측으로 갱신해야 한다.

### 3.5 Jackson 3 (`c7` + 소스 확인)

- 패키지는 `tools.jackson.*`이고 **어노테이션만 `com.fasterxml.jackson.annotation` 그대로**다(`@JsonProperty`, `@JsonIgnoreProperties`). spring-cloud-openfeign-core 5.0.3 소스도 같은 방식으로 import한다.
- **Spring Boot 4.1에서 `FAIL_ON_UNKNOWN_PROPERTIES`는 켜져 있다**(기본). Boot가 이 옵션을 끄는 것은 `spring.jackson.use-jackson2-defaults=true`일 때뿐이다(`c7`, `JacksonAutoConfiguration` 소스). → TMDB DTO에 모르는 필드가 오면 매핑이 실패한다. 6절 함정 1.

### 3.6 Micrometer (`c7`)

- `Counter.builder("tmdb.call").tags("endpoint", "...", "result", "...").register(registry).increment()`. `SimpleMeterRegistry`는 메모리에만 값을 두므로 테스트에서 쓴다.
- 태그 값이 무한히 늘어나는 것(작품 ID, URL, 검색어)을 태그로 쓰면 지표 시스템이 터진다(고카디널리티). 태그는 **종류가 정해진 문자열만** 쓴다.

### 3.7 TMDB API 자체 (`c7` 공식 문서)

`context7`의 `developer.themoviedb.org` 문서로 확인한 것과 못 한 것을 구분한다. 4절 표에 호출별로 다시 적었다.

| 항목 | 상태 |
|---|---|
| 인증: **API 읽기 액세스 토큰을 `Authorization: Bearer` 헤더**로 보내는 것이 권장 방식이고 v3·v4 양쪽에서 동작한다 | `c7` 확인. Task 004 기록과 일치(토큰을 `api_key` 쿼리로 보내면 401) |
| 호출 한도: 구식 한도는 없어졌고 "대량 수집 방지용 상한이 **현재 초당 약 40회**, 바뀔 수 있으며 429를 올바로 처리해야 한다" | `c7` 확인 |
| 오류 상태: 401(인증), 422(잘못된 파라미터), 429(과다), 500·504 등 | `c7` 확인 (오류 본문 구조는 확인 필요) |
| 429 응답에 `Retry-After` 헤더가 **오는지** | **확인 필요**(문서에 없음). 그래서 헤더가 없을 때의 지수 백오프가 반드시 동작해야 한다 |
| 각 엔드포인트의 경로·쿼리 파라미터·응답 필드 | 4절 표 참고 |

## 4. 소비자가 필요로 하는 호출

원칙: **응답 DTO는 `infra/tmdb`(하위 `dto`) 안에만** 둔다. 래퍼 `TmdbClient`의 반환 타입이 이 DTO이므로 소비 Task는 DTO를 **받아서 바로 자기 도메인 모델(엔티티 값·`PlanInput` 등)로 변환**하고, DTO를 필드·파라미터로 오래 들고 다니거나 `api` 계층 응답에 그대로 내보내지 않는다(`backend.md` DTO 절). 이 Task는 변환 로직을 만들지 않는다. 특히 아래는 **소비 Task가 정한다**(여기서 해석하지 않는다).
- KR `flatrate`에 매핑 서비스가 있는지로 AVAILABLE 판정, KR 없음 = `NO_KR_DATA`, 쿠팡플레이 영화 = `STRUCTURAL_GAP` (Task 042)
- 시즌 0 제외, 러닝타임 추정(120/45/24분), 한국어 줄거리 없으면 영어로 대체, 출연진 상위 10명 자르기 (Task 042)
- `tmdb_provider` 미매핑 저장 (Task 043): 전체 제공처 목록에서 우리 7곳이 아닌 제공처는 버리지 않고 "미매핑"으로 저장하고, 관리자가 나중에 "관련 없음"으로 정리한다(8절 1번)

표의 "파라미터"는 TMDB에 보내는 쿼리다. 고정값(항상 같은 값)은 래퍼가 채운다. 응답 필드는 **우리가 쓰는 것만** DTO에 둔다(나머지는 `ignoreUnknown`으로 버림).

| # | 래퍼 메서드(제안) | TMDB 경로 | 쓰는 Task / 이유 | 파라미터와 확인 수준 | 응답에서 쓰는 필드 |
|---|---|---|---|---|---|
| 1 | `discoverMovies(withWatchProviders, page)` | `GET /3/discover/movie` | 043: 서비스별 KR 구독 후보 작품 수집(PRD 5.5, FR-18). 상태 판정에는 쓰지 않는다(FR-02: discover는 후보 수집 전용) | `with_watch_providers`(쉼표 AND·파이프 OR 가능), `watch_region=KR`, `with_watch_monetization_types=flatrate`, `page`, `language=ko-KR`: **`c7` 확인**. `sort_by` 기본값은 `popularity.desc`(`c7`). `include_adult` 기본 false(`c7`). 파이프(`\|`) OR 값이 Feign 인코딩 뒤에도 TMDB에서 통하는지: **확인 필요**(WireMock으로는 확인 불가, 043 착수 전 로컬 1회 호출로) | `page`, `total_pages`, `total_results`, `results[]`: `id`, `title`, `original_title`, `overview`, `poster_path`, `backdrop_path`, `release_date`, `original_language`, `popularity`, `vote_average`, `genre_ids`(`c7` 확인) |
| 2 | `discoverTvs(withWatchProviders, page)` | `GET /3/discover/tv` | 043: 위와 같음(드라마) | 영화와 같은 파라미터: `c7` 확인. `watch_region`은 `with_watch_*`와 함께 써야 한다(`c7`). **페이지 상한(TMDB는 500쪽까지만 준다는 알려진 제한)은 `c7`로 확인하지 못함 → 확인 필요**, 043이 `min(total_pages, 상한)`으로 멈추게 한다 | `results[]`: `id`, `name`, `original_name`, `overview`, `poster_path`, `backdrop_path`, `first_air_date`(**빈 문자열 가능**, `c7` 예시), `original_language`, `popularity`, `vote_average`, `genre_ids`, `origin_country` |
| 3 | `searchMovies(query, page)` | `GET /3/search/movie` | 050: DB에 없는 작품의 단건 수집(FR-01) | `query`(필수), `language`, `page`: `c7` 확인. `include_adult=false` 지정 여부: 기본값(`c7`: 문서에 따라 다름)에 기대지 말고 명시. 검색어는 사용자 입력이라 **로그·태그에 쓰지 않는다** | 1번과 같은 `results[]` 필드 + `total_pages` |
| 4 | `searchTvs(query, page)` | `GET /3/search/tv` | 050: 위와 같음(드라마) | `query`, `language`, `page`, `first_air_date_year`·`year`(선택): `c7` 확인 | 2번과 같은 필드 + `total_pages`, `total_results` |
| 5 | `getMovie(movieId, language, appendToResponse)` | `GET /3/movie/{movie_id}` | 042·044: 영화 상세(러닝타임, 장르, 개봉일, 줄거리, 포스터) + 출연진 | `language`(`ko-KR`, 줄거리 빈 값이면 `en-US`로 한 번 더: 042 몫), `append_to_response=credits`(최대 20개 쉼표 목록): **`c7` 확인**. `credits`를 붙이면 호출 1회로 출연진까지 받는다(R11-26 호출량 절감). 한국어 줄거리가 **`""`로 오는지 `null`로 오는지**: 확인 필요(샘플로 확인) | `id`, `title`, `original_title`, `overview`, `poster_path`, `backdrop_path`, `release_date`, `runtime`(**null·0 가능**), `genres[].id/name`, `original_language`, `popularity`, `vote_average`, `credits.cast[]`: `id`, `name`, `character`, `order`, `profile_path` |
| 6 | `getTv(tvId, language, appendToResponse)` | `GET /3/tv/{series_id}` | 044: 드라마 상세 + **시즌 목록** + 출연진 | `language`, `append_to_response`: `c7` 확인. 드라마의 `credits`는 **"가장 최근 시즌"의 출연진**이다(`c7`: `/tv/{id}/credits` 설명). 전체 시즌 합산은 `aggregate_credits`(별도 모양): **어느 쪽을 쓸지 042 결정, 확인 필요**. `append_to_response=season/1,season/2`처럼 시즌 상세를 한 번에 붙이는 방법은 `c7`로 확인하지 못함 → 확인 필요(R11-26 호출량을 줄일 후보, 044가 판단) | `id`, `name`, `original_name`, `overview`, `poster_path`, `backdrop_path`, `first_air_date`, `status`, `original_language`, `popularity`, `vote_average`, `number_of_seasons`, `genres[]`, `episode_run_time[]`, `seasons[]`: `id`(=`tmdb_season_id`), `season_number`, `name`, `air_date`, `episode_count`, `poster_path`(시즌 0도 목록에 포함됨, `c7` 예시. 거르는 것은 042), `credits.cast[]` |
| 7 | `getTvSeason(tvId, seasonNumber, language)` | `GET /3/tv/{series_id}/season/{season_number}` | 044: 시즌 회차별 러닝타임(방영된 회차 합) | `language`: `c7` 확인. `episodes[].runtime`이 **null일 수 있음**(ERD `runtime_missing_count`, 042가 120/45/24분 추정). 시즌 응답 자체의 정수 `id`(시즌 ID) 존재: `c7` 예시에는 `_id`(문자열)만 보임 → 확인 필요(없으면 6번의 `seasons[].id`를 쓴다) | `season_number`, `name`, `air_date`, `poster_path`, `episodes[]`: `episode_number`, `air_date`, `runtime`, `name`, `episode_type`(예: standard) |
| 8 | `getMovieWatchProviders(movieId)` | `GET /3/movie/{movie_id}/watch/providers` | 042: 영화의 KR 제공처로 3상태 판정 (FR-02) | 파라미터 없음. 응답은 나라 코드를 키로 하는 맵: **`c7` 확인** | `results` 맵 `{국가코드 → {link, flatrate[], rent[], buy[]}}`. 각 항목 `provider_id`, `provider_name`, `logo_path`, `display_priority`. **`ads`·`free`가 같은 구조로 더 올 수 있음**(discover의 `with_watch_monetization_types`가 `flatrate, free, ads, rent, buy`를 받는다는 `c7` 기록으로 추정, 응답에 실제로 오는지는 확인 필요). 이 필드들은 DTO가 몰라도 `ignoreUnknown`으로 안전 |
| 9 | `getTvSeasonWatchProviders(tvId, seasonNumber)` | `GET /3/tv/{series_id}/season/{season_number}/watch/providers` | 042: 시즌별 KR 제공처 판정(PRD 8.2 시즌별 상이) | `language`(선택): `c7` 확인. 응답은 8번과 같은 모양(`c7` 예시는 `flatrate`만 보여 줌, `rent`·`buy`도 오는지는 확인 필요) | 8번과 같다 |
| 10 | `listProviders(mediaType)` → `/3/watch/providers/movie`·`/tv` | `GET /3/watch/providers/movie`·`tv` | 043: **미매핑 제공처 발견**(`tmdb_provider`에 `first_seen_at`과 함께 저장), Task 004가 ID를 얻은 호출 | `watch_region=KR`, `language=ko-KR`: **`c7` 확인**. 우리는 7곳만 다루므로 이 전체 목록을 받아 7곳 외 제공처를 "미매핑"으로 저장한다(8절 1번, **확정**). 이 Task는 목록을 받아 오는 호출까지 **구현 범위에 포함**하고, 저장은 043이 한다 | `results[]`: `provider_id`, `provider_name`, `logo_path`, `display_priorities`(국가별 맵) |

호출 횟수 감각 (R11-26 측정용 메모): 영화 1편 = 상세 1(+출연진 포함) + 제공처 1 = 2회. 드라마 1편 = 상세 1 + (시즌 상세 1 + 시즌 제공처 1) × 시즌 수. 후보가 수천 편이면 호출이 수만 회가 되므로 속도 제한(5.3절)이 수집 시간을 정한다. 초당 25회로 제한하면 1만 회에 약 7분이다.

**DTO 모양 규칙**

```java
// 모든 TMDB DTO에 공통: 모르는 필드는 버린다(Boot 4 + Jackson 3은 기본이 실패, 3.5절)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieSummary(
        long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("release_date") String releaseDate,   // 날짜도 String. 빈 문자열("")이 올 수 있어 LocalDate로 받지 않는다
        Integer runtime) {                                   // null이 올 수 있는 숫자는 Integer
}
```

- 필드 이름 변환은 `@JsonProperty("snake_case")`로 명시한다. 이 어노테이션은 `com.fasterxml.jackson.annotation` 그대로라 Jackson 3에서도 안전하다. 이름 규칙 자동 변환(`@JsonNaming`)은 Jackson 3에서 패키지가 바뀌었을 가능성이 있어 **확인 필요**라 권하지 않는다.
- 날짜(`release_date`, `first_air_date`, `air_date`)는 **`String`** 으로 받는다. 빈 문자열(`""`)이 오는 것을 `c7` TMDB 예시로 확인했고, Jackson 3이 `""`를 `LocalDate`로 바꿀 때의 기본 동작은 확인하지 못했다. 문자열 → `LocalDate` 변환은 소비 Task(042)가 `isBlank()`를 먼저 보고 한다.
- 목록 응답은 제네릭 한 개로 묶는다: `TmdbPage<T>(page, results, @JsonProperty("total_pages") totalPages, @JsonProperty("total_results") totalResults)`.
- 제공처 응답: `TmdbWatchProvidersResponse(long id, Map<String, TmdbRegionProviders> results)`, `TmdbRegionProviders(String link, List<TmdbProviderRef> flatrate, rent, buy)`. 전체 DTO 16개의 최종 코드는 5.2절이 기준이다(이 절의 스케치와 다르면 5.2절을 따른다). 국가 코드를 키로 한 맵이고 KR만 골라 쓰는 일은 소비 Task가 한다. 목록이 빠진 경우(`flatrate` 없음)는 `null`로 온다 → 소비 쪽이 null을 빈 목록으로 본다.

## 5. 구현 순서 (단계마다 멈춰서 확인한다)

**이 절은 파일 단위 전체 코드다.** 단계마다 (1) 건드리는 파일 표 (2) 파일별 전체 코드 (3) 앞 단계와의 연결 (4) 테스트 (5) 멈추고 확인할 것과 흔한 오류 순서로 쓴다. 패키지는 `com.ottnavi.infra.tmdb`(하위 `dto`, `exception`)이고 들여쓰기는 4칸 공백이다(기존 `infra/tmdb` 파일과 같다. `global/` 쪽 일부 파일은 탭이지만 이 Task의 파일은 모두 공백으로 통일한다).

**코드의 근거 수준**: 코드를 저장소에 만들지 않았고 컴파일·실행도 하지 않았다. 대신 import·시그니처를 로컬 Gradle 캐시의 소스 jar(feign-core 13.6.1, spring-cloud-openfeign-core 5.0.3, bucket4j 8.21.0, wiremock-standalone 3.13.2)와 Boot 4.1.1 jar 목록으로 대조했다. 소스 jar로 존재를 확인하지 못한 곳, 동작을 실행해 봐야 아는 곳은 코드 옆에 **`확인 필요(컴파일 시 확인)`** 또는 **`확인 필요(실행 시 확인)`** 로 적었다.

### 5.0.0 이 절을 쓰는 데 확인한 실제 저장소 사실 (추측 아님)

| 확인한 것 | 내용 |
|---|---|
| 의존성 (`backend/build.gradle`) | `spring-cloud-starter-openfeign`(Cloud 2025.1.3 BOM → openfeign-core 5.0.3, feign-core 13.6.1), `bucket4j_jdk17-core:8.21.0`, `spring-boot-starter-actuator`(Micrometer), Lombok, 테스트 `wiremock-standalone:3.13.2`, `spring-boot-starter-webmvc-test`(AssertJ·`OutputCaptureExtension`). **이 Task에서 `build.gradle`을 바꾸지 않는다** |
| 예외 기반 | `BusinessException`은 `abstract`이고 생성자 `protected (message)`·`protected (message, cause)` 두 개, 추상 메서드 `ErrorCode getErrorCode()` 하나다 |
| 오류 코드 | `ErrorCode.EXTERNAL_API_ERROR`(502), `TITLE_NOT_FOUND`(404, "작품을 찾을 수 없습니다"), `RESOURCE_NOT_FOUND`(404, "요청한 경로를 찾을 수 없습니다"), `RATE_LIMIT_EXCEEDED`(429) |
| 예외 응답 | `GlobalExceptionHandler.handleBusinessException`은 `ex.getMessage()`를 응답 `detail`로 내보낸다 → TMDB 예외 메시지는 고정 문구만 쓴다 |
| Feign 활성화 | `FeignConfig`(`global/config`)가 `@EnableFeignClients(basePackages = "com.ottnavi.infra")`다 |
| 프로퍼티 클래스 | 이 저장소에 `@ConfigurationProperties`를 쓰는 곳이 없다(`OriginSecretFilter`는 `@Value`). `@ConfigurationPropertiesScan`도 없다 → **`TmdbConfig`에 `@EnableConfigurationProperties(TmdbProperties.class)`** 로 등록한다(스캔보다 범위가 좁다) |
| 테스트 | `IntegrationTestSupport`는 `@SpringBootTest` + `@Import(TestcontainersConfig.class)`(PostgreSQL 17.6·Redis 8.6 컨테이너)다. `backend/src/test/resources/`는 **아직 없다**(이 Task가 `tmdb/` 폴더를 처음 만든다) |
| 설정 | `application.yml`에 `spring.cloud.openfeign.client.config.tmdb`(connectTimeout 3000, readTimeout 5000, loggerLevel basic)와 `app.tmdb.*`(base-url, api-token, rate-limit, retry)가 **이미 작업 트리에 들어 있다**. `.env.example`에는 `TMDB_API_KEY=`가 이미 있다 |
| 작업 트리 | `git status` 기준 `infra/tmdb`의 뼈대 파일 13개가 스테이징돼 있다. 아래 5.0.1에 어떤 파일이 컴파일되지 않는지 적었다 |

### 5.0.1 단계 순서 (재배치 결과)와 작업 트리 정리

**순서 재배치**: 예외 4종(`TmdbException` + 3종)을 **⑥에서 ①로 앞당겼다.** ③의 `TmdbRateLimiter`가 `TmdbUnavailableException`을 던지는데 ⑥까지 기다리면 ③을 컴파일할 수 없고, 부모가 `BusinessException`이라 `ErrorCode` 매핑을 처음부터 한 곳에 박아 두는 편이 낫다. ⑥에는 `TmdbClient`의 예외 변환과 `tmdb.call` 카운터만 남는다. 이 순서에 맞춰 9.1 체크리스트를 고쳤다.

| 단계 | 한 줄 | 새로 만드는 파일 | 통과시킬 테스트 |
|---|---|---|---|
| 5.0 | 완료 | `WireMockSmokeTest`(있음) | tests=1 failures=0 (2026-10-11) |
| ① 5.1 | 기반: 프로퍼티·예외·인증·주석 정정 | `TmdbProperties`, `TmdbConfig`, `TmdbFeignConfig`, 예외 4종, `TmdbTestFixtures` | T18, T21 |
| ② 5.2 | Feign 인터페이스·DTO·얇은 래퍼·샘플 JSON | `TmdbApi`, `TmdbMediaType`, `TmdbClient`(얇은 판), DTO 16개, 샘플 JSON 7개, `TmdbTestSupport` | T1, T2, T3, T10, T19 |
| ③ 5.3 | 로컬 버킷 | `TmdbRateLimiter`, `TmdbConfig`(완성) | T16 |
| ④ 5.4 | ErrorDecoder | `TmdbErrorDecoder` | T22 |
| ⑤ 5.5 | Retryer | `TmdbRetryer`, `TmdbFeignConfig`(완성) | T23, T4, T5, T14 |
| ⑥ 5.6 | 예외 변환·`tmdb.call` 카운터 | `TmdbClient`(완성판으로 교체) | T6, T7, T8, T9, T11, T12, T13, T15, T17, T24 |
| ⑦ 5.7 | 통합 테스트·전체 확인 | `TmdbClientIntegrationTest` | T20 + 전체 |

**테스트 번호 대응**: T1~T20은 5.7 기존 표의 번호를 그대로 쓴다. 단계별로 쓰다 보니 필요해진 보조 테스트 4개는 T21~T24로 새로 붙였다(T21 프로퍼티 `toString`, T22 `ErrorDecoder` 단위, T23 `Retryer` 단위, T24 응답 해석 실패). **T10(설정값 확인)은 ②의 `TmdbClientTest`에 둔다.** T9(타임아웃)가 `readTimeout`을 300으로 줄인 별도 컨텍스트(`TmdbTimeoutTest`)를 쓰기 때문에, 공용 컨텍스트에서는 본 yml 값 5000·3000이 그대로 보인다.

**패키지 구조 (최종)**

```
backend/src/main/java/com/ottnavi/infra/tmdb/
├── TmdbClient.java              소비자가 쓰는 래퍼(@Component): 지표 기록 + 예외 변환
├── TmdbApi.java                 Feign 인터페이스(@FeignClient). 소비자는 쓰지 않는다
├── TmdbMediaType.java           제공처 전체 목록 호출용 enum(MOVIE/TV)
├── TmdbProperties.java          app.tmdb.* 설정 (record)
├── TmdbConfig.java              @Configuration: 설정 프로퍼티 등록, 버킷·속도 제한 빈
├── TmdbRateLimiter.java         Bucket4j 로컬 버킷
├── TmdbFeignConfig.java         Feign 설정 클래스. @Configuration을 붙이지 않는다(6절 함정 3)
├── TmdbErrorDecoder.java        429·5xx를 재시도 가능으로
├── TmdbRetryer.java             지수 백오프 + Retry-After
├── dto/                         응답 DTO 16개(record)
└── exception/                   TmdbException(추상), TmdbNotFoundException, TmdbRequestException, TmdbUnavailableException
backend/src/test/java/com/ottnavi/infra/tmdb/    테스트 (각 단계에서 만든다)
backend/src/test/resources/tmdb/                 샘플 JSON 7개 (②에서 만든다)
```

**작업 트리 정리 (① 시작 전에 한다)**. 실제 파일을 읽어 보니 뼈대 중 아래 세 개는 컴파일되지 않는다. 그대로 두면 ①~②의 테스트를 돌릴 수 없다.

| 파일 | 지금 상태 (실제 확인) | 정리 방법 |
|---|---|---|
| `TmdbApi.java` | `TmdbPage`·`TmdbTvSummary`·`TmdbMovieDetail` 등 아직 없는 클래스를 import 없이 참조하고, **파일 끝에 `}`가 하나 더 있다** | ②에서 5.2의 전체 코드로 **통째로 교체**한다. ①을 확인하는 동안은 `package` 줄만 남기고 나머지를 지워(또는 주석 처리해) 둔다 |
| `TmdbClient.java` | `TmdbPage` 참조(없음) | ②에서 교체. ①을 확인하는 동안은 `package` 줄만 남긴다 |
| `TmdbRateLimiter.java` | import와 필드(`bucket`, `maxWait`)가 없고 `TmdbUnavailableException`도 import하지 않았다 | ③에서 교체. ①을 확인하는 동안은 `package` 줄만 남긴다 |
| `TmdbErrorDecoder.java`, `TmdbRetryer.java`, `TmdbConfig.java` | 컴파일은 되지만 내용이 스케치·빈 클래스다 | ④·⑤·①에서 교체 |
| `exception/*.java` 4개 중 3개 | `RuntimeException`을 상속한다. `TmdbException`과 무관하고 `BusinessException`이 아니다 | ①에서 5.1의 전체 코드로 교체 |
| `dto/TmdbMovieSummary.java` | 컴파일된다. 단 `runtime` 필드는 검색·discover 결과에 없다(상세 응답에만 있음) | ②에서 교체 |

사용자의 "옛 코드는 지우지 않고 주석 처리로 남긴다" 원칙은 **이 뼈대에는 적용하지 않는다**(아직 커밋되지 않은 스케치이고 아래 최종본이 상위 호환이다). 필요하면 `git stash`나 IDE 로컬 히스토리를 쓴다.

### 5.0 단계 0. 준비와 WireMock 기동 확인 — **완료 (2026-10-11)**

| 항목 | 결과 |
|---|---|
| 확인 테스트 | `src/test/java/com/ottnavi/infra/tmdb/WireMockSmokeTest.java` (동적 포트로 `WireMockServer`를 띄우고 스텁 `/ping`에 JDK `HttpClient`로 호출) |
| 결과 | **tests=1 failures=0** (사용자 실행, 2026-10-11). `wiremock-standalone:3.13.2`가 Boot 4.1.1 클래스패스에서 Jetty 충돌 없이 기동된다 |
| 후속 | ① `docs/TECH.md` 6절의 WireMock Jetty 행을 이 실측 결과로 갱신한다(9.1 체크리스트). ② PR 설명과 ROADMAP `기록:`에 결과를 적는다. ③ `WireMockSmokeTest`는 **⑦에서 지운다**(②의 `TmdbTestSupport`가 테스트마다 같은 서버 기동을 증명하므로 흡수된다) |

### 5.1 단계 ① 기반: 프로퍼티·예외·인증·주석 정정

#### 건드리는 파일

| 경로 (`backend/` 기준) | 구분 | 역할 |
|---|---|---|
| `src/main/java/com/ottnavi/infra/tmdb/TmdbProperties.java` | 수정(교체) | `app.tmdb.*` 바인딩 record. 토큰 가리는 `toString()` |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbConfig.java` | 수정(교체) | 프로퍼티 등록 (`@EnableConfigurationProperties`). ③에서 버킷 빈 추가 |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbFeignConfig.java` | 수정(교체) | Feign 설정 클래스. 인증 인터셉터. ③·④·⑤에서 빈 추가 |
| `src/main/java/com/ottnavi/infra/tmdb/exception/TmdbException.java` | 수정(교체) | 부모(추상). `BusinessException` 상속, `EXTERNAL_API_ERROR` 매핑 |
| `src/main/java/com/ottnavi/infra/tmdb/exception/TmdbUnavailableException.java` | 수정(교체) | 일시 장애 (429·5xx·타임아웃·로컬 한도 초과·응답 해석 실패) |
| `src/main/java/com/ottnavi/infra/tmdb/exception/TmdbRequestException.java` | 수정(교체) | 요청·설정 문제 (401·400·422 등) |
| `src/main/java/com/ottnavi/infra/tmdb/exception/TmdbNotFoundException.java` | 수정(교체) | TMDB에 없는 리소스 (404, D3) |
| `src/main/resources/application.yml` | 수정(2줄) | 주석 정정 1곳 + 옛 안내 주석 1줄 삭제 |
| `src/main/java/com/ottnavi/global/config/FeignConfig.java` | 수정(1줄) | 주석 정정 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbTestFixtures.java` | 신규 | 테스트용 `TmdbProperties` 생성 보조 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbFeignConfigTest.java` | 신규 | T18 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbPropertiesTest.java` | 신규 | T21 |
| `build.gradle`, `.env.example` (저장소 루트) | **변경 없음** | 필요한 의존성·`TMDB_API_KEY=`가 이미 있다 |

#### 파일별 전체 코드

**`TmdbProperties.java`**

```java
package com.ottnavi.infra.tmdb;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** TMDB 연동 설정(app.tmdb.*). 값은 application.yml에 두고 토큰만 환경 변수(TMDB_API_KEY)로 받는다 */
@ConfigurationProperties(prefix = "app.tmdb")
public record TmdbProperties(
        String baseUrl,        // TMDB API 주소. 끝의 /3까지 포함한다 (예: https://api.themoviedb.org/3)
        String apiToken,       // 읽기 액세스 토큰(Bearer). 기본값이 빈 문자열이라 토큰이 없어도 기동된다
        RateLimit rateLimit,   // 로컬 속도 제한(Bucket4j)
        Retry retry) {         // 재시도

    /** 로컬 버킷 설정. 한도 계산: capacity + 1초 동안 채워지는 토큰이 TMDB 한도(약 40)보다 작아야 한다 (10 + 25 = 35) */
    public record RateLimit(
            int capacity,           // 통 크기: 순간에 몰아 쓸 수 있는 최대 호출 수
            int refillTokens,       // refillPeriod마다 채워지는 토큰 수
            Duration refillPeriod,  // 채우는 주기
            Duration maxWait) {     // 토큰이 없을 때 최대 기다리는 시간
    }

    /** 재시도 설정 */
    public record Retry(
            int maxAttempts,          // 429·5xx의 총 시도 횟수(첫 시도 포함)
            int ioMaxAttempts,        // 타임아웃·연결 오류의 총 시도 횟수
            Duration initialBackoff,  // 첫 재시도 전 대기. 이후 두 배씩
            Duration maxBackoff,      // 지수 대기의 상한
            Duration maxRetryAfter) { // 서버가 이보다 오래 기다리라고 하면 기다리지 않고 포기
    }

    /** 토큰이 로그에 찍히지 않게 가린다. record 기본 toString()은 모든 필드를 출력한다 */
    @Override
    public String toString() {
        return "TmdbProperties[baseUrl=" + baseUrl + ", apiToken=***, rateLimit=" + rateLimit + ", retry=" + retry + "]";
    }
}
```

**`TmdbConfig.java`** (①의 모습. ③에서 완성본으로 바뀐다)

```java
package com.ottnavi.infra.tmdb;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** TMDB 연동 공통 빈. 설정 프로퍼티를 여기서 등록한다 */
@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbConfig {
}
```

**`TmdbFeignConfig.java`** (①의 모습. ③·④·⑤에서 빈이 늘어난다)

```java
package com.ottnavi.infra.tmdb;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * TMDB Feign 클라이언트 전용 설정.
 * @Configuration을 붙이지 않는다: 붙이면 컴포넌트 스캔에 잡혀 모든 Feign 클라이언트에 적용된다(6절 함정 3).
 * TmdbApi의 @FeignClient(configuration = TmdbFeignConfig.class)로만 연결한다.
 */
@Slf4j
public class TmdbFeignConfig {

    /** 모든 요청에 읽기 액세스 토큰을 Bearer로 붙인다. 토큰이 비어 있으면 헤더를 붙이지 않는다(호출은 TMDB가 401로 거절한다) */
    @Bean
    RequestInterceptor tmdbAuthInterceptor(TmdbProperties props) {
        String token = props.apiToken();
        if (token == null || token.isBlank()) {
            log.warn("TMDB 토큰이 비어 있다. TMDB_API_KEY 환경 변수를 설정하기 전까지 TMDB 호출은 401로 실패한다");
            return template -> { };
        }
        // 인터셉터는 재시도마다 다시 실행되지만 같은 값을 다시 넣어도 헤더가 중복되지 않는다(Feign이 값을 LinkedHashSet으로 보관)
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
```

**`exception/TmdbException.java`**

```java
package com.ottnavi.infra.tmdb.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/**
 * TMDB 호출 실패의 부모 예외. 하위 예외는 모두 EXTERNAL_API_ERROR(502)로 응답한다.
 * TMDB가 준 429도 여기로 모인다. RATE_LIMIT_EXCEEDED(429)는 우리 서비스의 공개 API 요청 제한이라 다른 개념이다(8절 6번).
 * 메시지는 응답 detail로 나갈 수 있으므로 고정 문구만 쓰고 URL·본문·토큰을 넣지 않는다. 원인은 cause로 남긴다.
 */
public abstract class TmdbException extends BusinessException {

    protected TmdbException(String message) {
        super(message);
    }

    protected TmdbException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.EXTERNAL_API_ERROR;
    }
}
```

**`exception/TmdbUnavailableException.java`**

```java
package com.ottnavi.infra.tmdb.exception;

/** TMDB가 일시적으로 쓸 수 없다: 429·5xx·타임아웃·연결 오류가 재시도 후에도 계속, 로컬 한도 대기 초과, 응답 해석 실패 */
public class TmdbUnavailableException extends TmdbException {

    public TmdbUnavailableException(String message) {
        super(message);
    }

    public TmdbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**`exception/TmdbRequestException.java`**

```java
package com.ottnavi.infra.tmdb.exception;

/** 요청이나 설정이 잘못됐다: 401(토큰)·400·422 등. 재시도해도 같은 결과라 바로 던진다 */
public class TmdbRequestException extends TmdbException {

    public TmdbRequestException(String message) {
        super(message);
    }

    public TmdbRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**`exception/TmdbNotFoundException.java`**

```java
package com.ottnavi.infra.tmdb.exception;

/** TMDB에 그 ID의 리소스가 없다(404, D3). 호출한 배치는 이 작품만 건너뛰고 계속한다(7.1절) */
public class TmdbNotFoundException extends TmdbException {

    public TmdbNotFoundException(String message) {
        super(message);
    }

    public TmdbNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**ErrorCode 매핑 결정 (기존 `ErrorCode`를 읽고 정했다)**: 세 예외 모두 `EXTERNAL_API_ERROR`(502)다. `TmdbNotFoundException`을 `TITLE_NOT_FOUND`(404)로 보내자는 안도 있지만 채택하지 않았다. `TITLE_NOT_FOUND`는 "우리 DB의 작품 ID를 못 찾았다"는 뜻이고, TMDB 404는 "우리가 TMDB에서 가져온 ID가 TMDB에서 사라졌다"는 외부 데이터 불일치라 호출한 사용자의 잘못이 아니다. 정상 흐름에서는 호출한 배치·서비스가 이 예외를 **잡아서 처리**하므로(7.1절) HTTP 응답까지 올라가는 경우는 처리 누락이다. 그때 404가 아니라 502로 드러나는 편이 누락을 알아채기 쉽다. 다른 매핑을 원하면 `TmdbNotFoundException`에서 `getErrorCode()`만 오버라이드하면 된다(`ErrorCode` 추가 없이 `TITLE_NOT_FOUND`를 쓰는 안).

**`application.yml` 수정 (2줄)** — 나머지 `app.tmdb.*` 블록은 이미 작업 트리에 있는 내용 그대로 둔다.

수정 1: 27행 부근 주석 정정 (ROADMAP 번호는 041이다, 8절 2번). 앞뒤 줄을 같이 보인다.

```yaml
# 수정 전
        config:
          tmdb: # Task 042의 @FeignClient(name = "tmdb")와 맞춘다
            connectTimeout: 3000  # ms. 연결이 안 되면 빨리 실패해 DB 연결을 붙잡지 않는다(TECH T-2)

# 수정 후
        config:
          tmdb: # Task 041의 @FeignClient(name = "tmdb")와 맞춘다
            connectTimeout: 3000  # ms. 연결이 안 되면 빨리 실패해 DB 연결을 붙잡지 않는다(TECH T-2)
```

수정 2: `app.tmdb` 아래의 옛 안내 주석 한 줄 삭제 (43행 부근, rate-limit·retry를 이미 넣었으므로 거짓 설명이 됐다).

```yaml
# 수정 전
    api-token: ${TMDB_API_KEY:}   # 읽기 액세스 토큰(Bearer). 기본값을 비워 둔다(6절 함정 5)
    # rate-limit.*, retry.* 는 ③·⑤단계에서 추가한다
    rate-limit:

# 수정 후 (가운데 주석 줄만 지운다)
    api-token: ${TMDB_API_KEY:}   # 읽기 액세스 토큰(Bearer). 기본값을 비워 둔다(6절 함정 5)
    rate-limit:
```

참고로 `app.tmdb` 블록의 최종 모습(이미 작업 트리와 같다. 값이 다르면 이 값으로 맞춘다):

```yaml
app:
  origin-secret:
    enabled: true
  tmdb:
    base-url: https://api.themoviedb.org/3
    api-token: ${TMDB_API_KEY:}   # 읽기 액세스 토큰(Bearer). 기본값을 비워 둔다(6절 함정 5)
    rate-limit:
      capacity: 10          # 통 크기(순간에 몰아 쓸 수 있는 최대 호출 수)
      refill-tokens: 25     # 이 시간마다 채워지는 토큰 수 (→ 초당 25회)
      refill-period: 1s
      max-wait: 2s          # 토큰이 없을 때 최대 기다리는 시간. 넘으면 포기(D1 확정)
    retry:
      max-attempts: 3          # 첫 시도 포함 총 시도 횟수 (429·5xx)
      io-max-attempts: 2       # 타임아웃·연결 오류의 총 시도 횟수 (D2 확정)
      initial-backoff: 500ms   # 첫 재시도 전 대기. 이후 두 배씩
      max-backoff: 4s          # 지수 대기의 상한
      max-retry-after: 5s      # 서버가 이보다 오래 기다리라고 하면 기다리지 않고 포기한다
```

**`FeignConfig.java` 수정 (10행)**

```java
// 수정 전
    // ErrorDecoder(429·5xx → 재시도 예외)와 Retryer(지수 백오프, Retry-After 우선)는 TMDB 클라이언트와 함께 Task 042에서 추가한다(TECH 3절)

// 수정 후
    // TMDB 전용 ErrorDecoder(429·5xx → 재시도 예외)와 Retryer(지수 백오프, Retry-After 우선)는 infra/tmdb의 TmdbFeignConfig에 있다(Task 041, TECH 3절). 여기(공통 설정)에 두면 모든 Feign 클라이언트에 적용된다
```

**`.env.example`**: 이미 `# --- 외부 연동 ---` 아래에 `TMDB_API_KEY=`가 있다. 변경 없음. 값은 **읽기 액세스 토큰**(D7)이라는 점은 PR 설명에 적는다.

**`TmdbTestFixtures.java`** (테스트용 보조, 이후 단계에서도 쓴다)

```java
package com.ottnavi.infra.tmdb;

import java.time.Duration;

/** 단위 테스트에서 TmdbProperties를 손으로 만들 때 쓰는 값 모음 */
final class TmdbTestFixtures {

    private TmdbTestFixtures() {
    }

    static TmdbProperties.RateLimit rateLimit() {
        return new TmdbProperties.RateLimit(10, 25, Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    /** 테스트가 느려지지 않도록 대기를 1~4ms로 줄인 재시도 설정 (총 3회, IO 오류는 총 2회) */
    static TmdbProperties.Retry retry() {
        return new TmdbProperties.Retry(3, 2, Duration.ofMillis(1), Duration.ofMillis(4), Duration.ofSeconds(5));
    }

    static TmdbProperties props(String token) {
        return new TmdbProperties("http://localhost/3", token, rateLimit(), retry());
    }
}
```

#### 앞 단계와의 연결

- 이 단계가 첫 단계다. 5.0의 `WireMockSmokeTest`와는 독립이다.
- `TmdbFeignConfig`가 쓰는 `TmdbProperties`는 이 단계에서 만든다. `TmdbRateLimiter`·`TmdbErrorDecoder`·`TmdbRetryer`는 아직 없고, 이 단계의 `TmdbFeignConfig`는 그것들을 참조하지 않는다(③·④·⑤에서 빈을 추가).
- 5.0.1의 정리대로 `TmdbApi`·`TmdbClient`·`TmdbRateLimiter`는 `package` 줄만 남긴 상태여야 컴파일된다.

#### 테스트 (T18, T21)

**`TmdbFeignConfigTest.java`** — T18: 토큰이 비었을 때 헤더를 안 붙이고 앱 기동에 지장이 없다는 것의 단위 버전(빈 생성이 예외 없이 끝난다). Spring 컨텍스트가 필요 없다.

```java
package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

class TmdbFeignConfigTest {

    @Test
    void t18_토큰이_비어_있으면_인증_헤더를_붙이지_않는다() {
        RequestInterceptor interceptor = new TmdbFeignConfig().tmdbAuthInterceptor(TmdbTestFixtures.props(""));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void t18_토큰이_있으면_Bearer_헤더가_시도를_반복해도_한_번만_붙는다() {
        RequestInterceptor interceptor = new TmdbFeignConfig().tmdbAuthInterceptor(TmdbTestFixtures.props("abc"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);
        interceptor.apply(template);   // 재시도마다 인터셉터가 다시 실행되는 상황

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer abc");
    }
}
```

**`TmdbPropertiesTest.java`** — T21: 토큰이 `toString()`에 안 나온다.

```java
package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TmdbPropertiesTest {

    @Test
    void t21_toString에_토큰이_나오지_않는다() {
        TmdbProperties props = TmdbTestFixtures.props("secret-token-value");

        assertThat(props.toString())
                .doesNotContain("secret-token-value")
                .contains("apiToken=***");
    }
}
```

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.TmdbFeignConfigTest" --tests "com.ottnavi.infra.tmdb.TmdbPropertiesTest"
```

(Docker 불필요.)

#### 여기서 멈추고 확인할 것

1. 위 두 테스트가 통과한다 (T18 2건, T21 1건 = tests=3).
2. `Select-String` 등으로 확인: 코드·설정 어디에도 토큰 값이 박혀 있지 않다. `TmdbProperties.toString()`이 `apiToken=***`을 출력한다.
3. 앱 전체 기동(`TMDB_API_KEY` 없이)은 ⑦의 T20이 확인한다. 지금은 컴파일과 위 단위 테스트까지.

| 흔한 오류 | 해결 |
|---|---|
| `compileJava`에서 `TmdbApi`·`TmdbRateLimiter`·`TmdbClient` 관련 "cannot find symbol" | 5.0.1대로 세 파일을 `package` 줄만 남긴다 |
| `TmdbException`에서 "abstract method getErrorCode is not implemented" 류 | `BusinessException`의 추상 메서드를 `TmdbException`이 구현했는지(위 코드) 확인. 하위 예외 3종은 구현하지 않는다 |
| `Class 'TmdbException' must either be declared abstract or implement abstract method` | `TmdbException` 선언에 `abstract`가 빠졌거나 `getErrorCode()` 오타 |
| `TmdbProperties`의 `Duration` 바인딩 오류(`500ms`) | Boot 기본 지원이다. 값 오타(`500 ms` 띄어쓰기) 확인. 이 오류는 ⑦의 앱 기동 때 처음 드러난다 |

### 5.2 단계 ② Feign 인터페이스·DTO·얇은 래퍼·샘플 JSON·첫 테스트

#### 건드리는 파일

| 경로 (`backend/` 기준) | 구분 | 역할 |
|---|---|---|
| `src/main/java/com/ottnavi/infra/tmdb/TmdbApi.java` | 수정(교체) | Feign 인터페이스 10개 호출 |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbMediaType.java` | 신규 | `listProviders` 경로 값(movie/tv) enum |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbClient.java` | 수정(교체) | 소비자용 래퍼. **지금은 위임만 하는 얇은 판**, ⑥에서 완성판으로 교체 |
| `src/main/java/com/ottnavi/infra/tmdb/dto/` 16개 | 신규 1개 제외 모두 신규(`TmdbMovieSummary`는 교체) | 응답 DTO |
| `src/test/resources/tmdb/*.json` 7개 | 신규 | 샘플 JSON (D6 가짜 값) |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbTestSupport.java` | 신규 | 테스트 공통 기반(WireMock 서버 + 가벼운 컨텍스트) |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbClientTest.java` | 신규 | T1, T2, T3, T10, T19 |

DTO 16개: `TmdbPage`, `TmdbMovieSummary`, `TmdbTvSummary`, `TmdbGenre`, `TmdbCastMember`, `TmdbCredits`, `TmdbMovieDetail`, `TmdbSeasonSummary`, `TmdbTvDetail`, `TmdbEpisode`, `TmdbTvSeasonDetail`, `TmdbProviderRef`, `TmdbRegionProviders`, `TmdbWatchProvidersResponse`, `TmdbProviderInfo`, `TmdbProviderListResponse`. 모두 `com.ottnavi.infra.tmdb.dto`.

규칙(전 DTO 공통, 4절): 클래스에 `@JsonIgnoreProperties(ignoreUnknown = true)`, snake_case 필드는 `@JsonProperty("...")`로 명시, 날짜는 `String`(빈 문자열 가능), null이 올 수 있는 숫자는 `Integer`/`Long`/`Double`. 원시형(`int`·`long`)은 **항상 오는 필드**(`id`, `page`, `total_pages` 등)에만 쓴다. 어노테이션 패키지는 `com.fasterxml.jackson.annotation` 그대로다(Jackson 3에서도 유지, 3.5절). **확인 필요(실행 시 확인)**: Jackson 3의 `null → 원시형` 기본 동작은 문서로 확인하지 못했다. 그래서 null 가능성이 조금이라도 있는 필드는 래퍼 타입으로 뒀다. T1~T3이 통과하면 해소된다.

#### 파일별 전체 코드 — main

**`TmdbApi.java`** (Feign 인터페이스. 소비자는 직접 쓰지 않고 `TmdbClient`를 쓴다)

```java
package com.ottnavi.infra.tmdb;

import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * TMDB v3 API 호출 목록. 파라미터는 TMDB 이름 그대로 모두 명시한다(고정값 채우기는 TmdbClient가 한다).
 * name = "tmdb"는 application.yml의 spring.cloud.openfeign.client.config.tmdb 키와 같아야 타임아웃·로그 수준이 적용된다.
 * 인증·속도 제한·오류 해석·재시도는 TmdbFeignConfig가 붙인다.
 */
@FeignClient(
        name = "tmdb",
        url = "${app.tmdb.base-url}",
        configuration = TmdbFeignConfig.class
)
public interface TmdbApi {

    // 1. 영화 후보 검색 (서비스별 KR 구독 후보 수집)
    @GetMapping("/discover/movie")
    TmdbPage<TmdbMovieSummary> discoverMovies(
            @RequestParam("with_watch_providers") String withWatchProviders,
            @RequestParam("watch_region") String watchRegion,
            @RequestParam("with_watch_monetization_types") String monetizationTypes,
            @RequestParam("language") String language,
            @RequestParam("page") int page
    );

    // 2. 드라마 후보 검색
    @GetMapping("/discover/tv")
    TmdbPage<TmdbTvSummary> discoverTvs(
            @RequestParam("with_watch_providers") String withWatchProviders,
            @RequestParam("watch_region") String watchRegion,
            @RequestParam("with_watch_monetization_types") String monetizationTypes,
            @RequestParam("language") String language,
            @RequestParam("page") int page
    );

    // 3. 영화 검색 (사용자 입력 query는 로그·지표 태그에 쓰지 않는다)
    @GetMapping("/search/movie")
    TmdbPage<TmdbMovieSummary> searchMovies(
            @RequestParam("query") String query,
            @RequestParam("language") String language,
            @RequestParam("page") int page,
            @RequestParam("include_adult") boolean includeAdult
    );

    // 4. 드라마 검색
    @GetMapping("/search/tv")
    TmdbPage<TmdbTvSummary> searchTvs(
            @RequestParam("query") String query,
            @RequestParam("language") String language,
            @RequestParam("page") int page,
            @RequestParam(value = "first_air_date_year", required = false) Integer firstAirDateYear
    );

    // 5. 영화 상세 조회 (append_to_response=credits로 출연진까지 한 번에)
    @GetMapping("/movie/{movieId}")
    TmdbMovieDetail getMovie(
            @PathVariable("movieId") long movieId,
            @RequestParam("language") String language,
            @RequestParam(value = "append_to_response", required = false) String appendToResponse
    );

    // 6. 드라마 상세 조회 (시즌 목록 포함)
    @GetMapping("/tv/{tvId}")
    TmdbTvDetail getTv(
            @PathVariable("tvId") long tvId,
            @RequestParam("language") String language,
            @RequestParam(value = "append_to_response", required = false) String appendToResponse
    );

    // 7. 드라마 시즌 상세(회차별 러닝타임)
    @GetMapping("/tv/{tvId}/season/{seasonNumber}")
    TmdbTvSeasonDetail getTvSeason(
            @PathVariable("tvId") long tvId,
            @PathVariable("seasonNumber") int seasonNumber,
            @RequestParam("language") String language
    );

    // 8. 영화 시청 제공처 (나라 코드 맵)
    @GetMapping("/movie/{movieId}/watch/providers")
    TmdbWatchProvidersResponse getMovieWatchProviders(
            @PathVariable("movieId") long movieId
    );

    // 9. 드라마 시즌별 시청 제공처
    @GetMapping("/tv/{tvId}/season/{seasonNumber}/watch/providers")
    TmdbWatchProvidersResponse getTvSeasonWatchProviders(
            @PathVariable("tvId") long tvId,
            @PathVariable("seasonNumber") int seasonNumber
    );

    // 10. 제공처 전체 목록 (mediaType은 "movie" 또는 "tv"). 7곳 외 제공처 발견용이라 거르지 않고 받는다
    @GetMapping("/watch/providers/{mediaType}")
    TmdbProviderListResponse listProviders(
            @PathVariable("mediaType") String mediaType,
            @RequestParam("watch_region") String watchRegion,
            @RequestParam("language") String language
    );
}
```

- `url = "${app.tmdb.base-url}"` 플레이스홀더는 spring-cloud-openfeign `FeignClient#url`이 지원한다(소스 확인: `String url() default ""`, `c7`). 값이 `.../3`이라 경로 `/discover/movie`와 합쳐져 `/3/discover/movie`가 된다.
- 값이 `null`인 `@RequestParam(required = false)`는 쿼리에서 빠지는 것으로 알려져 있다. **확인 필요(실행 시 확인)**: T2의 `withoutQueryParam` 검증이 이를 확인한다.
- 이름 앞의 숫자 주석은 4절 표의 호출 번호와 같다.

**`TmdbMediaType.java`**

```java
package com.ottnavi.infra.tmdb;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 제공처 전체 목록(/watch/providers/{mediaType}) 호출에서 쓰는 매체 종류 */
@Getter
@RequiredArgsConstructor
public enum TmdbMediaType {

    MOVIE("movie"),
    TV("tv");

    private final String path;   // TMDB 경로에 들어가는 값
}
```

**`TmdbClient.java` — ②의 얇은 판** (위임만 한다. ⑥에서 5.6의 완성판으로 통째로 교체한다. 4절 표의 래퍼 메서드 10개 시그니처는 이 단계에서 확정되고 ⑥에서도 바뀌지 않는다)

```java
package com.ottnavi.infra.tmdb;

import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 소비 Task(042~044, 050)가 쓰는 TMDB 호출 창구. ②에서는 TmdbApi에 위임만 한다 */
@Component
@RequiredArgsConstructor
public class TmdbClient {

    private static final String REGION = "KR";                  // PRD: 한국 제공처만 본다
    private static final String LANGUAGE_KO = "ko-KR";
    private static final String MONETIZATION_FLATRATE = "flatrate";   // 후보 수집은 구독형만

    private final TmdbApi api;

    /** 제공처(ID, 쉼표 AND·파이프 OR)별 KR 구독 후보 영화를 한 쪽씩 가져온다 */
    public TmdbPage<TmdbMovieSummary> discoverMovies(String withWatchProviders, int page) {
        return api.discoverMovies(withWatchProviders, REGION, MONETIZATION_FLATRATE, LANGUAGE_KO, page);
    }

    /** 제공처별 KR 구독 후보 드라마를 한 쪽씩 가져온다 */
    public TmdbPage<TmdbTvSummary> discoverTvs(String withWatchProviders, int page) {
        return api.discoverTvs(withWatchProviders, REGION, MONETIZATION_FLATRATE, LANGUAGE_KO, page);
    }

    /** 영화를 제목으로 검색한다 (성인물 제외) */
    public TmdbPage<TmdbMovieSummary> searchMovies(String query, int page) {
        return api.searchMovies(query, LANGUAGE_KO, page, false);
    }

    /** 드라마를 제목으로 검색한다 */
    public TmdbPage<TmdbTvSummary> searchTvs(String query, int page) {
        return api.searchTvs(query, LANGUAGE_KO, page, null);
    }

    /** 영화 상세를 가져온다. appendToResponse에 "credits"를 주면 출연진이 같이 온다(null이면 안 붙인다) */
    public TmdbMovieDetail getMovie(long movieId, String language, String appendToResponse) {
        return api.getMovie(movieId, language, appendToResponse);
    }

    /** 드라마 상세(시즌 목록 포함)를 가져온다 */
    public TmdbTvDetail getTv(long tvId, String language, String appendToResponse) {
        return api.getTv(tvId, language, appendToResponse);
    }

    /** 드라마 한 시즌의 회차 목록을 가져온다 */
    public TmdbTvSeasonDetail getTvSeason(long tvId, int seasonNumber, String language) {
        return api.getTvSeason(tvId, seasonNumber, language);
    }

    /** 영화의 나라별 시청 제공처를 가져온다(KR 골라 쓰기는 소비 쪽) */
    public TmdbWatchProvidersResponse getMovieWatchProviders(long movieId) {
        return api.getMovieWatchProviders(movieId);
    }

    /** 드라마 한 시즌의 나라별 시청 제공처를 가져온다 */
    public TmdbWatchProvidersResponse getTvSeasonWatchProviders(long tvId, int seasonNumber) {
        return api.getTvSeasonWatchProviders(tvId, seasonNumber);
    }

    /** KR의 제공처 전체 목록을 가져온다. 우리 7곳 외 제공처도 거르지 않고 돌려준다(저장은 Task 043) */
    public TmdbProviderListResponse listProviders(TmdbMediaType mediaType) {
        return api.listProviders(mediaType.getPath(), REGION, LANGUAGE_KO);
    }
}
```

**DTO 16개**

`dto/TmdbPage.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 목록 응답 공통 틀(discover·search). 모르는 필드는 버린다(Boot 4 + Jackson 3은 기본이 실패, 3.5절) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbPage<T>(
        int page,
        List<T> results,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("total_results") int totalResults) {
}
```

`dto/TmdbMovieSummary.java` (기존 파일 교체. `runtime`은 상세 응답에만 있어 뺀다)

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** discover·search 영화 결과 한 건 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieSummary(
        long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("release_date") String releaseDate,      // 날짜도 String. 빈 문자열("")이 올 수 있다
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("genre_ids") List<Integer> genreIds) {
}
```

`dto/TmdbTvSummary.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** discover·search 드라마 결과 한 건 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvSummary(
        long id,
        String name,
        @JsonProperty("original_name") String originalName,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("first_air_date") String firstAirDate,   // 빈 문자열("")이 올 수 있다 (c7 TMDB 예시)
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("genre_ids") List<Integer> genreIds,
        @JsonProperty("origin_country") List<String> originCountry) {
}
```

`dto/TmdbGenre.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 장르 (상세 응답의 genres[]) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbGenre(int id, String name) {
}
```

`dto/TmdbCastMember.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 출연진 한 명 (credits.cast[]). 상위 10명 자르기는 Task 042 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCastMember(
        long id,
        String name,
        String character,
        Integer order,
        @JsonProperty("profile_path") String profilePath) {
}
```

`dto/TmdbCredits.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** append_to_response=credits로 붙는 출연진. 드라마는 "가장 최근 시즌" 출연진이다(4절 6번) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCredits(List<TmdbCastMember> cast) {
}
```

`dto/TmdbMovieDetail.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 영화 상세. credits는 append_to_response에 credits를 줬을 때만 채워지고 아니면 null이다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieDetail(
        long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        String overview,                                         // 한국어 줄거리가 ""인지 null인지는 확인 필요
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("release_date") String releaseDate,
        Integer runtime,                                         // null·0 가능 (추정은 Task 042)
        List<TmdbGenre> genres,
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        TmdbCredits credits) {
}
```

`dto/TmdbSeasonSummary.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 드라마 상세 안의 시즌 요약 (seasons[]). 시즌 0(스페셜)도 포함돼 있다. 거르는 일은 Task 042 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSeasonSummary(
        long id,                                                 // tmdb_season_id
        @JsonProperty("season_number") int seasonNumber,
        String name,
        @JsonProperty("air_date") String airDate,                // null·"" 가능
        @JsonProperty("episode_count") Integer episodeCount,
        @JsonProperty("poster_path") String posterPath) {
}
```

`dto/TmdbTvDetail.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 드라마 상세 (시즌 목록 포함). credits는 append_to_response에 credits를 줬을 때만 채워진다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvDetail(
        long id,
        String name,
        @JsonProperty("original_name") String originalName,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("first_air_date") String firstAirDate,
        String status,
        @JsonProperty("original_language") String originalLanguage,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("number_of_seasons") Integer numberOfSeasons,
        List<TmdbGenre> genres,
        @JsonProperty("episode_run_time") List<Integer> episodeRunTime,   // 빈 배열 가능
        List<TmdbSeasonSummary> seasons,
        TmdbCredits credits) {
}
```

`dto/TmdbEpisode.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 시즌 상세 안의 회차 한 건 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbEpisode(
        @JsonProperty("episode_number") int episodeNumber,
        String name,
        @JsonProperty("air_date") String airDate,                // 미방영이면 null·"" 가능
        Integer runtime,                                         // null 가능 (ERD runtime_missing_count, 추정은 Task 042)
        @JsonProperty("episode_type") String episodeType) {      // 예: standard
}
```

`dto/TmdbTvSeasonDetail.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 드라마 시즌 상세(회차 목록) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvSeasonDetail(
        Long id,                                                 // 확인 필요: c7 예시에는 문자열 _id만 보였다. 없으면 null (tmdb_season_id는 TmdbSeasonSummary.id를 쓴다)
        @JsonProperty("season_number") int seasonNumber,
        String name,
        @JsonProperty("air_date") String airDate,
        @JsonProperty("poster_path") String posterPath,
        List<TmdbEpisode> episodes) {
}
```

`dto/TmdbProviderRef.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 작품 하나에 붙은 제공처 한 곳 (flatrate/rent/buy 배열의 원소) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProviderRef(
        @JsonProperty("provider_id") int providerId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("logo_path") String logoPath,
        @JsonProperty("display_priority") Integer displayPriority) {
}
```

`dto/TmdbRegionProviders.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 한 나라의 제공처. 목록이 없는 항목(flatrate 없음 등)은 null로 온다 → 소비 쪽이 null을 빈 목록으로 본다.
 * ads·free가 같은 구조로 더 올 수 있지만 쓰지 않아 ignoreUnknown으로 버린다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbRegionProviders(
        String link,
        List<TmdbProviderRef> flatrate,   // 구독
        List<TmdbProviderRef> rent,       // 대여
        List<TmdbProviderRef> buy) {      // 구매
}
```

`dto/TmdbWatchProvidersResponse.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** 작품(영화 또는 시즌)의 나라별 시청 제공처. results의 키는 나라 코드("KR"). KR만 골라 쓰는 일은 Task 042 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbWatchProvidersResponse(
        long id,
        Map<String, TmdbRegionProviders> results) {
}
```

`dto/TmdbProviderInfo.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** 제공처 전체 목록의 한 곳 (/watch/providers/movie|tv) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProviderInfo(
        @JsonProperty("provider_id") int providerId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("logo_path") String logoPath,
        @JsonProperty("display_priorities") Map<String, Integer> displayPriorities) {   // 나라별 노출 순서
}
```

`dto/TmdbProviderListResponse.java`

```java
package com.ottnavi.infra.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 제공처 전체 목록 응답. 우리 7곳 외 제공처를 "미매핑"으로 저장하는 일은 Task 043 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProviderListResponse(List<TmdbProviderInfo> results) {
}
```

#### 샘플 JSON 7개 (`backend/src/test/resources/tmdb/`, BOM 없는 UTF-8, D6: 구조는 실제와 같고 값은 가짜)

폴더가 없으면 만든다: `New-Item -ItemType Directory -Force C:\Users\swmoo\workspace\ottnavi-backend\backend\src\test\resources\tmdb`. 필드 구조는 4절의 `c7` 확인분을 따랐고, **쓰지 않는 필드(`adult`, `video`, `vote_count` 등)와 `""`·`null` 값을 일부러 섞었다**(T3이 이것으로 매핑 실패가 없는지 본다). 제공처 ID는 Task 004 매핑표의 숫자(8, 337 등)를 쓰되 이름은 가짜다. 실제 응답으로 모양을 검증하는 절차는 5.8절.

`discover-movie.json` — 영화 discover(검색 호출 `searchMovies`도 같은 모양이라 이 파일을 재사용한다). 두 번째 항목은 빈 날짜·null 포스터·빈 줄거리.

```json
{
  "page": 1,
  "results": [
    {
      "adult": false,
      "backdrop_path": "/test-backdrop-1.jpg",
      "genre_ids": [18, 53],
      "id": 900001,
      "original_language": "ko",
      "original_title": "테스트 원제 1",
      "overview": "가짜 줄거리 1",
      "popularity": 87.5,
      "poster_path": "/test-poster-1.jpg",
      "release_date": "2024-05-01",
      "title": "테스트 영화 1",
      "video": false,
      "vote_average": 7.8,
      "vote_count": 1234
    },
    {
      "adult": false,
      "backdrop_path": null,
      "genre_ids": [],
      "id": 900002,
      "original_language": "en",
      "original_title": "Test Original 2",
      "overview": "",
      "popularity": 3.2,
      "poster_path": null,
      "release_date": "",
      "title": "테스트 영화 2",
      "video": false,
      "vote_average": 0,
      "vote_count": 0
    }
  ],
  "total_pages": 3,
  "total_results": 41
}
```

`discover-tv.json` — 드라마 discover(`searchTvs`도 재사용). 두 번째 항목은 `first_air_date`가 `""`.

```json
{
  "page": 1,
  "results": [
    {
      "backdrop_path": "/test-tv-backdrop-1.jpg",
      "first_air_date": "2023-03-01",
      "genre_ids": [18],
      "id": 800001,
      "name": "테스트 드라마 1",
      "origin_country": ["KR"],
      "original_language": "ko",
      "original_name": "테스트 드라마 원제 1",
      "overview": "가짜 줄거리 A",
      "popularity": 120.4,
      "poster_path": "/test-tv-poster-1.jpg",
      "vote_average": 8.1,
      "vote_count": 560
    },
    {
      "backdrop_path": null,
      "first_air_date": "",
      "genre_ids": [10759, 18],
      "id": 800002,
      "name": "테스트 드라마 2",
      "origin_country": ["US", "KR"],
      "original_language": "en",
      "original_name": "Test Drama Original 2",
      "overview": "",
      "popularity": 5.0,
      "poster_path": null,
      "vote_average": 0,
      "vote_count": 0
    }
  ],
  "total_pages": 2,
  "total_results": 25
}
```

`movie-detail.json` — 영화 상세 + credits (`append_to_response=credits`)

```json
{
  "adult": false,
  "backdrop_path": "/test-backdrop-1.jpg",
  "belongs_to_collection": null,
  "budget": 0,
  "genres": [
    { "id": 18, "name": "드라마" },
    { "id": 53, "name": "스릴러" }
  ],
  "homepage": "",
  "id": 900001,
  "imdb_id": "tt0000000",
  "original_language": "ko",
  "original_title": "테스트 원제 1",
  "overview": "가짜 줄거리 1",
  "popularity": 87.5,
  "poster_path": "/test-poster-1.jpg",
  "production_companies": [],
  "release_date": "2024-05-01",
  "revenue": 0,
  "runtime": 118,
  "status": "Released",
  "tagline": "",
  "title": "테스트 영화 1",
  "video": false,
  "vote_average": 7.8,
  "vote_count": 1234,
  "credits": {
    "cast": [
      { "adult": false, "id": 7001, "name": "테스트 배우 1", "character": "테스트 역할 1", "order": 0, "profile_path": "/test-profile-1.jpg" },
      { "adult": false, "id": 7002, "name": "테스트 배우 2", "character": "테스트 역할 2", "order": 1, "profile_path": null }
    ],
    "crew": []
  }
}
```

`tv-detail.json` — 드라마 상세 + 시즌 목록(시즌 0 포함) + credits

```json
{
  "backdrop_path": "/test-tv-backdrop-1.jpg",
  "created_by": [],
  "episode_run_time": [60],
  "first_air_date": "2023-03-01",
  "genres": [
    { "id": 18, "name": "드라마" }
  ],
  "homepage": "",
  "id": 800001,
  "in_production": true,
  "name": "테스트 드라마 1",
  "number_of_episodes": 24,
  "number_of_seasons": 2,
  "original_language": "ko",
  "original_name": "테스트 드라마 원제 1",
  "overview": "가짜 줄거리 A",
  "popularity": 120.4,
  "poster_path": "/test-tv-poster-1.jpg",
  "seasons": [
    { "air_date": "2022-12-25", "episode_count": 2, "id": 810000, "name": "스페셜", "overview": "", "poster_path": null, "season_number": 0, "vote_average": 0 },
    { "air_date": "2023-03-01", "episode_count": 12, "id": 810001, "name": "시즌 1", "overview": "", "poster_path": "/test-season-1.jpg", "season_number": 1, "vote_average": 8.0 },
    { "air_date": "2024-03-01", "episode_count": 12, "id": 810002, "name": "시즌 2", "overview": "", "poster_path": "/test-season-2.jpg", "season_number": 2, "vote_average": 8.2 }
  ],
  "status": "Returning Series",
  "type": "Scripted",
  "vote_average": 8.1,
  "vote_count": 560,
  "credits": {
    "cast": [
      { "id": 7101, "name": "테스트 배우 3", "character": "테스트 역할 3", "order": 0, "profile_path": "/test-profile-3.jpg" }
    ],
    "crew": []
  }
}
```

`tv-season.json` — 시즌 상세. 세 번째 회차는 `runtime: null`, 미방영 회차는 `air_date: ""`.

```json
{
  "_id": "000000000000000000000001",
  "air_date": "2023-03-01",
  "episodes": [
    { "air_date": "2023-03-01", "episode_number": 1, "episode_type": "standard", "id": 820001, "name": "1화", "overview": "", "runtime": 62, "season_number": 1, "show_id": 800001 },
    { "air_date": "2023-03-02", "episode_number": 2, "episode_type": "standard", "id": 820002, "name": "2화", "overview": "", "runtime": 58, "season_number": 1, "show_id": 800001 },
    { "air_date": "2023-03-08", "episode_number": 3, "episode_type": "standard", "id": 820003, "name": "3화", "overview": "", "runtime": null, "season_number": 1, "show_id": 800001 },
    { "air_date": "", "episode_number": 4, "episode_type": "standard", "id": 820004, "name": "4화", "overview": "", "runtime": null, "season_number": 1, "show_id": 800001 }
  ],
  "id": 810001,
  "name": "시즌 1",
  "overview": "",
  "poster_path": "/test-season-1.jpg",
  "season_number": 1,
  "vote_average": 8.0
}
```

`watch-providers.json` — 작품의 제공처 맵(영화·시즌 공용). KR에 `flatrate`·`rent`가 있고 `buy`는 없음(`null`로 올 때와 키가 빠질 때 모두 `null`), US는 별도.

```json
{
  "id": 900001,
  "results": {
    "KR": {
      "link": "https://www.themoviedb.org/movie/900001/watch?locale=KR",
      "flatrate": [
        { "logo_path": "/test-logo-8.jpg", "provider_id": 8, "provider_name": "테스트 제공처 8", "display_priority": 3 },
        { "logo_path": "/test-logo-337.jpg", "provider_id": 337, "provider_name": "테스트 제공처 337", "display_priority": 7 }
      ],
      "rent": [
        { "logo_path": "/test-logo-2.jpg", "provider_id": 2, "provider_name": "테스트 제공처 2", "display_priority": 20 }
      ]
    },
    "US": {
      "link": "https://www.themoviedb.org/movie/900001/watch?locale=US",
      "flatrate": [
        { "logo_path": "/test-logo-8.jpg", "provider_id": 8, "provider_name": "테스트 제공처 8", "display_priority": 1 }
      ]
    }
  }
}
```

`provider-list.json` — 제공처 전체 목록. 우리 7곳 중 2곳(8, 337)과 7곳 밖 제공처 1곳(9999)이 섞여 있다(T19).

```json
{
  "results": [
    { "display_priorities": { "KR": 3, "US": 1 }, "display_priority": 3, "logo_path": "/test-logo-8.jpg", "provider_name": "테스트 제공처 8", "provider_id": 8 },
    { "display_priorities": { "KR": 7 }, "display_priority": 7, "logo_path": "/test-logo-337.jpg", "provider_name": "테스트 제공처 337", "provider_id": 337 },
    { "display_priorities": { "KR": 50 }, "display_priority": 50, "logo_path": "/test-logo-9999.jpg", "provider_name": "테스트 제공처 9999(7곳 밖)", "provider_id": 9999 }
  ]
}
```

#### 파일별 전체 코드 — test

**`TmdbTestSupport.java`** — 이후 모든 WireMock 테스트의 공통 기반이다. 핵심 결정: WireMock 서버를 **JVM에 한 번만** 띄우는 `static` 서버로 둔다. `@RegisterExtension` 확장은 클래스가 끝나면 서버를 멈추는데, Spring이 컨텍스트를 캐시해 다음 테스트 클래스가 **이미 멈춘 서버의 포트**(`app.tmdb.base-url`)를 그대로 쓰기 때문이다.

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ottnavi.global.config.FeignConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * TMDB 테스트 공통 기반: DB·Redis 없이 Feign·Jackson·TMDB 설정만 올린 가벼운 컨텍스트 + WireMock 서버(실제 TMDB는 부르지 않는다).
 * 앱 전체 대신 필요한 설정만 지정(@SpringBootTest(classes))하므로 DataSource·JPA·Redis 자동 구성이 로드되지 않는다 → Docker 불필요.
 */
@SpringBootTest(classes = TmdbTestSupport.TestApp.class)
abstract class TmdbTestSupport {

    protected static final String TOKEN = "test-token";
    protected static final String DISCOVER_MOVIE_PATH = "/3/discover/movie";

    // JVM에서 한 번만 띄운다. 컨텍스트가 캐시돼 여러 테스트 클래스가 같은 base-url(포트)을 재사용하기 때문이다.
    // 종료는 JVM 종료 훅에 맡긴다. 테스트 사이 상태는 @BeforeEach의 resetAll()이 지운다.
    protected static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMock.start();
        Runtime.getRuntime().addShutdownHook(new Thread(wireMock::stop));
    }

    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");   // 실제 TMDB 대신 WireMock
        registry.add("app.tmdb.api-token", () -> TOKEN);
        registry.add("app.tmdb.retry.initial-backoff", () -> "10ms");         // 테스트가 느려지지 않게 대기를 줄인다
        registry.add("app.tmdb.retry.max-backoff", () -> "40ms");
    }

    @Autowired
    protected TmdbClient tmdbClient;

    @Autowired
    protected TmdbProperties props;

    @Autowired
    protected MeterRegistry meterRegistry;

    @BeforeEach
    void resetWireMockAndMetrics() {
        wireMock.resetAll();       // 스텁·요청 기록·시나리오 상태를 모두 지운다
        meterRegistry.clear();     // tmdb.call 카운트를 테스트마다 0에서 시작. TmdbClient는 호출 때마다 카운터를 조회하므로 안전하다
    }

    /** 가벼운 컨텍스트 구성. 테스트 소스의 @Configuration은 반드시 테스트 클래스 안의 중첩 static 클래스로 둔다(TECH 6절) */
    @Configuration
    @Import({FeignConfig.class, TmdbConfig.class, TmdbClient.class})
    @ImportAutoConfiguration({
            FeignAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class})
    static class TestApp {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /** src/test/resources/tmdb/ 의 샘플 JSON을 읽는다 */
    protected static String json(String fileName) {
        try {
            return new ClassPathResource("tmdb/" + fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("샘플 JSON을 읽지 못했다: " + fileName, e);
        }
    }

    /** 200 + 샘플 JSON 응답 */
    protected static ResponseDefinitionBuilder okJson(String fileName) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json(fileName));
    }

    /** 오류 상태 + 손으로 만든 간단한 오류 본문(TMDB 오류 본문 구조는 확인하지 못했으니 테스트가 본문 형식에 의존하지 않는다, 5.8절 6번) */
    protected static ResponseDefinitionBuilder errorResponse(int status) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status_code\":7,\"status_message\":\"가짜 오류 본문\",\"success\":false}");
    }

    /** 같은 경로가 처음엔 status 오류, 그다음엔 discover-movie.json 200을 돌려주게 한다. retryAfter가 null이면 Retry-After 헤더를 안 붙인다 */
    protected static void stubFailThenOk(int status, String retryAfter) {
        String scenario = "fail-then-ok-" + status;
        ResponseDefinitionBuilder failure = errorResponse(status);
        if (retryAfter != null) {
            failure.withHeader("Retry-After", retryAfter);
        }
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .inScenario(scenario).whenScenarioStateIs(Scenario.STARTED)
                .willReturn(failure)
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .inScenario(scenario).whenScenarioStateIs("recovered")
                .willReturn(okJson("discover-movie.json")));
    }

    /** tmdb.call{endpoint, result} 카운터의 현재 값. 아직 한 번도 안 셌으면 0 */
    protected double callCount(String endpoint, String result) {
        Counter counter = meterRegistry.find("tmdb.call").tag("endpoint", endpoint).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
```

- `WireMockServer#baseUrl()`, `stubFor`, `verify(int, RequestPatternBuilder)`, `findAll`, `resetAll`, `Scenario.STARTED`, `RequestPatternBuilder#withoutQueryParam`, `HttpHeader#values()`는 모두 wiremock-standalone 3.13.2 소스 jar에서 존재를 확인했다.
- 자동 구성 클래스 이름(`org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration`, `org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration`, `org.springframework.cloud.openfeign.FeignAutoConfiguration`)은 jar 목록으로 존재를 확인했다. **확인 필요(실행 시 확인)**: 이 조합으로 컨텍스트가 뜨는지, Feign 디코더가 Jackson 3으로 실제 매핑하는지는 T1이 처음 확인한다.
- `ClassPathResource#getContentAsString(Charset)`는 Spring 6.0.5부터 있는 메서드다. **확인 필요(컴파일 시 확인)**: Spring 7에서 시그니처 변경이 있는지 문서로는 못 봤다. 컴파일 오류가 나면 `new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)`로 바꾼다.
- `@DynamicPropertySource`의 값 공급자(`() -> ...`)는 컨텍스트를 만들 때 평가된다. 서버는 이 클래스의 `static` 초기화에서 이미 떠 있으므로 포트를 얻을 수 있다.

**`TmdbClientTest.java`** — T1, T2, T3, T10, T19

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.infra.tmdb.dto.TmdbRegionProviders;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class TmdbClientTest extends TmdbTestSupport {

    @Autowired
    private Environment environment;

    // T1: 정상 discover. 헤더·쿼리·경로가 맞고 JSON이 record로 매핑된다 (Jackson 3 + Feign 디코더가 실제로 동작한다는 증거)
    @Test
    void t1_discoverMovies_정상_응답이_DTO로_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withQueryParam("with_watch_providers", equalTo("8"))
                .withQueryParam("watch_region", equalTo("KR"))
                .withQueryParam("with_watch_monetization_types", equalTo("flatrate"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("discover-movie.json")));

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalResults()).isEqualTo(41);
        assertThat(page.results()).hasSize(2);
        TmdbMovieSummary first = page.results().get(0);
        assertThat(first.id()).isEqualTo(900001L);
        assertThat(first.title()).isEqualTo("테스트 영화 1");
        assertThat(first.originalTitle()).isEqualTo("테스트 원제 1");
        assertThat(first.releaseDate()).isEqualTo("2024-05-01");
        assertThat(first.genreIds()).containsExactly(18, 53);
        assertThat(first.popularity()).isEqualTo(87.5);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .withoutQueryParam("api_key"));   // 토큰을 쿼리로 보내지 않는다 (Task 004: api_key 쿼리는 401)
    }

    // T2: 나머지 호출을 하나씩. 경로와 쿼리 이름이 맞는지 스텁을 엄격하게 쓴다
    @Test
    void t2_discoverTvs_정상_응답이_DTO로_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv"))
                .withQueryParam("with_watch_providers", equalTo("8|337"))
                .withQueryParam("watch_region", equalTo("KR"))
                .withQueryParam("with_watch_monetization_types", equalTo("flatrate"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson("discover-tv.json")));

        TmdbPage<TmdbTvSummary> page = tmdbClient.discoverTvs("8|337", 2);

        assertThat(page.results()).hasSize(2);
        assertThat(page.results().get(0).name()).isEqualTo("테스트 드라마 1");
        assertThat(page.results().get(0).originCountry()).containsExactly("KR");
        assertThat(page.results().get(1).firstAirDate()).isEmpty();   // 빈 문자열이 그대로 온다
    }

    @Test
    void t2_searchMovies_정상_응답이_DTO로_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/search/movie"))
                .withQueryParam("query", equalTo("테스트"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("include_adult", equalTo("false"))
                .willReturn(okJson("discover-movie.json")));

        TmdbPage<TmdbMovieSummary> page = tmdbClient.searchMovies("테스트", 1);

        assertThat(page.results()).hasSize(2);
    }

    @Test
    void t2_searchTvs_정상_응답이_DTO로_매핑되고_연도_파라미터는_빠진다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/search/tv"))
                .withQueryParam("query", equalTo("테스트"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("discover-tv.json")));

        TmdbPage<TmdbTvSummary> page = tmdbClient.searchTvs("테스트", 1);

        assertThat(page.results()).hasSize(2);
        // null인 @RequestParam은 쿼리에서 빠진다 (확인 필요였던 항목)
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/search/tv")).withoutQueryParam("first_air_date_year"));
    }

    @Test
    void t2_getMovie_상세와_출연진이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("append_to_response", equalTo("credits"))
                .willReturn(okJson("movie-detail.json")));

        TmdbMovieDetail movie = tmdbClient.getMovie(900001L, "ko-KR", "credits");

        assertThat(movie.id()).isEqualTo(900001L);
        assertThat(movie.runtime()).isEqualTo(118);
        assertThat(movie.genres()).extracting("name").containsExactly("드라마", "스릴러");
        assertThat(movie.credits().cast()).hasSize(2);
        assertThat(movie.credits().cast().get(0).name()).isEqualTo("테스트 배우 1");
        assertThat(movie.credits().cast().get(1).profilePath()).isNull();
    }

    @Test
    void t2_getMovie_appendToResponse가_null이면_쿼리에서_빠진다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001")).willReturn(okJson("movie-detail.json")));

        tmdbClient.getMovie(900001L, "en-US", null);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900001"))
                .withQueryParam("language", equalTo("en-US"))
                .withoutQueryParam("append_to_response"));
    }

    @Test
    void t2_getTv_상세와_시즌_목록이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001"))
                .withQueryParam("language", equalTo("ko-KR"))
                .withQueryParam("append_to_response", equalTo("credits"))
                .willReturn(okJson("tv-detail.json")));

        TmdbTvDetail tv = tmdbClient.getTv(800001L, "ko-KR", "credits");

        assertThat(tv.name()).isEqualTo("테스트 드라마 1");
        assertThat(tv.numberOfSeasons()).isEqualTo(2);
        assertThat(tv.episodeRunTime()).containsExactly(60);
        assertThat(tv.seasons()).extracting("seasonNumber").containsExactly(0, 1, 2);   // 시즌 0도 그대로 온다(거르는 건 042)
        assertThat(tv.seasons().get(1).id()).isEqualTo(810001L);
        assertThat(tv.credits().cast()).hasSize(1);
    }

    @Test
    void t2_getTvSeason_회차와_null_러닝타임이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001/season/1"))
                .withQueryParam("language", equalTo("ko-KR"))
                .willReturn(okJson("tv-season.json")));

        TmdbTvSeasonDetail season = tmdbClient.getTvSeason(800001L, 1, "ko-KR");

        assertThat(season.seasonNumber()).isEqualTo(1);
        assertThat(season.episodes()).hasSize(4);
        assertThat(season.episodes().get(0).runtime()).isEqualTo(62);
        assertThat(season.episodes().get(2).runtime()).isNull();
        assertThat(season.episodes().get(3).airDate()).isEmpty();
    }

    @Test
    void t2_getMovieWatchProviders_나라별_제공처_맵이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001/watch/providers"))
                .willReturn(okJson("watch-providers.json")));

        TmdbWatchProvidersResponse response = tmdbClient.getMovieWatchProviders(900001L);

        TmdbRegionProviders kr = response.results().get("KR");
        assertThat(kr.flatrate()).extracting("providerId").containsExactly(8, 337);
        assertThat(kr.rent()).hasSize(1);
        assertThat(kr.buy()).isNull();   // 키가 없으면 null (소비 쪽이 빈 목록으로 본다)
        assertThat(response.results()).containsKey("US");
    }

    @Test
    void t2_getTvSeasonWatchProviders_정상_응답이_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001/season/1/watch/providers"))
                .willReturn(okJson("watch-providers.json")));

        TmdbWatchProvidersResponse response = tmdbClient.getTvSeasonWatchProviders(800001L, 1);

        assertThat(response.results().get("KR").flatrate()).hasSize(2);
    }

    // T19: 제공처 전체 목록은 7곳 밖 제공처도 거르지 않고 전부 돌려준다 (거르는 건 Task 043)
    @Test
    void t19_listProviders_영화와_드라마_모두_거르지_않고_전부_돌려준다() {
        for (String type : new String[] {"movie", "tv"}) {
            wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/" + type))
                    .withQueryParam("watch_region", equalTo("KR"))
                    .withQueryParam("language", equalTo("ko-KR"))
                    .willReturn(okJson("provider-list.json")));
        }

        TmdbProviderListResponse movie = tmdbClient.listProviders(TmdbMediaType.MOVIE);
        TmdbProviderListResponse tv = tmdbClient.listProviders(TmdbMediaType.TV);

        assertThat(movie.results()).extracting("providerId").containsExactly(8, 337, 9999);   // 9999는 7곳 밖
        assertThat(tv.results()).hasSize(3);
        assertThat(movie.results().get(0).displayPriorities()).containsEntry("KR", 3);
    }

    // T3: 쓰지 않는 필드·빈 날짜·null이 있어도 매핑이 실패하지 않는다
    @Test
    void t3_모르는_필드와_빈_날짜와_null이_있어도_매핑에_성공한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": 1, "title": "x", "overview": null, "release_date": "", "runtime": null,
                         "genres": [], "credits": null,
                         "unknown_object": {"a": 1}, "unknown_list": [1, 2, 3]}
                        """)));

        TmdbMovieDetail movie = tmdbClient.getMovie(1L, "ko-KR", null);

        assertThat(movie.id()).isEqualTo(1L);
        assertThat(movie.overview()).isNull();
        assertThat(movie.releaseDate()).isEmpty();
        assertThat(movie.runtime()).isNull();
        assertThat(movie.credits()).isNull();
    }

    // T3 보강: discover 샘플의 두 번째 항목(빈 날짜, null 포스터, 쓰지 않는 필드 adult·video·vote_count)
    @Test
    void t3_discover_샘플의_빈_날짜와_null_포스터도_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(okJson("discover-movie.json")));

        TmdbMovieSummary second = tmdbClient.discoverMovies("8", 1).results().get(1);

        assertThat(second.releaseDate()).isEmpty();
        assertThat(second.posterPath()).isNull();
        assertThat(second.overview()).isEmpty();
    }

    // T10: 별도 오버라이드 없이 본 yml의 타임아웃이 이 클라이언트에 연결돼 있다 (Task 024 설정 확인)
    @Test
    void t10_본_yml의_Feign_타임아웃_설정값이_그대로_보인다() {
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.readTimeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.connectTimeout")).isEqualTo("3000");
    }

    // 5.9의 3번: 테스트는 실제 TMDB가 아니라 WireMock을 본다
    @Test
    void base_url이_WireMock_주소로_바뀌어_있다() {
        assertThat(props.baseUrl()).startsWith("http://localhost");
    }
}
```

- `tmdbClient.getMovie(...)`처럼 래퍼 시그니처가 위 `TmdbClient`(얇은 판)와 같다. ⑥에서 완성판으로 바뀌어도 시그니처는 같아 테스트를 고치지 않는다.
- `assertThat(...).extracting("name")` 문자열 프로퍼티 추출은 record 접근자(`name()`)에도 동작한다. **확인 필요(컴파일 시 확인)**: AssertJ 버전에 따라 record 접근자를 못 찾으면 `extracting(TmdbGenre::name)`처럼 메서드 참조로 바꾼다.

#### 앞 단계와의 연결

- ①의 `TmdbProperties`·`TmdbFeignConfig`(인증 인터셉터)·예외 4종을 그대로 쓴다. 이 단계의 `TmdbClient`는 아직 예외를 변환하지 않아 오류 응답은 Feign 예외(`FeignException` 계열)로 새 나온다(⑥에서 변환).
- `TmdbTestSupport.TestApp`이 `TmdbConfig`를 가져온다. 이 시점의 `TmdbConfig`는 프로퍼티 등록뿐이라 `TmdbRateLimiter` 빈은 없다. `TmdbFeignConfig`도 그걸 요구하지 않는다(③에서 함께 생긴다).
- `TmdbTestSupport`가 `MeterRegistry`를 이미 제공하지만 얇은 `TmdbClient`는 쓰지 않는다. ⑥에서 쓰기 시작한다.
- `WireMockSmokeTest`와 `TmdbTestSupport`의 서버는 서로 독립이다.

#### 테스트 실행

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.TmdbClientTest"
```

(Docker 불필요.) 기대: tests=15 failures=0 (t1 1, t2 9, t19 1, t3 2, t10 1, base_url 1).

#### 여기서 멈추고 확인할 것

① `Authorization: Bearer test-token` 헤더가 나간다 ② 쿼리 파라미터가 의도대로 나간다 ③ 경로가 `/3/discover/movie`(base-url의 `/3` 포함) ④ **JSON이 record로 매핑된다**(Jackson 3 + Feign 디코더가 실제로 동작한다는 증거, TASK024 6절 미확인 항목의 해소) ⑤ 응답에 모르는 필드와 `""` 날짜가 있어도 실패하지 않는다 ⑥ null 쿼리가 빠진다. 이 단계가 통과해야 다음으로 간다. 통과하면 **WireMock 스텁에 걸리지 않은 요청이 없다는 것**과 가벼운 컨텍스트가 뜬다는 것(5.7의 확인 필요 해소)도 함께 확인된 것이다.

| 흔한 오류 | 해결 |
|---|---|
| `No qualifying bean of type 'TmdbApi'` / `TmdbClient` 주입 실패 | `FeignConfig`가 `TestApp`에 import돼 있는지, `TmdbApi`가 `com.ottnavi.infra` 아래인지(`@EnableFeignClients(basePackages = "com.ottnavi.infra")`) 확인 |
| `NoSuchBeanDefinitionException: ... FeignHttpMessageConverters` 또는 디코더 관련 빈 오류 | `@ImportAutoConfiguration`에 `HttpMessageConvertersAutoConfiguration`·`JacksonAutoConfiguration`이 있는지 확인. 필요한 자동 구성이 더 있으면 같은 곳에 추가한다(5.7 가벼운 컨텍스트 항목의 확인 필요) |
| 요청이 `404`로 오고 `FeignException$NotFound` | 스텁이 안 맞았다. WireMock 서버 로그(요청 URL)와 스텁의 경로·쿼리를 대조한다. `app.tmdb.base-url`에 `/3`이 붙었는지 확인 |
| `Connection refused` / 다른 포트 | `TmdbTestSupport`의 서버를 클래스마다 새로 만들면 캐시된 컨텍스트의 옛 포트를 가리킨다. 서버는 `static` 하나여야 한다 |
| `UnrecognizedPropertyException` | DTO에 `@JsonIgnoreProperties(ignoreUnknown = true)`가 빠졌다(함정 1) |
| 숫자/`null` 관련 `MismatchedInputException`(원시형에 null) | 해당 필드를 `Integer`·`Long`·`Double`로 바꾼다 |
| `Could not extract response: no suitable HttpMessageConverter` | 응답 `Content-Type`이 `application/json`인지(스텁의 `okJson`이 지정함), 컨버터가 등록됐는지 확인 |
| 텍스트 블록(`"""`) 컴파일 오류 | Java 17이면 지원된다. 인덴트와 종료 `"""` 위치 확인 |
| 한글 샘플이 깨짐 | JSON 파일을 BOM 없는 UTF-8로 저장했는지 확인(5.8절 4번) |

### 5.3 단계 ③ Bucket4j 로컬 버킷과 호출 전 토큰 획득

#### 건드리는 파일

| 경로 (`backend/` 기준) | 구분 | 역할 |
|---|---|---|
| `src/main/java/com/ottnavi/infra/tmdb/TmdbRateLimiter.java` | 수정(교체) | 로컬 버킷에서 토큰 1개를 얻을 때까지 최대 `max-wait` 기다린다 |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbConfig.java` | 수정(완성본으로 교체) | 버킷 빈(`tmdbBucket`)과 `TmdbRateLimiter` 빈 |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbFeignConfig.java` | 수정(빈 1개 추가) | 호출 직전에 `acquire()`를 부르는 인터셉터 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbRateLimiterTest.java` | 신규 | T16 (Spring 없이 단위) |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbClientTest.java` | 수정(테스트 1개 추가) | 설정된 한도 합이 40 미만인지 확인 |

#### 파일별 전체 코드

**`TmdbRateLimiter.java`**

```java
package com.ottnavi.infra.tmdb;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import lombok.RequiredArgsConstructor;

/**
 * TMDB 호출 속도를 서버 한 대(JVM) 안에서 제한하는 로컬 버킷.
 * 버킷은 "토큰이 든 통"이다. 호출 한 번에 토큰 한 개를 쓰고 통은 정해진 속도로 다시 채워진다.
 * 이 클래스는 TMDB 한도(약 초당 40회)를 서버 전체가 넘지 않게 하는 안전망이고, 익명 방문자별 요청 제한(Task 049)과는 다른 장치다(2절 3번).
 */
@RequiredArgsConstructor
public class TmdbRateLimiter {

    private final Bucket bucket;      // 토큰 통(JVM 메모리). 빈 이름 tmdbBucket
    private final Duration maxWait;   // 토큰이 없을 때 최대 기다리는 시간

    /** 토큰 1개를 얻을 때까지 최대 maxWait 기다린다. 못 얻으면 TmdbUnavailableException으로 포기한다 */
    public void acquire() {
        try {
            if (!bucket.asBlocking().tryConsume(1, maxWait)) {
                throw new TmdbUnavailableException("TMDB 호출 한도 대기 시간을 넘겼다");   // 로컬 한도 포기
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 인터럽트 상태를 복원한 뒤 포기한다
            throw new TmdbUnavailableException("TMDB 호출 대기 중 중단되었다", e);
        }
    }
}
```

**`TmdbConfig.java`** (③의 완성본)

```java
package com.ottnavi.infra.tmdb;

import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TMDB 연동 공통 빈: 설정 프로퍼티 등록, 로컬 버킷, 속도 제한기 */
@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbConfig {

    /**
     * TMDB 호출 한도용 로컬 버킷. 앱 전체(부모 컨텍스트)에 하나만 둔다.
     * TmdbFeignConfig는 Feign 하위 컨텍스트라 거기서 만들면 클라이언트가 늘 때 버킷도 늘어나기 때문이다.
     * 050의 단건 수집과 043 배치가 같은 버킷을 함께 쓴다. 빈 이름을 붙이는 이유: Task 049의 Redis 버킷 등 다른 Bucket 빈과 구분하려고.
     */
    @Bean("tmdbBucket")
    Bucket tmdbBucket(TmdbProperties props) {
        TmdbProperties.RateLimit limit = props.rateLimit();
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.capacity())                                // 통 크기
                        .refillGreedy(limit.refillTokens(), limit.refillPeriod())) // 시간에 비례해 조금씩 채운다
                .build();
    }

    /** 호출 직전에 토큰을 얻는 속도 제한기 */
    @Bean
    TmdbRateLimiter tmdbRateLimiter(@Qualifier("tmdbBucket") Bucket tmdbBucket, TmdbProperties props) {
        return new TmdbRateLimiter(tmdbBucket, props.rateLimit().maxWait());
    }
}
```

**`TmdbFeignConfig.java` 수정** — ①의 `tmdbAuthInterceptor` 메서드 **아래**, 클래스를 닫는 `}` **위**에 아래 빈을 추가한다(import는 추가 필요 없음: `RequestInterceptor`·`@Bean`이 이미 있다).

```java
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }                                                          // ← ①의 tmdbAuthInterceptor 끝 (여기까지는 그대로)

    /**
     * 요청을 보내기 직전에 로컬 버킷에서 토큰 1개를 얻는다.
     * 인터셉터는 재시도마다 다시 실행되므로(3.2절 5번) 재시도도 한도에 포함된다. 재시도도 TMDB에 가는 실제 요청이기 때문이다(6절 함정 6).
     */
    @Bean
    RequestInterceptor tmdbRateLimitInterceptor(TmdbRateLimiter rateLimiter) {
        return template -> rateLimiter.acquire();
    }
}                                                              // ← 클래스 끝
```

#### 앞 단계와의 연결

- `TmdbUnavailableException`은 ①에서 만들었다.
- `TmdbFeignConfig`가 이제 `TmdbRateLimiter`를 요구하므로 `TmdbConfig`가 완성본이어야 한다. `TmdbTestSupport.TestApp`이 `TmdbConfig`를 import하므로 ②의 테스트는 수정 없이 다시 돈다(버킷이 호출 앞에 끼어든다).
- `TmdbClient`(얇은 판)는 그대로다. 한도 초과 예외는 `TmdbUnavailableException`이라 래퍼를 거쳐도 그대로 나간다(⑥의 `call()`이 `local_limit`으로 센다).
- 인증과 버킷 인터셉터는 서로 다른 `@Bean`이라 둘 다 적용된다. 순서는 정해져 있지 않지만(`@Order` 없음) 결과에 영향이 없다.

#### 테스트 (T16)

**`TmdbRateLimiterTest.java`** — Spring 없이 직접 만든 작은 버킷으로 검사한다.

```java
package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TmdbRateLimiterTest {

    private static Bucket bucket(long capacity, long refillTokens, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillGreedy(refillTokens, refillPeriod))
                .build();
    }

    // T16-a: 결정적 케이스. 토큰이 없고 기다리지 않으면 즉시 포기
    @Test
    void t16_토큰이_없고_max_wait가_0이면_즉시_TmdbUnavailableException이다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofMinutes(1)), Duration.ZERO);

        limiter.acquire();   // 통에 있던 토큰 1개를 쓴다

        assertThatThrownBy(limiter::acquire).isInstanceOf(TmdbUnavailableException.class);
    }

    // T16-b: 타이밍 케이스. 토큰이 200ms마다 1개씩 채워지면 3회 연속 호출에 약 400ms가 걸린다 (CI 흔들림에 대비해 300ms 이상으로 검사)
    @Test
    void t16_토큰이_채워질_때까지_기다린_뒤_통과한다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofMillis(200)), Duration.ofSeconds(2));

        long start = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
    }

    // T16-c: 필요한 대기가 max-wait보다 길면 기다리지 않고 포기한다
    @Test
    void t16_필요한_대기가_max_wait보다_길면_포기한다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofMinutes(1)), Duration.ofMillis(50));
        limiter.acquire();

        long start = System.nanoTime();
        assertThatThrownBy(limiter::acquire).isInstanceOf(TmdbUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(500);   // 1분을 기다리지 않는다
    }

    // T16-d: 기다리는 도중 인터럽트되면 인터럽트 상태를 복원하고 포기한다
    @Test
    void t16_대기_중_인터럽트되면_상태를_복원하고_포기한다() {
        TmdbRateLimiter limiter = new TmdbRateLimiter(bucket(1, 1, Duration.ofSeconds(1)), Duration.ofSeconds(5));
        limiter.acquire();   // 토큰 소진. 다음 토큰까지 약 1초라 max-wait(5초) 안이므로 실제로 대기에 들어간다

        Thread.currentThread().interrupt();
        assertThatThrownBy(limiter::acquire).isInstanceOf(TmdbUnavailableException.class);

        assertThat(Thread.interrupted()).isTrue();   // 상태를 읽으면서 지운다. 지우지 않으면 다음 테스트가 오염된다
    }
}
```

**`TmdbClientTest.java`에 테스트 1개 추가** — 클래스 맨 아래(`base_url이_WireMock_주소로_바뀌어_있다` 뒤, 닫는 `}` 앞). 한도 40을 넘지 않는지 손으로 계산하는 대신 설정값으로 검사한다. 필요한 import는 없다(AssertJ `assertThat`은 이미 있다).

```java
    // T16-e: capacity + 1초 동안 채워지는 토큰이 TMDB 한도(약 40)보다 작다 (D1 확정: 10 + 25 = 35)
    @Test
    void t16_설정된_버킷의_순간_최대_호출_수가_40_미만이다() {
        TmdbProperties.RateLimit limit = props.rateLimit();
        double refillPerSecond = limit.refillTokens() * 1000.0 / limit.refillPeriod().toMillis();

        assertThat(limit.capacity() + refillPerSecond).isLessThan(40.0);
    }
```

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.TmdbRateLimiterTest" --tests "com.ottnavi.infra.tmdb.TmdbClientTest"
```

#### 여기서 멈추고 확인할 것

1. `TmdbRateLimiterTest` 4건 통과, `TmdbClientTest`는 ②의 15건 + 1건이 **그대로 통과**한다(버킷이 끼어들어도 정상 케이스가 안 깨진다).
2. **한도 값 직접 확인**: `capacity + refill-tokens × 1초`가 40 미만이다(10 + 25 = 35). 이 값이 바뀌면 T16-e가 실패해 알려 준다.
3. 5절 맨 앞의 결정(D1): 상한 있는 기다림(최대 2초) 후 포기 — 실패 시 `TmdbUnavailableException`.

| 흔한 오류 | 해결 |
|---|---|
| `No qualifying bean of type 'Bucket'` 또는 `expected single matching bean but found 2` | `@Bean("tmdbBucket")`과 `@Qualifier("tmdbBucket")`의 이름이 같은지. 나중에 다른 `Bucket` 빈이 생겨도 이 한정자 때문에 안전하다 |
| 컴파일 오류 `incompatible types ... BandwidthBuilderBuildStage` | `addLimit(람다)`의 람다가 `capacity(...).refillGreedy(...)`로 끝나야 한다. `.build()`를 람다 안에 쓰지 않는다. 확인 필요(컴파일 시 확인): bucket4j 8.21.0의 `addLimit(Function<BandwidthBuilderCapacityStage, BandwidthBuilderBuildStage>)`는 소스에서 확인했다 |
| T16-b가 간헐적으로 실패 | 기대 시간(약 400ms)보다 느려질 수는 있어도 빨라질 수는 없다. `isGreaterThanOrEqualTo(300)`가 깨지면 버킷 설정(`capacity 1`, `200ms`)이 바뀐 것이다 |
| T16-d 실패(인터럽트 후 대기에 안 들어감) | 다음 토큰까지의 대기가 `max-wait`보다 길면 대기 없이 바로 `false`다. refill 주기(1초)가 max-wait(5초)보다 짧은지 확인 |

### 5.4 단계 ④ ErrorDecoder (429·5xx → 재시도 가능, 4xx → 재시도 불가)

`ErrorDecoder`는 "응답이 오류 상태일 때 어떤 예외로 바꿀지" 정하는 Feign 부품이다. 여기서 `RetryableException`을 돌려주면 `Retryer`가 개입한다(3.2절).

| 응답 | 처리 | 재시도 |
|---|---|---|
| 429 | `RetryableException`(`Retry-After`가 있으면 그 시각을 담고 없으면 `null`) | 한다 |
| 5xx (500·502·503·504) | `RetryableException` | 한다 |
| 401 | `FeignException.Unauthorized` 그대로 → ⑥에서 `TmdbRequestException` | 안 한다 |
| 404 | `FeignException.NotFound` 그대로 → ⑥에서 `TmdbNotFoundException` (D3) | 안 한다 |
| 그 밖의 4xx (400·422 등) | `FeignException` 그대로 → ⑥에서 `TmdbRequestException`. **`Retry-After`가 붙어 있어도 재시도하지 않는다** | 안 한다 |

#### 건드리는 파일

| 경로 (`backend/` 기준) | 구분 | 역할 |
|---|---|---|
| `src/main/java/com/ottnavi/infra/tmdb/TmdbErrorDecoder.java` | 수정(교체) | 상태별 재시도 가능 여부 결정 |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbFeignConfig.java` | 수정(빈 1개 추가) | `ErrorDecoder` 빈 등록 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbErrorDecoderTest.java` | 신규 | T22 (Spring 없이 단위) |

#### 파일별 전체 코드

**`TmdbErrorDecoder.java`**

```java
package com.ottnavi.infra.tmdb;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

/**
 * TMDB 오류 응답을 재시도할 것(429·5xx)과 안 할 것(그 밖의 4xx)으로 나눈다.
 * Feign 기본 ErrorDecoder.Default는 상태 코드를 따지지 않고 Retry-After 헤더가 있는 응답만 RetryableException으로 감싼다(3.2절 3번).
 * 응답 본문은 로그에 넣지 않는다. FeignException 메시지에 본문이 들어 있으므로 사용자에게 보이는 메시지는 TmdbClient가 고정 문구로 다시 만든다(6절 함정 8).
 */
public class TmdbErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder fallback = new ErrorDecoder.Default();   // Retry-After 해석과 상태별 FeignException 생성을 재사용

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception base = fallback.decode(methodKey, response);   // FeignException, 또는 Retry-After가 있으면 그것을 감싼 RetryableException
        int status = response.status();
        boolean transientStatus = status == 429 || status >= 500;

        if (!transientStatus) {
            // 4xx는 Retry-After가 붙어 있어도 재시도하지 않는다. Default가 감싼 RetryableException을 원래 FeignException으로 풀어 준다
            // (감쌀 때 원인(cause)으로 원래 예외를 넣는다. 응답 본문을 다시 읽을 수 없으므로 errorStatus를 또 부르지 않는다)
            if (base instanceof RetryableException && base.getCause() instanceof FeignException original) {
                return original;
            }
            return base;
        }

        if (base instanceof RetryableException) {
            return base;   // Retry-After가 있는 429·5xx: 값이 이미 담겨 있다
        }
        Long retryAfter = null;   // Retry-After가 없다. 대기 시간은 Retryer의 지수 백오프가 정한다 (Long 변수로 둬서 Date 생성자와의 모호함을 피한다)
        return new RetryableException(
                status,
                base.getMessage(),
                response.request().httpMethod(),
                base,
                retryAfter,
                response.request());
    }
}
```

**`TmdbFeignConfig.java` 수정** — 이미 있는 `tmdbRateLimitInterceptor` 빈 **아래**, 클래스 닫는 `}` **위**에 추가하고 import 한 줄을 더한다.

import에 추가 (알파벳 순서로 `feign.RequestInterceptor` 아래):

```java
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;       // ← 추가
import lombok.extern.slf4j.Slf4j;
```

빈 추가:

```java
    @Bean
    RequestInterceptor tmdbRateLimitInterceptor(TmdbRateLimiter rateLimiter) {
        return template -> rateLimiter.acquire();
    }                                                          // ← ③의 마지막 빈 끝 (여기까지는 그대로)

    /** 429·5xx는 재시도 가능으로, 그 밖의 4xx는 재시도 불가로 나눈다 */
    @Bean
    ErrorDecoder tmdbErrorDecoder() {
        return new TmdbErrorDecoder();
    }
}                                                              // ← 클래스 끝
```

`errorDecoder` 속성(yml)이 아니라 설정 클래스의 `@Bean`으로 등록한다. 설정 속성과 빈이 같은 값을 정하면 속성이 이기지만(3.1절), yml에 `errorDecoder` 키가 없으니 빈이 쓰인다.

#### 앞 단계와의 연결

- ①의 예외는 아직 쓰지 않는다(변환은 ⑥).
- `TmdbFeignConfig`는 이제 인증·버킷 인터셉터 + `ErrorDecoder` 3개 빈이다. ②·③의 테스트는 그대로 통과해야 한다(정상 응답에는 `ErrorDecoder`가 개입하지 않는다).
- `Retryer`는 아직 없다(⑤). 지금은 Feign 기본 `Retryer.NEVER_RETRY`라 `RetryableException`이 나도 재시도하지 않고 그대로 던져진다.

#### 테스트 (T22)

**`TmdbErrorDecoderTest.java`** — `Response`를 직접 만들어 디코더만 검사한다. Spring·WireMock 불필요.

```java
package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TmdbErrorDecoderTest {

    private final TmdbErrorDecoder decoder = new TmdbErrorDecoder();

    private static Response response(int status, Map<String, Collection<String>> headers) {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://localhost/3/movie/1", Map.of(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(headers)
                .body("{}", StandardCharsets.UTF_8)
                .build();
    }

    private static Map<String, Collection<String>> noHeaders() {
        return Map.of();
    }

    private static Map<String, Collection<String>> retryAfter(String seconds) {
        return Map.<String, Collection<String>>of("Retry-After", List.of(seconds));
    }

    @Test
    void t22_429는_Retry_After가_없어도_재시도_가능이고_retryAfter는_null이다() {
        Exception result = decoder.decode("TmdbApi#test", response(429, noHeaders()));

        assertThat(result).isInstanceOf(RetryableException.class);
        assertThat(((RetryableException) result).retryAfter()).isNull();
    }

    @Test
    void t22_5xx는_재시도_가능이다() {
        for (int status : new int[] {500, 502, 503, 504}) {
            assertThat(decoder.decode("TmdbApi#test", response(status, noHeaders())))
                    .as("상태 %d", status)
                    .isInstanceOf(RetryableException.class);
        }
    }

    @Test
    void t22_429에_Retry_After가_있으면_그_시각이_담긴다() {
        Exception result = decoder.decode("TmdbApi#test", response(429, retryAfter("2")));

        assertThat(result).isInstanceOf(RetryableException.class);
        assertThat(((RetryableException) result).retryAfter()).isNotNull();
    }

    @Test
    void t22_404는_재시도_불가이고_NotFound다() {
        Exception result = decoder.decode("TmdbApi#test", response(404, noHeaders()));

        assertThat(result).isNotInstanceOf(RetryableException.class).isInstanceOf(FeignException.NotFound.class);
    }

    @Test
    void t22_401은_재시도_불가이고_Unauthorized다() {
        Exception result = decoder.decode("TmdbApi#test", response(401, noHeaders()));

        assertThat(result).isNotInstanceOf(RetryableException.class).isInstanceOf(FeignException.Unauthorized.class);
    }

    @Test
    void t22_400에_Retry_After가_붙어_있어도_재시도_불가다() {
        Exception result = decoder.decode("TmdbApi#test", response(400, retryAfter("1")));

        assertThat(result).isNotInstanceOf(RetryableException.class).isInstanceOf(FeignException.BadRequest.class);
    }
}
```

- `Request.create(HttpMethod, String, Map, byte[], Charset, RequestTemplate)`, `Response.builder().status/reason/request/headers/body(String, Charset)`는 feign-core 13.6.1 소스로 존재를 확인했다. **확인 필요(컴파일 시 확인)**: `Response.Builder#body(String, Charset)` 시그니처.
- `Map.<String, Collection<String>>of(...)`로 타입을 명시한 이유: `Map.of("..", List.of(..))`는 `Map<String, List<String>>`로 추론돼 `Map<String, Collection<String>>`에 대입되지 않는다.

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.TmdbErrorDecoderTest" --tests "com.ottnavi.infra.tmdb.TmdbClientTest"
```

#### 여기서 멈추고 확인할 것

1. T22 6건 통과 + ②·③ 테스트 그대로 통과.
2. 다음 규칙이 코드와 일치한다: ① 429·5xx는 **항상** 재시도 대상 ② `Retry-After`는 있으면 값을 담고 없으면 `null` ③ 4xx(429 제외)는 `Retry-After`가 붙어 있어도 재시도하지 않는다.
3. WireMock 수준의 "401·404·400이 정확히 1번만 요청"은 변환이 생기는 ⑥의 T11~T13에서 확인한다.

| 흔한 오류 | 해결 |
|---|---|
| `new RetryableException(...)`에서 "reference to RetryableException is ambiguous" | 다섯 번째 인자가 `Long`과 `Date` 생성자에 모두 맞는다. 위처럼 `Long retryAfter = null;` 변수로 두거나 `(Long) null`로 캐스팅한다 |
| `base.getCause() instanceof FeignException original` 컴파일 오류 | Java 17이어야 패턴 매칭이 된다(`build.gradle` toolchain 17). IDE의 언어 수준 확인 |
| 400 + Retry-After 테스트가 실패(RetryableException이 나옴) | `base.getCause()`가 `FeignException`이 아닌 경우다. `ErrorDecoder.Default`가 감쌀 때 원인을 넣는지는 feign-core 13.6.1 소스에서 확인했으나 **실행 시 확인** 필요 |

### 5.5 단계 ⑤ Retryer (지수 백오프, Retry-After 우선, 상한)

`Retryer`는 "실패했을 때 다시 할지, 얼마나 기다릴지"를 정하는 Feign 부품이다. Feign 기본 `Retryer.Default`는 `Retry-After`를 `maxPeriod`로 잘라서 기다린 뒤 **다시 시도한다**(소스 확인). 서버가 "30초 뒤에 오라"고 했는데 1초 기다렸다가 또 429를 맞는 셈이라, 이 프로젝트는 직접 만든다.

동작 규칙 (값은 D2에서 확정, 설정 키 `app.tmdb.retry.*`):

1. 시도 횟수가 상한에 닿으면 **포기**한다(받은 `RetryableException`을 그대로 던진다. 래퍼가 ⑥에서 `TmdbUnavailableException`으로 바꾼다).
2. `RetryableException.retryAfter()`가 있으면 `retryAfter - 현재시각`만큼 기다린다. 이 값이 `max-retry-after`(5초)보다 크면 **기다리지 않고 포기**한다(오래 기다려 봐야 배치가 DB 연결을 잡고 있기만 한다).
3. 없으면 지수 백오프: `initial × 2^(시도-1)`, `max-backoff`로 자른다. 작은 무작위 흔들림(jitter, ±20%)을 더해 여러 호출이 같은 순간에 다시 몰리지 않게 한다.
4. IO 오류(`status == -1`, 3.2절 3번)는 `io-max-attempts`를 적용한다.
5. 대기 중 `InterruptedException`이 나면 인터럽트 상태를 복원하고 포기한다.
6. `clone()`은 같은 설정의 새 인스턴스를 돌려준다(시도 횟수가 호출마다 0에서 시작하게). Feign은 호출마다 `retryer.clone()`을 부른다(소스 확인: `SynchronousMethodHandler.invoke`).

#### 건드리는 파일

| 경로 (`backend/` 기준) | 구분 | 역할 |
|---|---|---|
| `src/main/java/com/ottnavi/infra/tmdb/TmdbRetryer.java` | 수정(교체) | 재시도 정책 |
| `src/main/java/com/ottnavi/infra/tmdb/TmdbFeignConfig.java` | 수정(**완성본으로 교체**) | `Retryer` 빈 추가. 이 단계에서 4개 빈이 모두 갖춰진다 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbRetryerTest.java` | 신규 | T23 (Spring 없이 단위) |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbRetryTest.java` | 신규 | T4, T5, T14 (⑥에서 T6~T8 추가) |

#### 파일별 전체 코드

**`TmdbRetryer.java`**

```java
package com.ottnavi.infra.tmdb;

import feign.RetryableException;
import feign.Retryer;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

/**
 * TMDB 재시도 정책: 429·5xx는 총 maxAttempts회, 타임아웃·연결 오류는 총 ioMaxAttempts회. Retry-After가 있으면 우선하되 너무 길면 포기한다.
 * Retryer는 상태(attempt)를 가진다. Feign이 호출마다 clone()으로 새 인스턴스를 만들어 쓰므로 스레드 간 공유 문제는 없다.
 */
@Slf4j
public class TmdbRetryer implements Retryer {

    private static final double JITTER_RATIO = 0.2;   // 대기 시간의 ±20%

    private final TmdbProperties.Retry config;
    private int attempt;   // 지금까지 실패한 시도 횟수. 호출마다 clone()돼 0에서 시작한다

    public TmdbRetryer(TmdbProperties.Retry config) {
        this.config = config;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        attempt++;   // 방금 실패한 시도 번호(1부터)
        int limit = isIoError(e) ? config.ioMaxAttempts() : config.maxAttempts();
        if (attempt >= limit) {
            log.warn("TMDB 호출 재시도를 포기한다: 상태={}, 시도={}/{}", e.status(), attempt, limit);   // e.request()는 헤더(토큰)를 품고 있어 찍지 않는다
            throw e;
        }

        long waitMillis;
        Long retryAfter = e.retryAfter();   // 서버가 알려 준 "다시 해도 되는 시각"(epoch ms). 없으면 null
        if (retryAfter != null) {
            waitMillis = Math.max(0, retryAfter - System.currentTimeMillis());
            if (waitMillis > config.maxRetryAfter().toMillis()) {
                log.warn("TMDB가 너무 오래 기다리라고 해서 재시도를 포기한다: 상태={}, 요구 대기={}ms", e.status(), waitMillis);
                throw e;
            }
        } else {
            waitMillis = backoffWithJitterMillis();
        }

        log.warn("TMDB 호출을 재시도한다: 상태={}, 시도={}/{}, 대기={}ms", e.status(), attempt, limit, waitMillis);
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();   // 인터럽트 상태를 복원한 뒤 포기한다
            throw e;
        }
    }

    /** 호출마다 새 인스턴스를 만든다(시도 횟수가 0에서 다시 시작) */
    @Override
    public Retryer clone() {
        return new TmdbRetryer(config);
    }

    /** 타임아웃·연결 오류: Feign이 IOException을 상태 -1의 RetryableException으로 감싼다(3.2절 3번) */
    private static boolean isIoError(RetryableException e) {
        return e.status() == -1;
    }

    /** initial × 2^(시도-1)을 max로 자른 뒤 ±20% 흔든다 */
    private long backoffWithJitterMillis() {
        long exponential = config.initialBackoff().toMillis() * (1L << (attempt - 1));
        long capped = Math.min(exponential, config.maxBackoff().toMillis());
        double jitter = 1 + ThreadLocalRandom.current().nextDouble(-JITTER_RATIO, JITTER_RATIO);
        return Math.max(0, Math.round(capped * jitter));
    }
}
```

- `Retryer`는 `Cloneable`을 확장하고 `Retryer clone()`을 선언한다(소스 확인). 위처럼 `public Retryer clone()`으로 덮어쓰는 것이 Feign 기본 구현(`Retryer.Default`)과 같은 방식이다. **확인 필요(컴파일 시 확인)**: `Object.clone()`의 `throws CloneNotSupportedException`과 충돌하는 경고가 나오면 `@SuppressWarnings("MethodDoesntCallSuperMethod")`(IDE 경고일 뿐 컴파일 오류는 아니다).
- `RetryableException#retryAfter()`는 `Long`(epoch ms)을 돌려준다(소스 확인).
- `1L << (attempt - 1)`: `attempt`가 작아 오버플로가 없다(총 시도 3회).

**`TmdbFeignConfig.java` — ⑤의 완성본 (전체 교체)**

```java
package com.ottnavi.infra.tmdb;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * TMDB Feign 클라이언트 전용 설정.
 * @Configuration을 붙이지 않는다: 붙이면 컴포넌트 스캔에 잡혀 모든 Feign 클라이언트에 적용된다(6절 함정 3).
 * TmdbApi의 @FeignClient(configuration = TmdbFeignConfig.class)로만 연결한다.
 * 타임아웃·로그 수준은 여기가 아니라 application.yml(spring.cloud.openfeign.client.config.tmdb)에 있다(속성이 빈보다 우선한다, TASK024 방침).
 */
@Slf4j
public class TmdbFeignConfig {

    /** 모든 요청에 읽기 액세스 토큰을 Bearer로 붙인다. 토큰이 비어 있으면 헤더를 붙이지 않는다(호출은 TMDB가 401로 거절한다) */
    @Bean
    RequestInterceptor tmdbAuthInterceptor(TmdbProperties props) {
        String token = props.apiToken();
        if (token == null || token.isBlank()) {
            log.warn("TMDB 토큰이 비어 있다. TMDB_API_KEY 환경 변수를 설정하기 전까지 TMDB 호출은 401로 실패한다");
            return template -> { };
        }
        // 인터셉터는 재시도마다 다시 실행되지만 같은 값을 다시 넣어도 헤더가 중복되지 않는다(Feign이 값을 LinkedHashSet으로 보관)
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /**
     * 요청을 보내기 직전에 로컬 버킷에서 토큰 1개를 얻는다.
     * 인터셉터는 재시도마다 다시 실행되므로(3.2절 5번) 재시도도 한도에 포함된다. 재시도도 TMDB에 가는 실제 요청이기 때문이다(6절 함정 6).
     */
    @Bean
    RequestInterceptor tmdbRateLimitInterceptor(TmdbRateLimiter rateLimiter) {
        return template -> rateLimiter.acquire();
    }

    /** 429·5xx는 재시도 가능으로, 그 밖의 4xx는 재시도 불가로 나눈다 */
    @Bean
    ErrorDecoder tmdbErrorDecoder() {
        return new TmdbErrorDecoder();
    }

    /** 재시도 정책. Feign 기본 Retryer.NEVER_RETRY를 덮어쓴다. Feign이 호출마다 clone()해서 쓴다 */
    @Bean
    Retryer tmdbRetryer(TmdbProperties props) {
        return new TmdbRetryer(props.retry());
    }
}
```

최악 소요 시간 계산 (배치 chunk 안에서 호출할 때 DB 연결 점유 시간. 연결 3초·읽기 5초 기준, 버킷 대기 최대 2초는 시도마다 더해질 수 있음):

| 상황 | 시도 | 최악 소요 |
|---|---|---|
| 429 또는 5xx가 계속, Retry-After 없음 | 3회 | 응답 시간 3번 + 대기 0.5초 + 1초 = 약 2~3초 |
| 읽기 타임아웃이 계속 | `io-max-attempts` = 2회 | 5초 × 2 + 0.5초 = 약 10.5초 (3회로 두면 5 × 3 + 1.5 = 16.5초) |
| 연결 타임아웃이 계속 | 2회 | 3초 × 2 + 0.5초 = 약 6.5초 |

풀이 5개인 서버에서 chunk 하나가 DB 연결 하나를 잡는다고 보면, TMDB 장애 때 호출 하나당 10초 넘게 연결을 쥔다. 그래서 IO 오류 재시도를 2회로 줄였고(D2 확정), **chunk 크기는 043에서 작게** 잡는다(T-2의 조건). 근본 대책(회로 차단기 등)은 이 Task의 범위가 아니다.

#### 앞 단계와의 연결

- `TmdbProperties.Retry`(①), `RetryableException`을 만드는 `TmdbErrorDecoder`(④), 인터셉터가 시도마다 `TmdbRateLimiter`(③)를 부르는 구조가 여기서 합쳐진다.
- `TmdbClient`는 여전히 얇은 판이다. 실패가 계속될 때의 최종 예외 타입은 ⑥에서 정해지므로, 이 단계에서는 **재시도 후 성공하는 경로**(T4·T5·T14)와 정책 자체(T23 단위)를 검사한다. 포기 경로(T6·T7·T8·T9)는 ⑥ 뒤에 돌린다.
- Feign은 `Retryer` 타입 빈을 `getInheritedAwareOptional(context, Retryer.class)`로 찾아 쓴다(소스 확인). 설정 클래스의 빈이 `FeignClientsConfiguration`의 기본 `NEVER_RETRY`를 대체하는 것은 Spring Cloud 문서(`c7`)가 말하는 방식이다. **확인 필요(실행 시 확인)**: T4(429 → 200 성공)가 통과하면 대체된 것이다. 안 되면 요청 수가 1이 나온다.

#### 테스트 (T23, T4, T5, T14)

**`TmdbRetryerTest.java`** (T23) — 정책 자체를 Spring 없이 검사한다.

```java
package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TmdbRetryerTest {

    private static RetryableException retryable(int status, Long retryAfter) {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://localhost/3/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(status, "테스트", Request.HttpMethod.GET, retryAfter, request);
    }

    private Retryer newRetryer() {
        return new TmdbRetryer(TmdbTestFixtures.retry());   // 총 3회, IO는 총 2회, 대기 1~4ms
    }

    @Test
    void t23_429는_총_3번째_실패에서_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, null);

        assertThatCode(() -> retryer.continueOrPropagate(failure)).doesNotThrowAnyException();   // 1번째 실패 → 재시도
        assertThatCode(() -> retryer.continueOrPropagate(failure)).doesNotThrowAnyException();   // 2번째 실패 → 재시도
        assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);        // 3번째 실패 → 포기
    }

    @Test
    void t23_IO_오류는_총_2번째_실패에서_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException ioFailure = retryable(-1, null);

        assertThatCode(() -> retryer.continueOrPropagate(ioFailure)).doesNotThrowAnyException();
        assertThatThrownBy(() -> retryer.continueOrPropagate(ioFailure)).isSameAs(ioFailure);
    }

    @Test
    void t23_Retry_After가_max_retry_after보다_길면_기다리지_않고_바로_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, System.currentTimeMillis() + 60_000L);

        long start = System.nanoTime();
        assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);

        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(500);
    }

    @Test
    void t23_Retry_After가_짧으면_그만큼_기다린_뒤_재시도한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, System.currentTimeMillis() + 200L);

        long start = System.nanoTime();
        assertThatCode(() -> retryer.continueOrPropagate(failure)).doesNotThrowAnyException();

        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isGreaterThanOrEqualTo(150);
    }

    @Test
    void t23_clone하면_시도_횟수가_처음부터_다시_시작한다() {
        Retryer original = newRetryer();
        RetryableException failure = retryable(503, null);
        original.continueOrPropagate(failure);
        original.continueOrPropagate(failure);
        assertThatThrownBy(() -> original.continueOrPropagate(failure)).isSameAs(failure);   // 원본은 소진됨

        Retryer copy = original.clone();

        assertThatCode(() -> copy.continueOrPropagate(failure)).doesNotThrowAnyException();   // 복제본은 새로 시작
    }

    @Test
    void t23_대기_중_인터럽트되면_상태를_복원하고_포기한다() {
        Retryer retryer = newRetryer();
        RetryableException failure = retryable(429, System.currentTimeMillis() + 3_000L);   // 3초 대기(5초 상한 안)에 들어가게

        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);

        assertThat(Thread.interrupted()).isTrue();   // 상태를 읽으면서 지운다
    }
}
```

- `new RetryableException(int, String, HttpMethod, Long, Request)`는 소스에서 확인한 다섯 인자 생성자다. `Long` 타입 매개변수(`retryAfter`)로 넘겨 `Date` 오버로드와의 모호함을 피한다(리터럴 `null`을 직접 쓰면 모호하다).

**`TmdbRetryTest.java`** (T4, T5, T14. ⑥에서 T6~T8을 같은 클래스에 추가한다)

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TmdbRetryTest extends TmdbTestSupport {

    // T4: 429(Retry-After 없음) → 200. 지수 백오프로 재시도해 성공하고 요청은 총 2회
    @Test
    void t4_429_후_재시도로_성공한다() {
        stubFailThenOk(429, null);

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.results()).hasSize(2);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
    }

    // T5: 429 + Retry-After: 1 → 서버가 알려 준 시각까지 기다린 뒤 재시도. 소요 시간이 약 1초 이상 (여유 있게 800ms)
    @Test
    void t5_Retry_After_1이면_약_1초_기다린_뒤_성공한다() {
        stubFailThenOk(429, "1");

        long start = System.nanoTime();
        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(page.results()).hasSize(2);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(800);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
    }

    // T14: 인증 헤더가 시도마다 정확히 한 번(값 하나)만 붙는다
    @Test
    void t14_인증_헤더가_시도마다_한_번만_붙는다() {
        stubFailThenOk(429, null);

        tmdbClient.discoverMovies("8", 1);

        List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(requests).hasSize(2);
        for (LoggedRequest request : requests) {
            assertThat(request.getHeaders().getHeader("Authorization").values())
                    .containsExactly("Bearer " + TOKEN);
        }
    }
}
```

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.TmdbRetryerTest" --tests "com.ottnavi.infra.tmdb.TmdbRetryTest" --tests "com.ottnavi.infra.tmdb.TmdbClientTest"
```

#### 여기서 멈추고 확인할 것

1. T23 6건, T4·T5·T14 3건 통과. T5는 약 1초 걸린다.
2. T4에서 요청이 정확히 **2번**이다(1번이면 `Retryer` 빈이 적용되지 않은 것, 3번 이상이면 `TmdbErrorDecoder`/`Retryer` 중복 등록을 의심).
3. 로그에 `TMDB 호출을 재시도한다: 상태=429, 시도=1/3, 대기=...ms`가 `warn`으로 나온다(토큰 없음).
4. 최악 소요 시간 표(위)와 `application.yml`의 설정값이 일치한다.

| 흔한 오류 | 해결 |
|---|---|
| T4에서 요청이 1번이고 `RetryableException`이 터진다 | `Retryer` 빈이 적용되지 않았다. `TmdbFeignConfig`에 `tmdbRetryer`가 있는지, `TmdbApi`의 `configuration = TmdbFeignConfig.class`가 있는지, 이 클래스가 스캔되는 `@Configuration`이 아닌지 확인 |
| `Retryer` 빈 충돌 `NoUniqueBeanDefinitionException` | Feign 기본 `NEVER_RETRY`와 우리 빈이 둘 다 있을 때. `FeignClientsConfiguration`의 기본 빈은 `@ConditionalOnMissingBean`이다. 그래도 나면 `getInheritedAwareOptional`이 이름으로 구분하지 못한다는 뜻이라 빈 이름을 `retryer`로 바꿔 보고, 결과를 보고한다 |
| `clone()` 컴파일 경고·오류 | 위 5.5의 `clone()` 메모 |
| T5가 800ms 미만으로 끝남 | `Retry-After: 1`이 무시됐다. `TmdbErrorDecoder`에서 헤더가 있는 429를 `RetryableException`(retryAfter 있음)으로 돌려주는 분기를 확인 |
| 테스트가 느림 | `app.tmdb.retry.initial-backoff`·`max-backoff` 테스트 프로퍼티(10ms·40ms)가 `TmdbTestSupport`에 있는지 확인 |

### 5.6 단계 ⑥ 예외 변환과 Micrometer `tmdb.call` 카운터

#### 건드리는 파일

| 경로 (`backend/` 기준) | 구분 | 역할 |
|---|---|---|
| `src/main/java/com/ottnavi/infra/tmdb/TmdbClient.java` | 수정(**완성판으로 교체**) | 모든 호출을 `call()`로 감싼다: 지표 + 예외 변환 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbRetryTest.java` | 수정(메서드 추가) | T6, T7, T8 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbErrorTest.java` | 신규 | T11, T12, T13, T24 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbTimeoutTest.java` | 신규 | T9 (별도 컨텍스트: readTimeout 300ms) |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbMetricsTest.java` | 신규 | T17 |
| `src/test/java/com/ottnavi/infra/tmdb/TmdbTokenLogTest.java` | 신규 | T15 (별도 컨텍스트: DEBUG 로그) |

예외 클래스 4종은 ①에서 이미 만들었다.

#### 예외 종류와 `tmdb.call` 카운터 설계

| 예외 | 언제 | 소비자가 하는 일 |
|---|---|---|
| `TmdbUnavailableException` | 429·5xx·타임아웃·연결 오류가 재시도 후에도 계속, 또는 로컬 한도 대기 초과, 응답 매핑 실패(`DecodeException`) | 일시적. 배치는 실패 건수로 세고 계속하거나 Job을 실패 처리, 050은 DB 결과만 반환. **TMDB가 준 429도 여기로 모여 `EXTERNAL_API_ERROR`(502)로 응답된다**(`RATE_LIMIT_EXCEEDED`가 아니다, 8절 6번) |
| `TmdbRequestException` | 401·400·422 등 | **코드나 설정 문제**. 재시도해도 같다. 토큰 확인 |
| `TmdbNotFoundException` | 404 (D3) | "그 ID가 TMDB에 없다". 호출한 수집 배치가 이 예외를 받아 **그 작품만 건너뛰고 계속**한다(7.1절). "KR 제공처 없음"(정상 200 응답의 빈 데이터)과 **구별**해서 처리 |

- 규칙(`backend.md`): 커스텀 예외는 `(String message)`, `(String message, Throwable cause)` 두 생성자만 둔다. 예외 메시지는 오류 응답 `detail`로 나갈 수 있으므로 **고정 문구**만 쓰고 URL·본문·토큰을 넣지 않는다. 원인은 `cause`로 연결한다.
- 로그 심각도: 재시도 중 한 번의 실패는 `warn`(`TmdbRetryer`). 최종 포기는 `warn`(예상 가능한 실패). `error`는 401(설정 문제)처럼 사람이 봐야 하는 것만 쓴다. **`e.request()`를 로그에 찍지 않는다**(헤더에 토큰).

| 태그 | 값 (닫힌 집합) | 이유 |
|---|---|---|
| `endpoint` | `discover_movie`, `discover_tv`, `search_movie`, `search_tv`, `movie_detail`, `tv_detail`, `tv_season`, `movie_providers`, `tv_season_providers`, `provider_list` (10개) | 호출 종류별 사용량(R11-26 시즌 호출량 측정에 직접 쓴다) |
| `result` | `success`, `not_found`, `rate_limited`, `server_error`, `client_error`, `io_error`, `local_limit`, `decode_error` (8개) | 성공/실패 분류. 429와 5xx를 나눠 두면 한도 문제와 TMDB 장애를 구별한다. **`decode_error`는 이 개정에서 추가했다**(응답 해석 실패를 `server_error`와 섞으면 TMDB 응답 형식이 바뀐 것을 못 알아챈다) |

- 시리즈 수는 최대 10 × 8 = 80개로 닫혀 있다. **작품 ID·검색어·URL·페이지 번호는 절대 태그로 쓰지 않는다**(3.6절).
- 카운트 단위는 **래퍼 호출 한 번(재시도 포함해 최종 결과 1회)** 이다. 시도 횟수가 궁금하면 `TmdbRetryer`에서 `tmdb.retry` 카운터를 추가하는 선택지가 있으나 필수가 아니다.
- 이름은 `backend.md`가 정한 `tmdb.call`을 쓴다. Feign이 자체적으로 만드는 관측값(`MicrometerObservationCapability`, 소스 확인)과 이름이 겹치지 않는다. 겹치거나 시리즈가 과도하면 `spring.cloud.openfeign.client.config.tmdb.micrometer.enabled: false`(`c7` 확인된 속성)로 Feign 쪽을 끌 수 있다(확인 필요: 이 버전에서 실제 이름·태그).
- 카운터는 호출 때마다 `meterRegistry.counter(이름, 태그...)`로 조회한다. 같은 이름·태그면 같은 카운터를 돌려주므로 필드에 캐시하지 않아도 된다(테스트의 `meterRegistry.clear()`와도 맞는다).

#### 파일별 전체 코드

**`TmdbClient.java` — 완성판 (②의 얇은 판을 통째로 교체)**

```java
package com.ottnavi.infra.tmdb;

import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import com.ottnavi.infra.tmdb.exception.TmdbException;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.infra.tmdb.exception.TmdbRequestException;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import feign.FeignException;
import feign.codec.DecodeException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 소비 Task(042~044, 050)가 쓰는 TMDB 호출 창구(D5).
 * 고정 쿼리 값(KR, ko-KR, flatrate)을 채우고, 모든 호출을 call()로 감싸 tmdb.call 카운터를 세고 Feign 예외를 TmdbException으로 바꾼다.
 * 반환 DTO(infra.tmdb.dto)는 받자마자 도메인 값으로 바꿔 쓰고 오래 들고 다니거나 api 응답에 내보내지 않는다(6절 함정 12).
 * 외부 호출이므로 트랜잭션 밖에서 부른다(수집 배치만 예외, TECH T-2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbClient {

    private static final String REGION = "KR";                        // PRD: 한국 제공처만 본다
    private static final String LANGUAGE_KO = "ko-KR";
    private static final String MONETIZATION_FLATRATE = "flatrate";   // 후보 수집은 구독형만

    // tmdb.call 카운터: 이름과 태그 값. 값 집합은 닫혀 있다(작품 ID·검색어·URL·페이지 번호는 태그로 쓰지 않는다, 3.6절)
    private static final String METRIC_NAME = "tmdb.call";
    private static final String EP_DISCOVER_MOVIE = "discover_movie";
    private static final String EP_DISCOVER_TV = "discover_tv";
    private static final String EP_SEARCH_MOVIE = "search_movie";
    private static final String EP_SEARCH_TV = "search_tv";
    private static final String EP_MOVIE_DETAIL = "movie_detail";
    private static final String EP_TV_DETAIL = "tv_detail";
    private static final String EP_TV_SEASON = "tv_season";
    private static final String EP_MOVIE_PROVIDERS = "movie_providers";
    private static final String EP_TV_SEASON_PROVIDERS = "tv_season_providers";
    private static final String EP_PROVIDER_LIST = "provider_list";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_NOT_FOUND = "not_found";
    private static final String RESULT_RATE_LIMITED = "rate_limited";
    private static final String RESULT_SERVER_ERROR = "server_error";
    private static final String RESULT_CLIENT_ERROR = "client_error";
    private static final String RESULT_IO_ERROR = "io_error";
    private static final String RESULT_LOCAL_LIMIT = "local_limit";
    private static final String RESULT_DECODE_ERROR = "decode_error";

    private final TmdbApi api;
    private final MeterRegistry meterRegistry;

    // 후보 수집·검색

    /** 제공처(ID, 쉼표 AND·파이프 OR)별 KR 구독 후보 영화를 한 쪽씩 가져온다 */
    public TmdbPage<TmdbMovieSummary> discoverMovies(String withWatchProviders, int page) {
        return call(EP_DISCOVER_MOVIE,
                () -> api.discoverMovies(withWatchProviders, REGION, MONETIZATION_FLATRATE, LANGUAGE_KO, page));
    }

    /** 제공처별 KR 구독 후보 드라마를 한 쪽씩 가져온다 */
    public TmdbPage<TmdbTvSummary> discoverTvs(String withWatchProviders, int page) {
        return call(EP_DISCOVER_TV,
                () -> api.discoverTvs(withWatchProviders, REGION, MONETIZATION_FLATRATE, LANGUAGE_KO, page));
    }

    /** 영화를 제목으로 검색한다 (성인물 제외). query는 사용자 입력이라 로그·태그에 쓰지 않는다 */
    public TmdbPage<TmdbMovieSummary> searchMovies(String query, int page) {
        return call(EP_SEARCH_MOVIE, () -> api.searchMovies(query, LANGUAGE_KO, page, false));
    }

    /** 드라마를 제목으로 검색한다 */
    public TmdbPage<TmdbTvSummary> searchTvs(String query, int page) {
        return call(EP_SEARCH_TV, () -> api.searchTvs(query, LANGUAGE_KO, page, null));
    }

    // 상세·시즌

    /** 영화 상세를 가져온다. appendToResponse에 "credits"를 주면 출연진이 같이 온다(null이면 안 붙인다) */
    public TmdbMovieDetail getMovie(long movieId, String language, String appendToResponse) {
        return call(EP_MOVIE_DETAIL, () -> api.getMovie(movieId, language, appendToResponse));
    }

    /** 드라마 상세(시즌 목록 포함)를 가져온다 */
    public TmdbTvDetail getTv(long tvId, String language, String appendToResponse) {
        return call(EP_TV_DETAIL, () -> api.getTv(tvId, language, appendToResponse));
    }

    /** 드라마 한 시즌의 회차 목록을 가져온다 */
    public TmdbTvSeasonDetail getTvSeason(long tvId, int seasonNumber, String language) {
        return call(EP_TV_SEASON, () -> api.getTvSeason(tvId, seasonNumber, language));
    }

    // 제공처

    /** 영화의 나라별 시청 제공처를 가져온다(KR 골라 쓰기는 소비 쪽) */
    public TmdbWatchProvidersResponse getMovieWatchProviders(long movieId) {
        return call(EP_MOVIE_PROVIDERS, () -> api.getMovieWatchProviders(movieId));
    }

    /** 드라마 한 시즌의 나라별 시청 제공처를 가져온다 */
    public TmdbWatchProvidersResponse getTvSeasonWatchProviders(long tvId, int seasonNumber) {
        return call(EP_TV_SEASON_PROVIDERS, () -> api.getTvSeasonWatchProviders(tvId, seasonNumber));
    }

    /** KR의 제공처 전체 목록을 가져온다. 우리 7곳 외 제공처도 거르지 않고 돌려준다(저장은 Task 043) */
    public TmdbProviderListResponse listProviders(TmdbMediaType mediaType) {
        return call(EP_PROVIDER_LIST, () -> api.listProviders(mediaType.getPath(), REGION, LANGUAGE_KO));
    }

    // 공통 처리

    /** 호출을 실행하고 결과를 tmdb.call로 센다. Feign 예외는 TmdbException으로 바꿔 던진다 */
    private <T> T call(String endpoint, Supplier<T> action) {
        try {
            T result = action.get();
            count(endpoint, RESULT_SUCCESS);
            return result;
        } catch (TmdbException e) {
            // 호출 전 속도 제한 인터셉터가 던진 예외(로컬 한도 대기 초과 등). 이미 변환된 예외라 그대로 올린다
            count(endpoint, RESULT_LOCAL_LIMIT);
            log.warn("TMDB 호출 전 로컬 한도에서 포기했다: endpoint={}", endpoint);
            throw e;
        } catch (FeignException e) {
            // 재시도 후에도 실패한 경우와 응답 해석 실패(DecodeException 포함)
            Failure failure = toFailure(e);
            count(endpoint, failure.result());
            logFailure(endpoint, e.status(), failure.result());
            throw failure.exception();
        }
    }

    /** FeignException을 결과 태그와 TmdbException 쌍으로 바꾼다. 메시지는 고정 문구, 원인은 cause로 남긴다 */
    private Failure toFailure(FeignException e) {
        if (e instanceof DecodeException) {
            // 상태가 200이어도 JSON을 DTO로 못 바꾼 경우. 재시도 대상이 아니며 TMDB 응답 형식 변화를 의심한다
            return new Failure(RESULT_DECODE_ERROR, new TmdbUnavailableException("TMDB 응답을 해석하지 못했다", e));
        }
        int status = e.status();
        if (status == 404) {
            return new Failure(RESULT_NOT_FOUND, new TmdbNotFoundException("TMDB에 요청한 리소스가 없다", e));
        }
        if (status == 429) {
            return new Failure(RESULT_RATE_LIMITED, new TmdbUnavailableException("TMDB 호출 한도 초과로 재시도 후에도 실패했다", e));
        }
        if (status >= 500) {
            return new Failure(RESULT_SERVER_ERROR, new TmdbUnavailableException("TMDB 서버 오류로 호출에 실패했다", e));
        }
        if (status == -1) {
            // 연결 실패·읽기 타임아웃·연결 끊김: Feign이 IOException을 상태 -1의 RetryableException으로 감싼다
            return new Failure(RESULT_IO_ERROR, new TmdbUnavailableException("TMDB에 연결하지 못했거나 응답 시간을 넘겼다", e));
        }
        return new Failure(RESULT_CLIENT_ERROR, new TmdbRequestException("TMDB가 요청을 거절했다. 요청 값과 토큰 설정을 확인한다", e));
    }

    /** 최종 실패를 심각도에 맞게 남긴다. e.request()나 응답 본문은 찍지 않는다(헤더에 토큰이 있다) */
    private void logFailure(String endpoint, int status, String result) {
        if (status == 401) {
            log.error("TMDB 인증에 실패했다. TMDB_API_KEY(읽기 액세스 토큰) 설정을 확인한다: endpoint={}", endpoint);
        } else {
            log.warn("TMDB 호출이 실패했다: endpoint={}, result={}, status={}", endpoint, result, status);
        }
    }

    private void count(String endpoint, String result) {
        meterRegistry.counter(METRIC_NAME, "endpoint", endpoint, "result", result).increment();
    }

    /** 변환 결과 묶음: 카운터 태그 값 + 던질 예외 */
    private record Failure(String result, TmdbException exception) {
    }
}
```

- `@RequiredArgsConstructor`로 생성자가 `(TmdbApi api, MeterRegistry meterRegistry)` 순서로 만들어진다(필드 선언 순서). 단위 테스트에서 `new TmdbClient(api, registry)`로 쓴다.
- `FeignException#status()`, `feign.codec.DecodeException`, `FeignException.NotFound` 등은 feign-core 13.6.1 소스에서 확인했다. `MeterRegistry#counter(String, String...)`는 Micrometer 표준 API다(`c7`).
- `catch (TmdbException e)`는 `FeignException`보다 앞에 둔다(둘은 상속 관계가 없어 순서는 상관없지만 읽기 쉽게 변환된 예외를 먼저 처리한다).
- 알려진 한계: 위 두 분기에 들지 않는 예외(예: 인터셉터의 예기치 못한 `RuntimeException`)는 변환·카운트 없이 그대로 올라간다. 필요하면 소비 쪽 공통 처리(`GlobalExceptionHandler`의 500)에서 드러난다.
- 예외가 `BusinessException`이라 `GlobalExceptionHandler`가 `EXTERNAL_API_ERROR`(502)로 응답한다. 이 예외의 `cause`(FeignException)의 메시지에는 요청 URL(검색어 포함)과 응답 본문이 들어 있어 서버 로그의 스택 트레이스에 남을 수 있다. 요청 **헤더(토큰)는 들어 있지 않다**(6절 함정 9).

#### 앞 단계와의 연결

- 이제 ①의 예외, ③·④·⑤의 Feign 설정이 하나로 이어진다: `TmdbRateLimiter`가 던진 `TmdbUnavailableException`은 `call()`이 `local_limit`으로 세고, Feign이 던진 `RetryableException`(재시도 소진)·`FeignException`은 `toFailure()`가 변환한다.
- 얇은 판과 **공개 메서드 시그니처가 같다.** `TmdbClientTest`(②)는 수정 없이 그대로 통과해야 한다.
- 이 단계부터 `TmdbTestSupport`의 `MeterRegistry` 빈(`SimpleMeterRegistry`)이 실제로 쓰인다. 앱 전체(통합 테스트 ⑦)에서는 Boot 액추에이터가 만든 `MeterRegistry`가 주입된다.

#### 테스트 (T6, T7, T8, T9, T11, T12, T13, T15, T17, T24)

**`TmdbRetryTest.java`에 메서드 추가** (T6, T7, T8). 클래스 맨 아래(T14 뒤, 닫는 `}` 앞)에 붙이고 import를 보강한다.

추가할 import (기존 import 목록에 합친다):

```java
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
```

추가할 메서드:

```java
    // T6: 429가 계속되면 정확히 max-attempts번 요청한 뒤 포기한다. 502(EXTERNAL_API_ERROR)이고 RATE_LIMIT_EXCEEDED(429)가 아니다 (8절 6번)
    @Test
    void t6_429가_계속되면_max_attempts번_요청_후_TmdbUnavailableException으로_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(errorResponse(429)));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1))
                .isInstanceOfSatisfying(TmdbUnavailableException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(502);
                    assertThat(e.getErrorCode()).isNotEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
                });

        wireMock.verify(props.retry().maxAttempts(), getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "rate_limited")).isEqualTo(1.0);
    }

    // T7: Retry-After가 max-retry-after(5초)보다 크면 기다리지 않고 요청 1회로 포기한다
    @Test
    void t7_Retry_After가_상한보다_크면_요청_1회로_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .willReturn(errorResponse(429).withHeader("Retry-After", "60")));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "rate_limited")).isEqualTo(1.0);
    }

    // T8-a: 503 → 200이면 재시도로 성공한다
    @Test
    void t8_503_후_재시도로_성공한다() {
        stubFailThenOk(503, null);

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.results()).hasSize(2);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "success")).isEqualTo(1.0);
    }

    // T8-b: 500이 계속되면 max-attempts번 요청 후 TmdbUnavailableException
    @Test
    void t8_500이_계속되면_max_attempts번_요청_후_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(errorResponse(500)));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        wireMock.verify(props.retry().maxAttempts(), getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "server_error")).isEqualTo(1.0);
    }
```

**`TmdbErrorTest.java`** (T11, T12, T13, T24)

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.infra.tmdb.exception.TmdbRequestException;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TmdbErrorTest extends TmdbTestSupport {

    // T11: 401은 코드·설정 문제라 재시도하지 않는다
    @Test
    void t11_401은_TmdbRequestException이고_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(401)));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
        assertThat(callCount("movie_detail", "client_error")).isEqualTo(1.0);
    }

    // T12: 404는 TmdbNotFoundException (D3). 재시도하지 않는다
    @Test
    void t12_404는_TmdbNotFoundException이고_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(404)));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbNotFoundException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
        assertThat(callCount("movie_detail", "not_found")).isEqualTo(1.0);
    }

    // T13: 그 밖의 4xx(400·422)도 재시도하지 않는다
    @ParameterizedTest
    @ValueSource(ints = {400, 422})
    void t13_그_밖의_4xx는_TmdbRequestException이고_한_번만_요청한다(int status) {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(status)));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
    }

    // T13 보강: 4xx에 Retry-After가 붙어 있어도 재시도하지 않는다 (ErrorDecoder.Default는 헤더만 보고 감싸므로 TmdbErrorDecoder가 거른다)
    @Test
    void t13_400에_Retry_After가_붙어_있어도_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(400).withHeader("Retry-After", "1")));

        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/1")));
    }

    // T24: 200인데 JSON을 DTO로 못 바꾸면 TmdbUnavailableException(decode_error). 재시도하지 않는다
    @Test
    void t24_응답을_해석하지_못하면_TmdbUnavailableException이고_한_번만_요청한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("이건 JSON이 아니다")));

        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(callCount("discover_movie", "decode_error")).isEqualTo(1.0);
    }
}
```

**`TmdbTimeoutTest.java`** (T9) — `readTimeout`을 300ms로 줄인 **별도 컨텍스트**를 쓴다. `@TestPropertySource`는 `application.yml`보다 우선하고, `TmdbTestSupport`의 `@DynamicPropertySource`와는 키가 달라 충돌하지 않는다.

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "spring.cloud.openfeign.client.config.tmdb.readTimeout=300")
class TmdbTimeoutTest extends TmdbTestSupport {

    // T9: 응답이 읽기 타임아웃(300ms)보다 늦으면 io-max-attempts번 시도한 뒤 TmdbUnavailableException으로 포기한다
    @Test
    void t9_읽기_타임아웃이면_io_max_attempts번_요청_후_포기한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH))
                .willReturn(okJson("discover-movie.json").withFixedDelay(1000)));   // 300ms 타임아웃보다 늦게 응답

        long start = System.nanoTime();
        assertThatThrownBy(() -> tmdbClient.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        wireMock.verify(props.retry().ioMaxAttempts(), getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE_PATH)));
        assertThat(elapsedMs).isLessThan(5000);   // 대략 300ms × 횟수 근처. 정상 yml(5초)로 돌았다면 10초 이상 걸렸을 것
        assertThat(callCount("discover_movie", "io_error")).isEqualTo(1.0);
    }
}
```

**`TmdbMetricsTest.java`** (T17)

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TmdbMetricsTest extends TmdbTestSupport {

    @Test
    void t17_성공하면_tmdb_call_success가_1_증가한다() {
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE_PATH)).willReturn(okJson("discover-movie.json")));

        tmdbClient.discoverMovies("8", 1);

        assertThat(callCount("discover_movie", "success")).isEqualTo(1.0);
    }

    @Test
    void t17_404는_not_found로_센다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/1")).willReturn(errorResponse(404)));

        assertThatThrownBy(() -> tmdbClient.getTv(1L, "ko-KR", null)).isInstanceOf(TmdbNotFoundException.class);

        assertThat(callCount("tv_detail", "not_found")).isEqualTo(1.0);
        assertThat(callCount("tv_detail", "success")).isZero();
    }

    @Test
    void t17_429_포기는_rate_limited_5xx_포기는_server_error로_센다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/1/season/1")).willReturn(errorResponse(429)));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/2/season/1")).willReturn(errorResponse(503)));

        assertThatThrownBy(() -> tmdbClient.getTvSeason(1L, 1, "ko-KR")).isInstanceOf(TmdbUnavailableException.class);
        assertThatThrownBy(() -> tmdbClient.getTvSeason(2L, 1, "ko-KR")).isInstanceOf(TmdbUnavailableException.class);

        assertThat(callCount("tv_season", "rate_limited")).isEqualTo(1.0);
        assertThat(callCount("tv_season", "server_error")).isEqualTo(1.0);
    }

    // 태그 값이 닫힌 집합인지: 작품 ID·URL 같은 값이 태그로 새지 않는다
    @Test
    void t17_tmdb_call의_태그는_endpoint와_result_두_개뿐이다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001")).willReturn(okJson("movie-detail.json")));
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900002")).willReturn(okJson("movie-detail.json")));

        tmdbClient.getMovie(900001L, "ko-KR", null);
        tmdbClient.getMovie(900002L, "ko-KR", null);

        assertThat(meterRegistry.find("tmdb.call").meters())
                .hasSize(1);   // 작품 ID가 달라도 시리즈는 늘지 않는다
        Set<String> tagKeys = meterRegistry.find("tmdb.call").meters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(Tag::getKey)
                .collect(Collectors.toSet());
        assertThat(tagKeys).containsExactlyInAnyOrder("endpoint", "result");
    }

    // 로컬 한도 초과(local_limit)는 WireMock으로 만들기 어려워 TmdbApi를 목으로 바꿔 단위로 검사한다
    @Test
    void t17_호출_전_로컬_한도_예외는_local_limit으로_센다() {
        TmdbApi api = mock(TmdbApi.class);
        when(api.discoverMovies(any(), any(), any(), any(), anyInt()))
                .thenThrow(new TmdbUnavailableException("테스트용 로컬 한도 초과"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TmdbClient client = new TmdbClient(api, registry);

        assertThatThrownBy(() -> client.discoverMovies("8", 1)).isInstanceOf(TmdbUnavailableException.class);

        assertThat(registry.get("tmdb.call").tag("endpoint", "discover_movie").tag("result", "local_limit")
                .counter().count()).isEqualTo(1.0);
    }
}
```

**`TmdbTokenLogTest.java`** (T15) — Feign 요청 로그(`TmdbApi` 로거)가 DEBUG로 켜진 별도 컨텍스트에서 재시도 성공 흐름(T4)과 포기 흐름(T6)을 실행하고, 출력에 토큰이 없는지 본다.

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.ottnavi.infra.tmdb=DEBUG")   // loggerLevel: basic이어도 Feign 로그는 해당 로거가 DEBUG여야 나온다
class TmdbTokenLogTest extends TmdbTestSupport {

    // T15: 재시도 성공(T4) 흐름과 포기(T6) 흐름의 로그에 토큰이 없다
    @Test
    void t15_DEBUG_로그에도_토큰이_나오지_않는다(CapturedOutput output) {
        stubFailThenOk(429, null);
        tmdbClient.discoverMovies("8", 1);

        wireMock.stubFor(get(urlPathEqualTo("/3/movie/1")).willReturn(errorResponse(429)));
        assertThatThrownBy(() -> tmdbClient.getMovie(1L, "ko-KR", null)).isInstanceOf(TmdbUnavailableException.class);

        assertThat(output.getAll()).contains("TmdbApi#discoverMovies");   // 요청 로그가 실제로 켜졌다는 증거. 이게 없으면 아래 검사는 의미가 없다
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }
}
```

- `Slf4jLogger`는 `basic` 수준에서 `[TmdbApi#discoverMovies(String,String,String,String,int)] ---> GET http://... HTTP/1.1` 형태로 남긴다. **확인 필요(실행 시 확인)**: 정확한 로그 문구는 실행해 봐야 안다. 첫 단언(`contains("TmdbApi#discoverMovies")`)이 깨지면 실제 출력에서 Feign 로그 줄을 찾아 맞추거나, 로거 이름을 `logging.level.com.ottnavi.infra.tmdb.TmdbApi=DEBUG`로 좁혀 본다.
- 검색어 같은 쿼리 문자열은 이 로그에 나오므로 운영에서 DEBUG를 켜지 않는다(6절 함정 16).

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.*"
```

(Docker 불필요. 통합 테스트 T20은 ⑦에서 만들므로 아직 없다.)

#### 여기서 멈추고 확인할 것

1. `TmdbClientTest`(②)가 수정 없이 통과한다(래퍼 교체로 깨지지 않음).
2. T6·T7·T8: 포기 경로의 요청 횟수가 설정값과 같다(429 계속 → `max-attempts` = 3회, `Retry-After: 60` → 1회, 500 계속 → 3회). 429 포기의 `getErrorCode()`가 `EXTERNAL_API_ERROR`/502다.
3. T9: 타임아웃 포기가 `io-max-attempts`(2)회, 5초 안에 끝난다.
4. T11~T13: 401·404·400·422가 **정확히 1회**만 요청된다(4xx에 `Retry-After`가 붙어도 1회).
5. T15: 토큰이 로그에 없다 + 로그가 켜졌다는 증거가 있다.
6. T17: `tmdb.call{endpoint=discover_movie,result=success}` 증가, 429 포기 `rate_limited`, 타임아웃 포기 `io_error`(T9), 응답 해석 실패 `decode_error`(T24), 로컬 한도 `local_limit`. **소비자(테스트)가 받는 예외가 모두 `TmdbException` 하위**이고 `FeignException`이 새지 않는다.

| 흔한 오류 | 해결 |
|---|---|
| `FeignException$...`이 그대로 테스트까지 올라온다 | `call()`의 `catch (FeignException e)`에 걸리지 않은 경로다. 예외 클래스가 `feign.FeignException`인지(다른 `FeignException` import 주의), `toFailure()`의 분기 확인 |
| T9가 `TmdbUnavailableException` 대신 시간 초과로 오래 걸린다 | `@TestPropertySource`가 적용되지 않아 5초 타임아웃으로 돈 것. 프로퍼티 키 철자(`readTimeout` 카멜 케이스)와 `application.yml`의 키가 같은지 확인 |
| T9 요청 수가 예상보다 많다 | JDK `HttpURLConnection`이 일부 연결 오류를 자체 재시도할 수 있다(확인 필요: 실행 시 확인). 타임아웃(`SocketTimeoutException`)은 재시도 대상이 아니다 |
| T15의 첫 단언(`contains("TmdbApi#discoverMovies")`)이 실패 | 위 메모대로 로그 줄을 확인한다. 로그가 안 켜졌다면 `logging.level` 프로퍼티가 컨텍스트에 적용됐는지, 콘솔 로그 출력이 캡처되는지(`OutputCaptureExtension`) 확인 |
| T17의 시리즈 수 단언(`hasSize(1)`) 실패 | 다른 테스트가 같은 레지스트리에 값을 남긴 것. `@BeforeEach`의 `meterRegistry.clear()`가 기반 클래스에 있는지 확인 |
| `t2_discoverTvs`(파이프 `8|337`)만 실패 | `|` 문자 인코딩 문제다. 043이 쉼표·단일 ID로 바꿔 부르면 된다(4절 1번의 "확인 필요"의 일부). WireMock은 디코딩된 값을 비교하므로 인코딩 방식 자체가 막힌 것이다 |

### 5.7 단계 ⑦ 통합 테스트 1건과 전체 확인

가벼운 테스트(②~⑥)는 필요한 자동 구성만 골라 올리므로 "실제 앱에서도 같은 조립이 되는가"를 못 본다. 이 단계의 통합 테스트 1건(D4, 7.2절)이 그 틈을 메운다. Docker Desktop이 켜져 있어야 한다(`IntegrationTestSupport`가 PostgreSQL·Redis 컨테이너를 띄운다).

#### 건드리는 파일

| 경로 (`backend/` 기준) | 구분 | 역할 |
|---|---|---|
| `src/test/java/com/ottnavi/infra/tmdb/TmdbClientIntegrationTest.java` | 신규 | T20 (앱 전체 컨텍스트) |
| `src/test/java/com/ottnavi/infra/tmdb/WireMockSmokeTest.java` | **삭제** | 5.0의 기동 확인용. `TmdbTestSupport`가 흡수 |
| `docs/TECH.md` 6절 WireMock Jetty 행 | 수정(문서) | 5.0의 실측 결과로 갱신. 무엇을 왜 바꿨는지 PR 설명에 적는다 |

#### 파일별 전체 코드

**`TmdbClientIntegrationTest.java`** (T20)

```java
package com.ottnavi.infra.tmdb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.support.IntegrationTestSupport;
import feign.RequestInterceptor;
import feign.Retryer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T20: 앱 전체 컨텍스트에서 TMDB 연동이 조립되는지 본다(Docker 필요).
 * 확인 항목: ① TMDB_API_KEY 없이 기동 ② 설정 바인딩과 빈 조립, TmdbFeignConfig가 앱 전체로 퍼지지 않음 ③ 본 yml 타임아웃 ④ 실제 앱 Jackson 설정으로 DTO 매핑.
 * 실제 TMDB는 부르지 않는다: base-url만 WireMock으로 돌린다.
 */
class TmdbClientIntegrationTest extends IntegrationTestSupport {

    // 이 클래스만 쓰는 서버. 이 클래스의 컨텍스트는 다른 테스트와 공유되지 않으므로(base-url이 달라 캐시 키가 다르다) 끝나면 멈춘다
    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");
        // api-token은 일부러 건드리지 않는다: 환경 변수 TMDB_API_KEY가 없는 운영과 같은 조건으로 기동되는지 본다
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TmdbClient tmdbClient;

    @Autowired
    private Environment environment;

    @Test
    void t20_토큰_없이_기동되고_TMDB_빈이_조립된다() {
        // 여기까지 왔다면 TMDB_API_KEY 없이 컨텍스트가 떴다는 뜻이다 (기본값 ${TMDB_API_KEY:}, 함정 5)
        assertThat(context.getBeansOfType(TmdbProperties.class)).hasSize(1);
        assertThat(context.getBeansOfType(TmdbApi.class)).hasSize(1);
        assertThat(context.getBeansOfType(TmdbClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(TmdbRateLimiter.class)).hasSize(1);
    }

    @Test
    void t20_TmdbFeignConfig는_앱_전체_컨텍스트로_퍼지지_않는다() {
        // 인증·버킷 인터셉터와 Retryer는 TMDB 전용 Feign 하위 컨텍스트에만 있어야 한다 (함정 3).
        // 확인 필요(실행 시 확인): 이후 다른 Feign 클라이언트나 라이브러리가 이 타입의 빈을 앱 전체에 등록하면 이 단언을 조정한다
        assertThat(context.getBeansOfType(TmdbFeignConfig.class)).isEmpty();
        assertThat(context.getBeansOfType(RequestInterceptor.class)).isEmpty();
        assertThat(context.getBeansOfType(Retryer.class)).isEmpty();
    }

    @Test
    void t20_설정_바인딩과_본_yml_타임아웃이_그대로다() {
        TmdbProperties props = context.getBean(TmdbProperties.class);

        assertThat(props.rateLimit().capacity()).isEqualTo(10);
        assertThat(props.rateLimit().refillTokens()).isEqualTo(25);
        assertThat(props.retry().maxAttempts()).isEqualTo(3);
        assertThat(props.retry().ioMaxAttempts()).isEqualTo(2);
        assertThat(props.toString()).contains("apiToken=***");
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.readTimeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.tmdb.connectTimeout")).isEqualTo("3000");
    }

    @Test
    void t20_대표_호출이_앱의_Jackson_설정으로_DTO에_매핑된다() {
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"page": 1, "total_pages": 1, "total_results": 1,
                         "results": [{"id": 900001, "title": "테스트 영화 1", "release_date": "", "adult": false}]}
                        """)));

        TmdbPage<TmdbMovieSummary> page = tmdbClient.discoverMovies("8", 1);

        assertThat(page.results()).hasSize(1);
        assertThat(page.results().get(0).title()).isEqualTo("테스트 영화 1");
        assertThat(page.results().get(0).releaseDate()).isEmpty();
    }
}
```

- `IntegrationTestSupport`가 `@SpringBootTest` + Testcontainers를 이미 제공한다(저장소에서 확인). `@DynamicPropertySource`는 하위 클래스의 `static` 메서드로 둔다.
- 앱 전체 컨텍스트에서는 `MeterRegistry`가 액추에이터 자동 구성으로 주입된다(`TmdbClient`의 두 번째 생성자 인자). **확인 필요(실행 시 확인)**: Boot 4.1에서 액추에이터의 `MeterRegistry` 빈이 이 프로젝트 설정으로 만들어지는지는 이 테스트가 처음 확인한다(없으면 `No qualifying bean of type MeterRegistry`).

#### 앞 단계와의 연결

- 모든 클래스(`TmdbProperties`·`TmdbConfig`·`TmdbRateLimiter`·`TmdbClient`)가 `OttnaviApplication`의 컴포넌트 스캔(`com.ottnavi.**`)에 잡힌다. `TmdbFeignConfig`는 `@Configuration`이 아니라 스캔되지 않는다(T20 둘째 테스트가 확인).
- `FeignConfig`의 `@EnableFeignClients(basePackages = "com.ottnavi.infra")`가 `TmdbApi`를 등록한다.
- 이 테스트의 `static` 서버는 `TmdbTestSupport`의 서버와 별개다(컨텍스트가 별개).

#### 전체 실행

```powershell
Remove-Item Env:TMDB_API_KEY -ErrorAction SilentlyContinue    # 이 셸에서만 지운다. 토큰이 없어도 통과해야 한다(5.9)
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.*"          # Docker Desktop을 켠다 (T20 때문)
.\gradlew.bat clean build                                       # 마지막에 전체
```

테스트 클래스와 T 번호 대응 (한눈에):

| 테스트 클래스 | T 번호 | Spring / Docker |
|---|---|---|
| `TmdbFeignConfigTest` | T18 | 없음 / 불필요 |
| `TmdbPropertiesTest` | T21 | 없음 / 불필요 |
| `TmdbClientTest` | T1, T2, T3, T10, T16(설정 합), T19 | 가벼운 컨텍스트 / 불필요 |
| `TmdbRateLimiterTest` | T16 | 없음 / 불필요 |
| `TmdbErrorDecoderTest` | T22 | 없음 / 불필요 |
| `TmdbRetryerTest` | T23 | 없음 / 불필요 |
| `TmdbRetryTest` | T4, T5, T6, T7, T8, T14 | 가벼운 컨텍스트 / 불필요 |
| `TmdbErrorTest` | T11, T12, T13, T24 | 가벼운 컨텍스트 / 불필요 |
| `TmdbTimeoutTest` | T9 | 가벼운 컨텍스트(별도) / 불필요 |
| `TmdbMetricsTest` | T17 | 가벼운 컨텍스트 / 불필요 |
| `TmdbTokenLogTest` | T15 | 가벼운 컨텍스트(별도) / 불필요 |
| `TmdbClientIntegrationTest` | T20 | 앱 전체 / **필요** |

#### 여기서 멈추고 확인할 것

1. 위 표의 모든 케이스가 통과하고, Docker 없이 도는 부분은 수 초(가벼운 컨텍스트 3개와 `TmdbTimeoutTest`·T5의 대기를 합쳐 십수 초 안팎)에 끝난다.
2. `TMDB_API_KEY`를 비운 셸로도 통과한다(5.9).
3. `WireMockSmokeTest.java`를 지웠고 `git status`에 의도하지 않은 파일(`.env`, 임시 JSON)이 없다.
4. 기존 테스트(`OttnaviApplicationTests`, 계약 대조 테스트 등)가 `clean build`에서 그대로 통과한다.

| 흔한 오류 | 해결 |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop을 켠다. 이 오류는 T20(과 기존 통합 테스트)만 해당하고 나머지는 Docker 없이 돈다 |
| `No qualifying bean of type 'io.micrometer.core.instrument.MeterRegistry'` | 액추에이터의 메트릭 자동 구성이 안 올라온 것. `spring-boot-starter-actuator`가 있는지 확인하고, 그래도 없으면 `TmdbConfig`에 `@Bean MeterRegistry`를 두는 대신 `ObjectProvider<MeterRegistry>`로 받는 방식을 협의한다(설계 변경이라 보고 후 결정) |
| `Failed to bind properties under 'app.tmdb'` | `application.yml`의 키 철자(`rate-limit`, `refill-tokens`, `max-retry-after` 등)와 `TmdbProperties` 필드명(카멜 케이스)이 일치하는지, `Duration` 값 형식(`1s`, `500ms`) 확인 |
| `Parameter 0 of method ... required a bean of type 'TmdbRateLimiter'`가 **다른 Feign 클라이언트**에서 발생 | `TmdbFeignConfig`가 `@Configuration`으로 스캔돼 퍼진 것. `@Configuration`/`@Component`를 붙이지 않았는지 확인(함정 3) |
| `getBeansOfType(RequestInterceptor.class)`가 비어 있지 않음 | 다른 라이브러리가 앱 전체에 인터셉터를 등록한 것일 수 있다. 어떤 빈인지 이름을 확인해 TMDB 것이 아니면 단언을 `TmdbFeignConfig` 소속 빈이 없다는 형태로 좁힌다 |

### 5.8 샘플 JSON 확보 절차 (실제 호출은 사용자가 직접)

Claude는 TMDB를 호출하지 않는다. 사용자가 로컬에서 **자기 토큰으로** 응답을 받아, **저장소 밖**에서 줄인 뒤 `src/test/resources/tmdb/`에 넣는다.

```powershell
# 1) 토큰은 입력 화면에서 가려진다(PowerShell 7.1 이상의 -MaskInput). 명령 기록·파일에 남기지 않는다
$token = Read-Host "TMDB 읽기 액세스 토큰" -MaskInput
$headers = @{ Authorization = "Bearer $token"; accept = "application/json" }

# 2) 원본은 저장소 밖 임시 폴더에 받는다 (예: 영화 상세)
New-Item -ItemType Directory -Force "$env:TEMP\tmdb-raw" | Out-Null
Invoke-RestMethod -Uri "https://api.themoviedb.org/3/movie/550?language=ko-KR&append_to_response=credits" `
  -Headers $headers | ConvertTo-Json -Depth 20 | Set-Content "$env:TEMP\tmdb-raw\movie-detail.json" -Encoding utf8

# 3) 끝나면 토큰 변수를 지운다
Remove-Variable token, headers
```

2단계의 URL을 4절 표 경로별로 바꿔 가며 받는다. 받은 뒤 아래 순서로 줄인다.

1. 에디터에서 **필요한 필드만 남긴다**(4절 표의 "응답에서 쓰는 필드"). 배열은 2~3개만 남긴다.
2. D6에 따라 **제목·줄거리·출연자 이름은 가짜 값으로 바꾼다**(예: `"테스트 영화"`, `"테스트 배우 1"`). 구조·필드 이름·`null`/`""` 같은 값의 모양은 실제와 같게 둔다. 제공처 응답은 `provider_id`는 실제 ID(8, 337, 97, 356, 1883, 350, 1881 등, Task 004 매핑표)를 쓰되 KR만 남기고 다른 나라는 1개만 둔다.
3. **필요한 변형을 직접 만든다**(실제 응답에서 찾기 어려운 경우를 손으로 편집. 5.2절의 샘플 7개에는 `""` 날짜, `null` 포스터, 시즌 0, `runtime: null` 회차, `buy` 없음이 이미 들어 있다. 아래는 추가로 필요할 때의 목록이다): 영화 제공처에서 `flatrate`를 지워 **대여·구매만** 있는 파일, `KR` 키를 지워 **KR 없음** 파일, `runtime: null` 영화, 날짜가 `""`인 드라마, `episodes[].runtime`이 null인 회차, 시즌 0이 포함된 `seasons`.
4. 저장 형식은 **BOM 없는 UTF-8**이다(PowerShell 5.1의 `-Encoding utf8`은 BOM을 붙인다). 에디터(IntelliJ/VS Code)에서 열어 인코딩을 확인한다.
5. **토큰·민감 값이 없는지 검사한다** (아무것도 안 나와야 정상):

```powershell
Select-String -Path C:\Users\swmoo\workspace\ottnavi-backend\backend\src\test\resources\tmdb\*.json -Pattern "eyJ|Bearer|api_key|Authorization"
```

   TMDB 읽기 토큰은 JWT 형식이라 `eyJ`로 시작한다. 응답 본문에는 토큰이 들어 있지 않지만, 실수로 요청 정보를 파일에 붙여 넣는 일을 막는 검사다.

6. 429 오류 본문 샘플은 TMDB 오류 본문 구조를 `c7`로 확인하지 못했으므로 **손으로 만든 간단한 JSON**(예: `{"status_code": 25, "status_message": "..."}`)을 쓰고, 테스트가 이 본문 형식에 의존하지 않게 한다. 본문 구조는 확인 필요(공식 문서 `developer.themoviedb.org/docs/errors`).

**TMDB 약관과 샘플 데이터 (확인 필요)**: 약관은 TMDB에서 받은 정보를 **6개월을 넘겨 캐시하는 것**을 금지한다(TASK003 1절, 프로젝트 문서). 공개 저장소에 실제 응답을 그대로 커밋하면 이 조항에 걸릴 수 있는지, 테스트 픽스처에도 적용되는지는 약관 원문을 보지 않았다. 안전하게 **D6 확정안(가짜 값으로 바꾼 합성 데이터)** 을 쓰고, 실제 응답을 쓰려면 약관 원문(themoviedb.org/api-terms-of-use)을 먼저 확인한다.

### 5.9 실제 TMDB를 부르지 않았다는 확인

1. `TMDB_API_KEY`를 **지운 셸**에서 테스트를 돌린다. 실제 호출이 있었다면 토큰이 없어 401이 난다. 테스트가 통과하면 실제 호출이 없다는 뜻이다.

```powershell
Remove-Item Env:TMDB_API_KEY -ErrorAction SilentlyContinue    # 이 셸에서만 지운다
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.*"
```

2. 테스트 소스에 실제 주소가 없는지 확인한다(아무것도 안 나와야 정상. `src/main/resources/application.yml`의 기본 base-url은 대상이 아니다).

```powershell
Get-ChildItem -Recurse -File C:\Users\swmoo\workspace\ottnavi-backend\backend\src\test | Select-String -Pattern "api.themoviedb.org"
```

3. 테스트 프로퍼티가 `app.tmdb.base-url`을 WireMock 주소로 바꾸는 것을 `TmdbClientTest`의 `base_url이_WireMock_주소로_바뀌어_있다`가 한 번 단언한다(`baseUrl`이 `http://localhost`로 시작). 통합 테스트(T20)도 같은 방식으로 base-url만 돌린다.

## 6. 알려진 함정과 확인 필요 사항

| # | 함정 | 대응 |
|---|---|---|
| 1 | **Jackson 3은 모르는 JSON 필드에서 실패한다.** Boot 4.1은 `FAIL_ON_UNKNOWN_PROPERTIES`를 끄지 않는다(`spring.jackson.use-jackson2-defaults=true`일 때만 끔, `c7`). TMDB 응답에는 DTO가 모르는 필드가 많다 | 모든 TMDB DTO에 `@JsonIgnoreProperties(ignoreUnknown = true)`(어노테이션 패키지 `com.fasterxml.jackson.annotation`). `use-jackson2-defaults`로 앱 전체를 바꾸지 않는다(다른 곳 직렬화가 달라진다). T3이 지킨다 |
| 2 | 날짜가 **`""`** 로 올 수 있다(`c7` 예시의 `first_air_date`). `LocalDate`로 받으면 실패할 수 있다(Jackson 3 기본 동작 확인 못 함) | 날짜 필드는 `String`으로 받고 변환은 소비 Task에서. `runtime`·`episode_count` 등 숫자는 `Integer`(null 가능) |
| 3 | **Feign 설정 클래스에 `@Configuration`을 붙이면** 컴포넌트 스캔에 잡혀 **모든 Feign 클라이언트에 적용**된다(`c7` 경고). 여기서 등록한 인증 인터셉터가 나중에 추가될 다른 Feign 클라이언트(메일 API 등)에 TMDB 토큰을 실어 보낼 수 있다 | `TmdbFeignConfig`에 `@Configuration`을 **붙이지 않고** `@FeignClient(configuration = TmdbFeignConfig.class)`로만 연결한다. 인증·버킷 인터셉터를 일반 `@Component`/`@Bean`으로 두지 않는다. 확인: 설정 클래스가 일반 클래스인데도 `@Bean` 메서드가 적용되는지 테스트 T1(헤더)로 확인 |
| 4 | **application.yml의 주석이 "Task 042의 @FeignClient"** 라고 되어 있다 | ROADMAP 번호는 041이다(8절 2번, 확정). 이 Task에서 **코드 주석 2곳(`application.yml` 27행 부근, `FeignConfig.java`)만** 고치고, `TASK024_GUIDE`·ROADMAP의 024 기록은 과거 기록이라 그대로 둔다. `name = "tmdb"`가 yml의 `config.tmdb`와 **일치해야** 타임아웃이 적용되는 것은 그대로다 |
| 5 | **운영(Cloudtype)에는 `TMDB_API_KEY`가 아직 없다.** `PHASE1_HUMAN_TASKS`는 첫 배포 기동에 필요 없는 값(`TMDB_API_KEY` 포함)을 Task 092에서 채워도 된다고 적었다. `api-token: ${TMDB_API_KEY}`처럼 **기본값 없이** 쓰면 값이 없을 때 **기동이 실패**한다(placeholder 해석 오류) | `${TMDB_API_KEY:}`로 기본값을 비워 두고, 기동 단계에서 토큰이 비었다고 실패시키지 않는다. 호출 시점에 401로 드러난다(T18). 다음 배포(develop→main) 때 이 PR이 포함되므로 확인한다 |
| 6 | **재시도도 버킷 토큰을 쓰는가**: 인터셉터는 시도마다 실행된다(3.2절 5번, 소스 확인) | 의도한 동작으로 둔다. 재시도도 TMDB에 가는 실제 요청이기 때문이다. 버킷 대기 시간은 시도마다 더해질 수 있으므로 5.5절 최악 시간 계산에 포함해서 본다 |
| 7 | **타임아웃도 `Retryer`를 거친다**(3.2절 3번). `readTimeout: 5000`이 호출 한 번의 총 제한 시간이 아니다 | `io-max-attempts`를 작게(D2 확정: 2). T9로 횟수를 검증한다 |
| 8 | `BusinessException`의 메시지는 오류 응답의 `detail`로 나갈 수 있다(`GlobalExceptionHandler.handleBusinessException`) | `TmdbException` 메시지에는 URL·본문·토큰을 넣지 않고 고정 문구만 쓴다. 오류 코드는 `EXTERNAL_API_ERROR`(502)이며 `RATE_LIMIT_EXCEEDED`(429)를 쓰지 않는다 — 후자는 **우리 서비스의 공개 API 요청 제한**(error-codes.md)이라 TMDB의 429와 다른 개념이다 |
| 9 | `Request.toString()`·`FeignException`에 실린 `Request`는 헤더(토큰)를 포함한다(소스 확인) | 로그에 `e.request()`·`request` 객체를 찍지 않는다. 스택 트레이스의 `FeignException` 메시지에는 URL과 응답 본문이 들어가지만 요청 헤더는 없다 |
| 10 | **WireMock Jetty 이슈**(TECH 6절). `wiremock-standalone` 3.13.2는 처음 실제로 띄우는 것이다 | 5.0단계에서 기동 확인. 해결 방법·결과를 TECH 6절 해당 행에 **실측**으로 갱신한다(PR에 문서 변경 포함, 무엇을 왜 바꿨는지 PR 설명에 적는다) |
| 11 | Windows에서 WireMock `Fault.CONNECTION_RESET_BY_PEER`는 멈출 수 있다(`c7`) | 지연 응답(`withFixedDelay`)으로만 타임아웃을 시험한다 |
| 12 | 응답 DTO를 엔티티·`api` 응답에 그대로 쓰고 싶어진다 | `backend.md`: DTO는 `infra/tmdb` 안에만. 소비자는 받자마자 필요한 값을 꺼내 도메인 모델로 바꾼다. `api` 계층 응답에 TMDB DTO를 노출하지 않는다 |
| 13 | **Retry-After를 TMDB가 429에 실어 보내는지 모른다**(확인 필요) | 헤더가 없을 때 지수 백오프만으로 정상 동작해야 한다(T4). 헤더가 있을 때의 동작은 T5·T7 |
| 14 | **페이지 상한**(discover는 정해진 쪽수까지만, 500쪽으로 알려져 있음)을 `c7`로 확인하지 못했다 | 이 Task의 범위가 아니다. 043이 `total_pages` 외에 상한도 보고 멈추게 하고, 필요하면 필터를 쪼갠다. 공식 문서 확인 필요 |
| 15 | 가벼운 컨텍스트 구성이 실제로 뜨는지 확인하지 못했다 | 5.2절 `TmdbTestSupport`. 안 되면 `IntegrationTestSupport` 상속으로 바꿔도 된다(사용자 허용, Docker를 켠다). 그때는 `TmdbTestSupport`의 `@SpringBootTest(classes = ...)` 대신 `IntegrationTestSupport`를 상속하고 `TestApp`을 지운다 |
| 16 | 검색 호출의 `query`는 사용자 입력이다 | 로그(DEBUG)에 나오므로 운영 DEBUG 금지. 지표 태그에 넣지 않는다. 길이·내용 검증은 050(API 계층)이 한다 |

## 7. 확정된 결정

사용자가 2026-10-11에 아래와 같이 확정했다. 더 정할 안건은 없다. 용어 풀이: **버킷**은 호출 횟수를 세는 "토큰 통"(호출마다 토큰 1개를 쓰고 일정 속도로 다시 채워진다), **백오프**는 실패 후 기다리는 시간을 점점 늘리는 방식, **chunk**는 배치가 한 번에(한 트랜잭션으로) 묶어 처리하는 작업 묶음이다.

| # | 주제 | 확정 내용 | 이유 한 줄 |
|---|---|---|---|
| D1 | 버킷이 비었을 때 | **최대 2초 기다린 뒤 포기**(선택지 B). 통 크기 `capacity` **10**, 채우는 속도 **초당 25**, 최대 대기 `max-wait` **2초** | 어떤 1초 구간에서도 최대 10 + 25 = 35회라 TMDB 한도(약 40회) 아래다. 평소엔 기다려서 속도가 맞고, 장애 때는 2초 뒤 포기해 배치가 영원히 멈추지 않는다 |
| D2 | 재시도 | 429·5xx는 **총 3회**, 타임아웃·연결 오류는 **총 2회**. 대기 0.5초 → 1초(두 배씩, 상한 4초, ±20% 흔들림). `Retry-After`가 있으면 우선하되 **5초를 넘게 기다리라고 하면 포기** | 오래 재시도할수록 배치가 DB 연결(풀 5개)을 쥐는 시간이 길어진다. 타임아웃을 3번 하면 최악 16.5초, 2번이면 10.5초다 |
| D3 | 404 | **`TmdbNotFoundException`을 던진다**(선택지 A). 아래 "404가 어떤 상황인가" 참고 | 없는 작품을 "KR 제공처 없음(모름)"으로 잘못 판정하는 오판정을 막는다 |
| D4 | 테스트 | **가벼운 테스트 위주**(DB·Redis 없이 WireMock + 필요한 설정만 올림) **+ Testcontainers 통합 테스트 1건**(`IntegrationTestSupport` 기반). 사용자가 Docker Desktop을 켜 준다 | 대부분은 Docker 없이 수 초에 돈다. 실제 앱 컨텍스트에서 조립이 되는지만 통합 1건으로 확인한다. 어떤 1건인지는 아래에 적었다 |
| D5 | 소비자가 쓰는 것 | **얇은 래퍼 `TmdbClient`**를 쓴다(선택지 B). Feign 인터페이스는 `TmdbApi`로 감춘다 | 예외 변환·`tmdb.call` 지표·고정 쿼리 값(`KR`, `ko-KR`)이 한 곳에 모이고, 소비자는 Feign을 몰라도 된다 |
| D6 | 샘플 JSON | **구조만 실제와 같고 값은 가짜**(선택지 A) | 공개 저장소에 올라가는 파일이라 TMDB 약관(6개월 캐시 금지) 위험을 피한다 |
| D7 | `TMDB_API_KEY` | **읽기 액세스 토큰(v4 Bearer)** 확인 완료. `.env.example` 이름은 그대로 | v3 키를 넣으면 Bearer로 401이 난다. Task 004에서 읽기 토큰으로 호출해 성공한 기록과 같다 |

### 7.1 D3 풀이: "404 처리"가 어떤 상황인가

**상황.** TMDB에서 작품 하나를 상세 조회할 때(`getMovie`, `getTv`, `getTvSeason` 등) 그 ID가 **TMDB에서 지워졌거나, 애초에 잘못된 ID**이면 TMDB가 404를 돌려준다. 예를 들어 어제 후보 수집 때 있던 작품이 오늘 TMDB에서 삭제되면, 오늘 상세 수집에서 404를 받는다. "KR에서 볼 수 있는 곳이 없다"는 **정상 응답 안의 빈 데이터**(200)이고, 404는 "그런 작품 자체가 없다"는 뜻이라 서로 다른 일이다.

**이 Task가 하는 일.** 404는 재시도하지 않고(몇 번을 해도 같다), 바로 `TmdbNotFoundException`을 던진다. 호출한 쪽은 `null`이 아니라 예외를 받으므로 처리를 빠뜨릴 수 없다.

**호출한 쪽(수집 배치, Task 044 등)이 하는 일.** 그 예외를 받아 **해당 작품만 건너뛰고 다음 작품으로 계속**한다. Job 전체를 실패시키지 않는다. 소비 쪽 스케치:

```java
try {
    TmdbMovieDetail detail = tmdbClient.getMovie(tmdbId, "ko-KR", "credits");
    // ... 정상 처리
} catch (TmdbNotFoundException e) {
    log.warn("TMDB에 없는 작품이라 건너뛴다: tmdbId={}", tmdbId);   // 이 작품만 건너뛰고 루프는 계속한다
}
```

다른 예외는 다르게 다룬다: `TmdbUnavailableException`(일시 장애)은 실패 건수로 세고, `TmdbRequestException`(토큰·요청 문제)은 모든 호출이 실패할 것이므로 Job을 멈추고 알린다. 이 분기를 만드는 일은 소비 Task의 몫이고, 이 Task는 예외 종류를 구별해 던지는 데까지다.

### 7.2 D4 풀이: 통합 테스트 1건은 무엇으로 하나

**통합으로 둘 한 건(이 안내서의 추천): `TmdbClientIntegrationTest extends IntegrationTestSupport`.** 앱 전체 컨텍스트를 띄우고(PostgreSQL·Redis는 Testcontainers) 다음을 한 번에 확인한다.

1. **`TMDB_API_KEY` 없이도 기동된다**(6절 함정 5). 운영과 같은 조건이다.
2. `app.tmdb.*` 설정이 `TmdbProperties`에 바인딩되고, `TmdbApi`·`TmdbClient`·`TmdbRateLimiter` 빈이 실제 컨텍스트에 조립된다. 특히 `TmdbFeignConfig`가 컴포넌트 스캔에 잡혀 **다른 Feign 클라이언트에 퍼지지 않는지**(6절 함정 3)를 앱 전체에서 확인할 수 있다.
3. **대표 호출 1개**(`discoverMovies`)를 WireMock으로 보내 DTO까지 매핑된다. 실제 앱의 Jackson 3 설정(`JacksonAutoConfiguration`)으로 매핑되는지 확인하는 유일한 테스트다.

이유: 가벼운 테스트는 필요한 자동 구성만 골라 올리므로 "실제 앱에서도 같은 조립이 되는가"를 못 본다. 이 한 건이 그 틈을 메운다. 나머지(재시도 표 T4~T9, 오류 T11~T13 등)는 모두 가벼운 테스트로 둬서 Docker가 꺼져 있어도 돌게 한다. `OttnaviApplicationTests`처럼 이미 앱 전체를 띄우는 테스트가 있으므로, 이 통합 테스트는 컨텍스트를 새로 하나 더 띄우는 비용이 든다(`@DynamicPropertySource`로 base-url을 바꾸면 컨텍스트 캐시가 분리된다). 1건만 두는 이유다.

## 8. 문서 불일치 처리 결과

1. **"처음 본 제공처"를 어디서 얻는가 (확정).** 우리는 **7곳만 다룬다.** 그래서 전체 제공처 목록(`/watch/providers/movie|tv?watch_region=KR`)을 한 번 받아 오고, 그중 우리 7곳이 아닌 제공처는 **버리지 않고 `tmdb_provider`에 "미매핑" 상태로 저장**한다. 나중에 관리자가 화면에서 "관련 없음"으로 정리한다(프론트 관리자 화면에 미매핑/매핑됨/관련 없음 3상태가 이미 있다). 이유: 버리면 TMDB에 새 제공처가 생겨도 우리가 알 수 없고, 저장하면 관리자가 한 번 훑어보고 "관련 없음"으로 닫을 수 있다.
   - **이 Task(041)의 범위**: 전체 목록 호출 `listProviders`(4절 10번)를 포함해 DTO로 돌려주는 데까지.
   - **Task 043의 범위**: 받은 목록에서 7곳 매핑 여부를 가려 `tmdb_provider`에 `first_seen_at`과 함께 저장하는 일. ROADMAP Task 041·043 설명에 이 호출이 빠져 있었으므로, 이 결정을 PR 설명과 ROADMAP `기록:`에 남긴다.
2. **Task 번호 042 vs 041 (확정: 코드 주석 2곳만 고친다).** 무슨 일인가: TMDB 클라이언트는 ROADMAP에서 **Task 041**인데(042는 제공 상태 판정), Task 024 때 쓴 아래 글들이 "Task 042"라고 적고 있다.
   - `backend/src/main/resources/application.yml` 27행 부근: `tmdb: # Task 042의 @FeignClient(name = "tmdb")와 맞춘다`
   - `backend/src/main/java/com/ottnavi/global/config/FeignConfig.java` 10행: `// ErrorDecoder(429·5xx → 재시도 예외)와 Retryer(지수 백오프, Retry-After 우선)는 TMDB 클라이언트와 함께 Task 042에서 추가한다(TECH 3절)`
   - `docs/TASK024_GUIDE.md`(14·16·99·193·198·261줄)와 `docs/ROADMAP.md` Task 024 기록
   
   **Task 041에서는 위 코드 주석 2곳만 041로 고친다.** 이유: 코드 주석은 앞으로 코드를 읽는 사람이 그대로 믿으므로 틀리면 안 된다. 반면 `TASK024_GUIDE`와 ROADMAP의 024 기록은 **그 시점에 쓴 과거 기록**이라 그대로 둔다(고치면 기록의 날짜 의미가 흐려진다). `FeignConfig.java`의 주석은 번호만 고치는 것이 아니라, ErrorDecoder·Retryer를 이 Task에서 `TmdbFeignConfig`에 만든다는 사실도 함께 반영한다(공통 `FeignConfig`가 아니라 TMDB 전용 설정에 둔다, 6절 함정 3).
3. **041의 로컬 버킷 vs 049의 단건 수집 버킷 (사용자 확인함, 정할 것 없음).** 서로 다른 장치다. 2절 3번에서 구분했다.
4. **`TMDB_API_KEY`가 가리키는 값 (D7과 같음).** 읽기 액세스 토큰으로 확정했다.
5. **표기 차이 (사용자 확인함).** `backend.md`·PRD·TECH의 "초당 40회 미만/약 40회 아래"는 같은 뜻이다. 35회(10 + 25)를 안전 마진으로 쓴다(D1).
6. **TMDB 429와 `RATE_LIMIT_EXCEEDED` (확정: 구현 범위에 포함).** `error-codes.md`의 `RATE_LIMIT_EXCEEDED`(429)는 **우리 서비스의 공개 API 요청 제한**이다. TMDB가 우리에게 429를 준 경우는 사용자 잘못이 아니라 외부 연동 문제이므로, **재시도 후에도 실패하면 `EXTERNAL_API_ERROR`(502)로 응답한다.** 구현은 5.6절의 `TmdbUnavailableException`(`getErrorCode()` = `EXTERNAL_API_ERROR`)이고, 테스트 T6에서 코드와 상태(502)까지 단언한다. `ErrorCode`·`error-codes.md`는 바꾸지 않는다(이미 있는 코드를 쓴다).

## 9. 체크리스트와 마무리

### 9.1 단계별 체크리스트

- [x] 단계 0: `git branch --show-current`가 `feature/b3-tmdb-client`, WireMock 기동 확인 완료(2026-10-11, `WireMockSmokeTest` tests=1 failures=0)
- [ ] 작업 트리 정리(5.0.1): 컴파일되지 않는 `TmdbApi`·`TmdbClient`·`TmdbRateLimiter` 뼈대를 `package` 줄만 남기고 비운 뒤 ①을 시작
- [ ] ①: `TmdbProperties`(토큰 가리는 `toString()`)·`TmdbConfig`·`TmdbFeignConfig`(인증 인터셉터)·**예외 4종(`TmdbException`이 `BusinessException` 상속, `EXTERNAL_API_ERROR` 매핑; 404는 `TmdbNotFoundException`)**, 토큰 비어도 기동, 코드 주석 2곳 정정, T18·T21 통과
- [ ] ②: `TmdbApi` 10개 호출(전체 제공처 목록 `listProviders` 포함, 7곳 외도 거르지 않고 반환), `TmdbMediaType`, 얇은 `TmdbClient`, DTO 16개(`@JsonIgnoreProperties`, 날짜는 `String`), 샘플 JSON 7개, `TmdbTestSupport`, T1·T2·T3·T10·T19 통과
- [ ] ③: `TmdbRateLimiter`(싱글톤 빈 `tmdbBucket`), 재시도도 토큰 사용, `capacity + refill < 40` 계산 확인, T16 통과
- [ ] ④: `TmdbErrorDecoder` 등록, T22 통과
- [ ] ⑤: `TmdbRetryer` 등록(`clone()` 포함), T23·T4·T5·T14 통과, 최악 소요 시간 표와 설정값 일치
- [ ] ⑥: `TmdbClient` 완성판(`call()`, 예외 변환, `tmdb.call` 카운터 태그 닫힌 집합 10×8), TMDB 429 포기 → `EXTERNAL_API_ERROR`(502) 매핑, T6·T7·T8·T9·T11·T12·T13·T15·T17·T24 통과
- [ ] ⑦: T1~T19·T21~T24 가벼운 테스트 전부 통과(Docker 없이), T20 통합 테스트 1건 통과(Docker Desktop 켠 상태), `WireMockSmokeTest` 삭제
- [ ] 코드 주석 2곳 정정: `application.yml` 27행 부근과 `FeignConfig.java`의 "Task 042"를 041로(`TASK024_GUIDE`·ROADMAP 024 기록은 고치지 않는다)
- [ ] 샘플 JSON 확인: `Select-String` 검사에서 토큰 패턴 없음, BOM 없음
- [ ] `docs/TECH.md` 6절의 WireMock Jetty 행을 실측 결과로 갱신(무엇을 왜 바꿨는지 PR 설명에 적음)

### 9.2 PR 전 확인

```powershell
cd C:\Users\swmoo\workspace\ottnavi-backend\backend
Remove-Item Env:TMDB_API_KEY -ErrorAction SilentlyContinue
.\gradlew.bat test --tests "com.ottnavi.infra.tmdb.*"   # 이 Task의 테스트 (토큰 없이 통과 = 실제 호출 없음)
# 통합 테스트 1건(T20)도 위 명령에 포함되므로 처음부터 Docker Desktop을 켜 둔다
.\gradlew.bat clean build                               # 전체 (기존 테스트 전부 포함, Testcontainers 테스트 포함)
cd ..
git status --short                                      # 의도하지 않은 파일(.env, 임시 JSON)이 없는지
git diff --stat
Get-ChildItem -Recurse -File backend\src | Select-String -Pattern "TMDB_API_KEY\s*="   # 토큰 값이 코드·설정에 직접 박혀 있지 않은지
```

- `.env`를 포함해 어떤 비밀 값도 커밋되지 않았는지 `git status`로 한 번 더 본다.
- 계약(`openapi.yaml`)·`error-codes.md`·`ErrorCode`·`frontend/`는 이 Task에서 바뀌지 않아야 정상이다. 바뀌었다면 이유를 PR 설명에 적는다.
- 커밋 예: `feat(backend): TMDB Feign 클라이언트와 호출 제한·재시도·지표 구현 (Task 041)` — 커밋·푸시·PR은 사용자가 요청할 때만 진행한다.
- `gh pr create --base develop`. 병합 후 ROADMAP Task 041에 `기록:`(확정 결정 D1~D7과 문서 불일치 처리 결과, WireMock 기동 결과, 테스트 건수, 확인 필요 항목의 결과)을 남기고 ⬜를 ✅로 바꾼다. CodeRabbit 지적은 `coderabbit-triage`로 정리한다.

### 9.3 이후 Task에 넘길 인터페이스 요약

| 받는 Task | 쓰는 것 | 알아야 할 점 |
|---|---|---|
| 042 (판정·러닝타임) | `getMovieWatchProviders`, `getTvSeasonWatchProviders`, `getMovie`·`getTv`·`getTvSeason`의 DTO | DTO는 `TmdbClient`가 돌려주는 `infra.tmdb.dto.*`. 받자마자 도메인 값으로 변환한다. 날짜는 `String`, null·`""` 처리 필요. KR 키가 맵에 없으면 `NO_KR_DATA` 판단은 042 몫 |
| 043 (후보 수집) | `discoverMovies`, `discoverTvs`, `listProviders` | `listProviders`는 전체 제공처를 거르지 않고 돌려준다. 우리 7곳이 아닌 제공처를 `tmdb_provider`에 "미매핑"으로 저장하는 일은 043이 한다(관리자가 나중에 "관련 없음"으로 정리). `TmdbNotFoundException`은 그 작품만 건너뛴다. `total_pages`·페이지 상한은 043이 처리. chunk를 작게(T-2). 예외는 `TmdbUnavailableException`(일시)·`TmdbRequestException`(설정)으로 구분 |
| 044 (상세·시즌) | `getMovie`, `getTv`, `getTvSeason` + 시즌 제공처 | 호출량 지표는 `tmdb.call{endpoint=...}`로 R11-26에 쓴다. `append_to_response`로 호출 수를 줄일 방법은 044가 공식 문서로 확인 |
| 049·050 (요청 제한·검색) | `searchMovies`, `searchTvs` | 050은 `TmdbUnavailableException`·`TmdbRequestException`을 잡아 DB 결과만 반환(FR-01). 로컬 버킷 대기가 최대 `max-wait`이므로 사용자 요청이 그만큼 느려질 수 있다. 049의 IP별 버킷이 먼저 걸린다 |
| 048 (첫 전체 수집) | `app.tmdb.rate-limit.*`·`retry.*` 값, `tmdb.call` 지표 | 값은 실측 후 조정(설정 키) |

## 10. 확인하지 못한 것

- Feign 기본 디코더가 Jackson 3으로 실제 매핑하는지, 5.2절 가벼운 컨텍스트 구성(`TmdbTestSupport.TestApp`)이 실제로 뜨는지(실행 안 함 → ②의 T1에서 확인). `wiremock-standalone` 3.13.2 기동은 2026-10-11 5.0단계에서 확인됐다.
- **5절 코드 전체의 컴파일·실행**: 저장소에 만들지 않았다. 소스 jar로 시그니처를 대조했으나 아래는 컴파일 시/실행 시 확인이 필요하다.
  - 컴파일 시: `ClassPathResource#getContentAsString(Charset)`(Spring 7), `Response.Builder#body(String, Charset)`, AssertJ `extracting("프로퍼티명")`이 record 접근자에 동작하는지, `TmdbRetryer#clone()` 선언의 경고, Lombok `@RequiredArgsConstructor`가 만든 `TmdbClient(TmdbApi, MeterRegistry)` 생성자 순서.
  - 실행 시: Jackson 3의 `null → 원시형 필드` 기본 동작, 제네릭 record `TmdbPage<T>` 매핑, `@Bean Retryer`가 Feign 기본 `NEVER_RETRY`를 대체하는지, `ErrorDecoder.Default`가 `RetryableException`의 cause에 원래 `FeignException`을 넣는지(4xx + Retry-After 풀어 주기), `@DynamicPropertySource`와 공유 WireMock 서버의 조합, `OutputCaptureExtension`으로 Feign DEBUG 로그 캡처(T15), 파이프(`8|337`) 쿼리 값 처리, JDK `HttpURLConnection`의 자체 재시도 여부(T9), 앱 전체 컨텍스트의 `MeterRegistry` 빈 주입(T20), `getBeansOfType(RequestInterceptor.class)`가 앱 전체에서 비어 있는지(T20).
  - `TmdbNotFoundException`의 `ErrorCode`를 `EXTERNAL_API_ERROR`(502)로 정한 것은 이 개정의 판단이다(5.1절 근거). 다르게 하려면 사용자 결정이 필요하다.
  - `tmdb.call`의 `result` 태그에 `decode_error`를 추가한 것(7→8개)도 이 개정의 판단이다.
- TMDB가 429에 `Retry-After`를 보내는지, 오류 본문 구조, discover 페이지 상한, 파이프(`|`) OR 파라미터 동작, 응답에 `ads`·`free`가 오는지, 시즌 제공처에 `rent`·`buy`가 오는지, 시즌 상세 응답의 정수 `id` 존재, `append_to_response`로 시즌 상세를 붙일 수 있는지, TV `credits` vs `aggregate_credits` 선택, 한국어 줄거리가 `""`인지 `null`인지, 한국어 제목 대체 동작(공식 문서 `developer.themoviedb.org` 직접 확인 또는 사용자가 로컬에서 1회 호출).
- 샘플 JSON을 공개 저장소에 두는 것의 TMDB 약관상 허용 범위(약관 원문 미확인).
- `@JsonNaming`의 Jackson 3 패키지 위치(권하지 않는 방식이라 확인하지 않음).
- Feign 자체 관측값(`MicrometerObservationCapability`)의 이름·태그와 `tmdb.call`의 충돌 여부.
