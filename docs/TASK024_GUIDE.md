# Task 024 작업 안내서 (Redis·캐시·Feign 공통 설정)

> 2026-10-08 작성. 기준 문서는 `docs/ROADMAP.md` Task 024, TECH 1절(`planCalc` 캐시)·3절(TMDB Feign)·6절(Redis 직렬화·Jackson 3 함정)·T-2·T-3, `.claude/rules/backend.md`.
> 근거 수준: context7 문서(이하 `c7`) — Spring Boot 4.1.0(`/spring-projects/spring-boot/v4.1.0`), Spring Data Redis 4.x 업그레이드 안내(`/spring-projects/spring-data-redis`), Spring Cloud OpenFeign(`/spring-cloud/spring-cloud-openfeign`). 확인하지 못한 것은 "확인 필요"로 표시했고 대부분 이 Task의 테스트로 확인한다.
> 이 Task는 사용자가 직접 작성한다. Claude(또는 `backend-dev` 에이전트)는 검수를 돕는다.

## 1. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 목적 | 캐시(Redis)와 외부 HTTP 클라이언트(OpenFeign)의 **공통 설정을 한 번만** 만들어 이후 Task가 설정 고민 없이 쓰게 한다 |
| 태그·선행 | [B] · 선행 Task 016(완료) · Phase 2 |
| 브랜치 | `feature/b0-redis-feign-config` (`develop`에서) |
| 시기 | MVP 1주차(10-08~10-14). 이 설정을 처음 쓰는 Task는 042(TMDB 클라이언트)·049(공개 조회 캐시)·062(계산 캐시)라 2주차 전에 끝나면 된다 |
| 만드는 것 | `global/config/RedisConfig`, `global/config/FeignConfig`, `application.yml` 설정, 캐시 통합 테스트 1개 |
| 만들지 않는 것 | TMDB `@FeignClient` 인터페이스·DTO·`ErrorDecoder`·`Retryer`(Task 042), 각 캐시의 `@Cacheable` 적용(Task 049·062 등), 요청 제한(Bucket4j, Task 049) |

| 완료 기준 (ROADMAP) | 확인 방법 |
|---|---|
| V-B: Testcontainers Redis에 캐시를 저장·조회하고 값이 JSON인지 확인하는 테스트가 통과한다 | `.\gradlew.bat test --tests "com.ottnavi.global.config.*"`, 마지막에 `.\gradlew.bat clean build` |
| (공통) `develop` 대상 PR에서 `backend-ci`·`frontend-ci` 통과 | PR 화면 |

## 2. 왜 하는가

1. **Redis 캐시 기본 직렬화가 JDK 직렬화다**(TECH 6절, `c7`). 그대로 쓰면 ① 값이 사람이 못 읽는 바이너리라 `redis-cli`로 확인할 수 없고 ② 클래스가 `Serializable`이어야 하며 ③ 클래스 구조가 바뀌면 역직렬화가 깨진다. JSON 직렬화로 바꾼다.
2. **Spring Boot 4 = Spring Data Redis 4 = Jackson 3**이라 예전 자료의 클래스가 바뀌었다(`c7`, 아래 3.1). 각 기능 Task에서 따로 찾다 보면 Jackson 2 클래스(지원 중단 예정)를 섞어 쓰기 쉽다.
3. **캐시 이름과 TTL을 한곳에 모은다.** `titleDetail`(작품 상세, Task 050·051), `planCalc`(같은 입력의 계산 결과, TECH 1절 `input_hash`), `exclusiveTitles`(독점 목록, 2단계). Redis Cloud 무료는 **30MB**라(Task 003) TTL 없이 쌓이면 금방 찬다.
4. **외부 호출 타임아웃은 필수다.** TMDB 응답이 늦어지면 수집 배치의 DB 연결(풀 약 5개)이 묶여 고갈된다(TECH T-2: 수집 배치만 트랜잭션 안 외부 호출을 허용하는 대신 "짧은 Feign 타임아웃"을 조건으로 걸었다). 기본값에 맡기지 않고 클라이언트별로 정한다.

## 3. 사전 조사 결과 (c7 확인)

### 3.1 Jackson 3용 Redis 직렬화기 (Spring Data Redis 4.0 업그레이드 안내)

| 용도 | Jackson 2 (지원 중단 예정, 쓰지 않음) | **Jackson 3 (사용)** |
|---|---|---|
| 범용 JSON 직렬화기 | `GenericJackson2JsonRedisSerializer` | **`GenericJacksonJsonRedisSerializer`** |
| 타입 지정 JSON 직렬화기 | `Jackson2JsonRedisSerializer` | **`JacksonJsonRedisSerializer<T>`** |

- **함정**: 예전 `GenericJackson2...`는 기본으로 타입 정보(`@class`)를 JSON에 넣었지만, **`GenericJacksonJsonRedisSerializer`는 기본으로 넣지 않는다**(`c7` 원문: "does not enable default typing by default"). 타입 정보가 없으면 캐시에서 꺼낸 값이 원래 record가 아니라 `LinkedHashMap`이 되어 `ClassCastException`이 난다. 넣으려면 빌더의 `enableDefaultTyping(…)`을 쓴다.
- 추가 함정(확인 필요): Jackson의 기본 타이핑 중 흔히 쓰는 `NON_FINAL`은 final 클래스에 타입 정보를 넣지 않는데, **Java record는 final**이다. 이 프로젝트의 DTO는 record이므로 기본 타이핑 방식으로는 여전히 `LinkedHashMap`이 될 수 있다. 그래서 4절 ③의 테스트에서 **record를 넣고 같은 타입으로 꺼내지는지**를 반드시 확인한다.

### 3.2 캐시 자동 구성 (Spring Boot 4.1.0 Caching 문서)

- Redis가 있으면 `RedisCacheManager`가 자동 구성된다. `spring.cache.cache-names`·`spring.cache.redis.time-to-live`로 기본값을 줄 수 있고, 전체 기본 설정은 `RedisCacheConfiguration` 빈, 캐시별 설정은 `RedisCacheManagerBuilderCustomizer` 빈으로 바꾼다.
- 키 접두사(캐시 이름)는 기본으로 붙고 켜 두는 것을 권장한다(캐시끼리 키가 겹치지 않게).
- `@Cacheable`을 쓰려면 `@EnableCaching`이 필요하다.
- **확인 필요**: Boot 4는 모듈이 잘게 나뉘어서, 지금 의존성(`spring-boot-starter-data-redis`)만으로 캐시 자동 구성이 들어오는지 확인하지 못했다. 문서는 `spring-boot-starter-cache`를 "기본 캐시 의존성" 스타터로 소개한다. 4절 ①에서 확인한다.

### 3.3 Feign 클라이언트별 타임아웃 (Spring Cloud OpenFeign 문서)

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          tmdb:                 # @FeignClient의 name(=value, contextId)과 같아야 한다
            connectTimeout: 3000
            readTimeout: 5000
            loggerLevel: basic
```

- `default`라는 이름으로 모든 클라이언트의 기본값을 줄 수 있다.
- `@Configuration` 빈과 설정 속성이 둘 다 있으면 **설정 속성이 이긴다**(`spring.cloud.openfeign.client.default-to-properties=false`로 바꿀 수 있다). 그래서 타임아웃은 yml에만 두고 자바 설정에 중복으로 두지 않는다.

## 4. 단계 (단계마다 멈춰서 확인하고 넘어간다)

### ① 브랜치와 의존성 확인

```powershell
git switch develop; git pull
git switch -c feature/b0-redis-feign-config
cd backend
.\gradlew.bat dependencies --configuration runtimeClasspath | Select-String "spring-boot-cache|spring-context-support|spring-boot-data-redis"
```

- `spring-boot-cache`(캐시 자동 구성 모듈)가 보이지 않으면 `build.gradle`의 Redis 줄 아래에 추가한다.

```groovy
	implementation 'org.springframework.boot:spring-boot-starter-cache'   // 캐시 자동 구성(Boot 4는 모듈 분리)
```

- 확인: 의존성 변경이 없어도 이 단계에서 `.\gradlew.bat compileJava`가 통과해야 한다.

### ② 설정(yml)

`application.yml`(공통)에 추가한다. 값은 **추천안(미확정)** 이며 사용자가 정한다(5절 결정 D3·D4).

```yaml
spring:
  data:
    redis:
      timeout: 2s                 # 명령 응답 대기. Redis Cloud 첫 호출이 11.5초 걸린 관측이 있어(Task 020) 운영 값은 측정 후 조정
      connect-timeout: 5s         # 연결 대기
  cloud:
    openfeign:
      client:
        config:
          tmdb:                   # Task 042의 @FeignClient(name = "tmdb")와 맞춘다
            connectTimeout: 3000  # ms. 연결이 안 되면 빨리 실패해 DB 연결을 붙잡지 않는다(TECH T-2)
            readTimeout: 5000     # ms
            loggerLevel: basic    # 요청 메서드·URL·상태·소요 시간만. 본문·헤더(API 토큰)는 남기지 않는다
```

- 캐시 이름과 TTL은 yml이 아니라 ③의 `RedisConfig`에 둔다(캐시마다 TTL이 달라 자바 쪽이 읽기 쉽다). yml로 두고 싶다면 `app.cache.ttl.*` 같은 자체 속성을 만들어도 된다(D4).
- 확인 필요: `spring.data.redis.timeout`·`connect-timeout` 키 이름은 Boot 4.1 문서로 대조하지 않았다. 기동 로그에 "unknown property" 경고는 나오지 않으므로, 설정 메타데이터(IDE 자동 완성)로 확인한다.

### ③ `global/config/RedisConfig`

```java
package com.ottnavi.global.config;

/** Redis 캐시 설정: 값은 JSON(Jackson 3)으로 저장하고 캐시마다 TTL을 둔다. */
@Configuration
@EnableCaching
public class RedisConfig {

    // 캐시 이름 (@Cacheable(cacheNames = ...)에서 이 상수를 쓴다)
    public static final String TITLE_DETAIL = "titleDetail";         // 작품 상세(Task 050·051)
    public static final String PLAN_CALC = "planCalc";               // 계산 결과, 키는 input_hash(TECH 1절)
    public static final String EXCLUSIVE_TITLES = "exclusiveTitles"; // 독점 목록(2단계)

    /** 모든 캐시의 기본 설정: 문자열 키, JSON 값, null 미저장, 기본 TTL. */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))   // 기본 TTL (D4 추천안)
                .disableCachingNullValues()      // 없는 값을 캐시하면 수집 직후에도 계속 "없음"이 나온다
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer()));
    }

    /** 캐시별 TTL. 기본 설정을 바탕으로 TTL만 바꾼다. */
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheTtlCustomizer(RedisCacheConfiguration base) {
        return builder -> builder
                .withCacheConfiguration(TITLE_DETAIL, base.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration(PLAN_CALC, base.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration(EXCLUSIVE_TITLES, base.entryTtl(Duration.ofHours(24)));
    }

    private RedisSerializer<Object> valueSerializer() {
        // D1 결정에 따라 아래 둘 중 하나 (5절)
    }
}
```

**값 직렬화기 선택(D1)** — 3.1의 함정 때문에 두 방법 중 하나를 고른다.

| 방법 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **A. 범용 + 타입 정보** | `GenericJacksonJsonRedisSerializer.builder().enableDefaultTyping(검증기)` — 검증기는 `com.ottnavi.`와 `java.util.` 아래 타입만 허용 | 설정 한 곳으로 모든 캐시에 적용 | JSON에 클래스 이름이 들어가 클래스 이동·이름 변경 시 기존 캐시가 깨진다(TTL이 지나면 사라지므로 큰 문제는 아님). 허용 목록을 잘못 열면 역직렬화 취약점이 된다. record(final)에 타입 정보가 실제로 붙는지 확인 필요 |
| **B. 캐시별 타입 지정** (추천) | 캐시마다 `JacksonJsonRedisSerializer<값 타입>`을 `withCacheConfiguration`에 지정 | 타입 정보 없이 깔끔한 JSON, 다형성 역직렬화 위험 없음, record 문제 없음 | 값 타입(응답 DTO)이 생기는 Task(049·062 등)에서 캐시마다 한 줄씩 추가해야 한다. Task 024 시점에는 DTO가 없어 테스트용 record로만 확인한다 |

- 추천은 **B**다. 이유: 캐시가 3개뿐이라 한 줄씩 추가하는 비용이 작고, 타이핑 관련 함정(record·보안)을 아예 피한다. Task 024에서는 기본 설정의 값 직렬화기를 `GenericJacksonJsonRedisSerializer`(타입 정보 없음)로 두고, 테스트에서 "B 방식으로 등록한 테스트 캐시가 record를 같은 타입으로 돌려준다"를 확인한다.
- 생성자·빌더의 정확한 시그니처(어떤 `JsonMapper`를 받는지)는 구현할 때 IDE와 `c7`로 확인한다. 앱의 `JsonMapper` 빈(Boot 자동 구성)을 주입해 같은 날짜·모듈 설정을 쓰는 것이 좋다.
- import 주의: Jackson 3은 `tools.jackson.*` 패키지다. `com.fasterxml.jackson.databind.*`가 자동 완성되면 Jackson 2(jjwt·springdoc이 끌고 온 것)이므로 쓰지 않는다(TECH 6절).

### ④ `global/config/FeignConfig`

```java
package com.ottnavi.global.config;

/** OpenFeign 활성화. 클라이언트별 타임아웃은 application.yml(spring.cloud.openfeign.client.config.*)에 둔다. */
@Configuration
@EnableFeignClients(basePackages = "com.ottnavi.infra")   // 외부 연동 클라이언트는 infra 아래에만 둔다(backend.md 패키지 구조)
public class FeignConfig {
    // ErrorDecoder(429·5xx → 재시도 예외)와 Retryer(지수 백오프, Retry-After 우선)는 TMDB 클라이언트와 함께 Task 042에서 추가한다(TECH 3절)
}
```

- 클라이언트가 하나도 없어도 `@EnableFeignClients`는 오류 없이 뜬다(확인 필요 — ⑤의 컨텍스트 로드로 확인).
- **"앱 직렬화는 Jackson 3만"**(ROADMAP): Spring Cloud OpenFeign의 기본 디코더는 Spring의 HTTP 메시지 컨버터를 쓰므로 Boot 4의 Jackson 3 컨버터를 탈 것으로 보지만 확인하지 못했다. Task 042의 WireMock 테스트에서 TMDB 응답이 record로 매핑되는지로 확인한다. Task 024에서는 Feign 전용 디코더·인코더를 따로 만들지 않는다.

### ⑤ 테스트 — `global/config/RedisCacheConfigTest`

`IntegrationTestSupport`를 상속한다(상속하지 않으면 local 프로필의 compose Redis에 붙어 CI에서 실패한다, TECH 6절).

| 케이스 | 확인 내용 |
|---|---|
| 저장·조회 | `CacheManager.getCache(...)`에 테스트용 record(예: `record SampleValue(Long id, String name, List<String> tags)`)를 넣고 꺼내면 **같은 record 타입·같은 값**이다 |
| 값이 JSON | `StringRedisTemplate`으로 Redis 원본 값을 읽으면 `{`로 시작하는 JSON이고 `"name"` 필드가 보인다(JDK 직렬화가 아님) |
| 키 접두사 | 원본 키가 `캐시이름::키` 형식이다(예: `titleDetail::1`) |
| TTL | `getExpire(key)`가 0보다 크고 설정한 TTL 이하다 |
| null 미저장 | `cache.put(key, null)` 시 예외가 나거나 저장되지 않는다(`disableCachingNullValues` 동작을 실제로 보고 기대값을 정한다) |

- 테스트 전용 캐시 설정이 필요하면 테스트 클래스 안의 중첩 `@TestConfiguration`으로 둔다(Task 023 경험: 중첩 클래스로 두면 다른 테스트의 스캔에 섞이지 않는다).
- 각 테스트가 끝나면 사용한 키를 지워 다른 테스트에 영향이 없게 한다.

```powershell
cd backend
.\gradlew.bat test --tests "com.ottnavi.global.config.*"
.\gradlew.bat clean build
```

### ⑥ 로컬 기동 확인 (V-API 수준)

```powershell
docker compose -f infra/docker-compose.yml up -d
cd backend; .\gradlew.bat bootRun
```

- 기동 로그에 캐시·Feign 관련 오류가 없고 `/actuator/health`가 200 `UP`(Redis 포함)인지 본다.

### ⑦ PR

- 커밋 예: `feat(backend): Redis 캐시 JSON 직렬화와 Feign 타임아웃 공통 설정 (Task 024)`
- `gh pr create --base develop`. CodeRabbit 지적은 `coderabbit-triage` 에이전트로 정리한다.
- 병합 후 ROADMAP Task 024에 `기록:`(D1~D4 결정, 테스트 결과, 확인 필요 항목의 결과)을 남긴다.

## 5. 사용자 결정 사항

| ID | 내용 | 추천안(미확정) | 근거 |
|---|---|---|---|
| D1 | 값 직렬화 방식 | B. 캐시별 타입 지정(`JacksonJsonRedisSerializer<T>`) | 3.1 함정, 캐시 3개뿐 |
| D2 | `spring-boot-starter-cache` 추가 여부 | ①의 확인 결과에 따름 | 3.2 |
| D3 | Feign `tmdb` 타임아웃 | 연결 3초, 읽기 5초 | TECH T-2(짧은 타임아웃). Task 048 첫 전체 수집에서 실측 후 조정 |
| D4 | 캐시 TTL | 기본 1시간, `titleDetail`·`planCalc`·`exclusiveTitles` 24시간 | 수집이 하루 1회(PRD 5.5)라 하루보다 길 필요가 없다. `planCalc`는 키에 가격·`data_as_of`가 들어가 무효화가 필요 없다(TECH 1절). Redis Cloud 30MB 한도 고려 |
| D5 | Redis 타임아웃 | 명령 2초, 연결 5초 | 첫 호출 11.5초 관측(Task 020)은 기동 직후 1회라 일반 요청 기준과 다르다. 운영에서 첫 요청 실패가 보이면 늘린다 |

## 6. 확인하지 못한 것 (이 Task에서 확인)

- 캐시 자동 구성에 `spring-boot-starter-cache`가 필요한지(①).
- `GenericJacksonJsonRedisSerializer`·`JacksonJsonRedisSerializer`의 생성자·빌더 시그니처(③, IDE·`c7`).
- record가 기본 타이핑에서 타입 정보를 받는지(D1에서 A를 고를 때만 중요, ⑤ 테스트).
- `@EnableFeignClients`가 클라이언트 없이 뜨는지(⑤·⑥).
- Feign 기본 디코더가 Jackson 3을 쓰는지(Task 042로 넘김).
- bucket4j-lettuce와 Lettuce 7 호환(Task 016 기록의 미확인 항목, 요청 제한 Task 049로 넘김. 이 Task와 무관).
