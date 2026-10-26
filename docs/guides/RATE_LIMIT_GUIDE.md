# 공개 API 요청 제한 (Task 049)

> 설명용 문서이고 근거 문서가 아니다. 규칙의 근거는 PRD FR-05·11절 12번, TECH 3절이고 코드가 기준이다.

## 1. 무엇을 막는가

로그인하지 않은 방문자(익명)가 `/api/public/**`와 인증 경로(`/api/auth/**`, `/api/oauth2/**`, `/api/login/**`)를 너무 자주 부르지 못하게 **IP별로** 횟수를 센다. 넘으면 429 `RATE_LIMIT_EXCEEDED`와 `Retry-After`(초)를 준다.

이유는 두 가지다. 서버·Redis 무료 한도를 한 명이 소진하는 것을 막고, 검색했는데 DB에 없는 작품을 TMDB에서 가져오는 "단건 수집"이 익명 사용자의 입력으로 TMDB 한도를 쓰게 되는 것을 막는다.

## 2. 개념

| 용어 | 뜻 |
|---|---|
| 필터 | 컨트롤러에 도착하기 전에 모든 요청이 순서대로 지나가는 관문. 다음으로 보내거나(`chain.doFilter`) 여기서 응답을 끝낼 수 있다. 요청이 어느 지점에서 걸리는지는 3절 |
| 버킷(토큰 버킷) | 토큰이 든 통. 요청 1건이 토큰 1개를 쓰고, 통은 정해진 속도로 다시 찬다. 통 크기가 순간 몰림 허용량, 채우는 속도가 평균 허용량이다 |
| fail-open / fail-closed | 한도를 세는 저장소(Redis)가 고장 났을 때 요청을 통과시킬지(open), 거부할지(closed) |

## 3. 필터는 요청 흐름의 어디서 작동하는가

서블릿 필터는 요청이 컨트롤러에 도착하기 **전에** 줄을 서서 지나가는 관문이다. 각 필터는 `chain.doFilter()`를 호출해 다음 관문으로 보내거나, 호출하지 않고 거기서 응답을 끝낼 수 있다. 요청 제한은 "호출하지 않고 끝내는" 쪽을 쓴다. 거부된 요청은 컨트롤러·DB·Security 검사까지 가지 않으므로 서버 일이 가장 적다.

```
브라우저 요청
  ↓
[서블릿 컨테이너(Tomcat)] ── 아래 필터들이 순서대로 지나간다(@Order 값이 작을수록 먼저)
  1. ForwardedHeaderFilter     X-Forwarded-For의 방문자 IP를 remoteAddr에 반영
  2. RequestIpLoggingFilter    (+10) 진입 IP를 debug로 기록
  3. OriginSecretFilter        (+20) Vercel을 거치지 않은 요청은 403
  4. ★ PublicRateLimitFilter   (+25) IP별 토큰 확인, 없으면 여기서 429로 끝낸다
  5. AdminBatchTokenFilter     (+30) 관리자 배치 토큰 검사
  6. Spring Security 필터 체인  (-100이지만 위 필터들은 HIGHEST_PRECEDENCE 기준이라 이 뒤) 인증·인가
  ↓
DispatcherServlet → (인터셉터) → 컨트롤러 → 서비스 → DB
  ↓
응답은 같은 필터들을 거꾸로 지나 브라우저로 돌아간다
```

| 질문 | 답 |
|---|---|
| 왜 3번 뒤인가 | 비밀 헤더가 없는 요청(Vercel을 안 거친 직접 호출)은 제한을 세기 전에 버린다. 그래야 가짜 IP로 버킷만 채우는 요청이 Redis를 쓰지 못한다 |
| 왜 6번(Security)보다 앞인가 | 로그인 여부와 상관없이 IP만 보고 거르기 때문이다. 또 Task 053에서 SecurityConfig를 통째로 바꿔도 이 필터는 영향이 없다 |
| 왜 인터셉터가 아니라 필터인가 | 인터셉터는 컨트롤러가 정해진 **뒤**에 도는 Spring MVC 안쪽 장치다. 코드한사바리는 "이 메서드는 토큰 5개"처럼 메서드 단위 어노테이션이 필요해 인터셉터를 썼다. 이번에는 경로와 IP만 보면 되고 가능한 한 앞에서 막고 싶어 필터를 쓴다 |
| 거부 응답은 누가 만드나 | 필터는 MVC 밖이라 `@RestControllerAdvice`가 직접 받지 못한다. 그래서 예외를 `HandlerExceptionResolver`에 넘기면 `GlobalExceptionHandler`가 429 ProblemDetail과 `Retry-After`를 만든다(오리진 필터와 같은 방식) |
| 어떤 경로만 걸리나 | `shouldNotFilter`에서 `/api/public`, `/api/auth`, `/api/oauth2`, `/api/login` 아래만 대상으로 판정하고 나머지는 4번을 그냥 통과한다 |

단건 수집 버킷(`SingleFetchRateLimiter`)은 이 필터 줄에 서지 않는다. 검색 서비스가 DB를 조회한 뒤 TMDB를 불러야 하는 지점에서 직접 호출하므로, 필터가 아니라 **서비스 코드 안**에서 작동한다.

## 4. 이전 프로젝트에서는 이렇게 했다

| | 코드한사바리(CVMento) | senahaza |
|---|---|---|
| 목적 | 로그인 사용자의 AI 기능 사용량 쿼터 | 익명 IP의 폭주 차단 |
| 구분 키 | 회원 ID | IP |
| 센 방법 | Redis 정수 `get` → 비교 → `decrement` | Redis `INCR` + 별도 `EXPIRE` |
| 채우는 방법 | 스케줄러가 2시간마다 `keys` 스캔 후 +10 | 키가 1분 뒤 만료 |
| 초과 시 | 429 | 60분 전면 차단(403) + DB `blocked_ip` 기록 |
| 약점 | 읽기와 차감이 분리돼 동시 요청에 뚫림, `keys` 전체 스캔, 키 TTL 없음 | `INCR`과 `EXPIRE` 사이 오류 시 영구 차단 가능, 요청마다 Redis 호출 증가, 공유 IP 오탐, 헤더 조건 없이 신뢰 |

둘 다 직접 구현했다. 당시에는 버킷 라이브러리를 몰랐다.

## 5. 이번에는 이렇게 했다

**Bucket4j 라이브러리**의 Redis 버킷을 쓴다. 카운터·만료·충전을 직접 만들지 않았다.

| 항목 | 이번 방식 |
|---|---|
| 원자성 | 토큰 확인과 차감을 Redis 안에서 한 번에 처리(CAS). 동시 요청에도 한도가 뚫리지 않는다 |
| 충전 | 시간 경과에 따라 라이브러리가 계산한다. 스케줄러·`keys` 스캔이 없다 |
| 만료 | 버킷이 가득 찬 뒤 10초 뒤 키가 지워진다. 가득 찬 버킷은 새 버킷과 같아 한도가 달라지지 않는다 |
| IP | `request.getRemoteAddr()`만 쓴다. 오리진 비밀 헤더로 Vercel 경유만 받으므로 위조가 막힌다(TECH 4절) |
| 초과 시 | 차단 기간 없이 토큰이 차면 바로 풀린다. `Retry-After`에 대기 초를 담는다 |
| DB·블랙리스트 | 만들지 않았다(사용자 결정). 남용이 관측되면 별도 Task |

| 버킷 | 키 | 용량 | 채움 | 저장소 장애 시 |
|---|---|---|---|---|
| 일반(`PublicRateLimiter`) | `rl:public:{ip}` | 20 | 분당 60 | 통과(fail-open) |
| 단건 수집(`SingleFetchRateLimiter`) | `rl:fetch:{ip}` | 3 | 분당 10 | 거부(fail-closed) |

저장소 오류가 나면 5초간 Redis를 부르지 않는다. 안 그러면 연결 대기(2초) 때문에 요청마다 늦어진다.

### `PublicRateLimiter.check` 한 줄 요약

필터가 대상 경로만 골라 `check`를 부르면 → Redis의 그 IP 버킷에서 토큰 1개를 쓰고 → 토큰이 없으면 429로 거부한다.

- **버킷 생성**: "있는지 확인하고 없으면 만든다"의 두 단계가 아니다. Bucket4j가 Redis 안에서 "있으면 쓰고, 없으면 가득 찬(20개) 새 버킷을 만들어 쓴다"를 한 번에 처리한다. 같은 IP의 첫 요청이 동시에 두 개 와도 버킷이 두 개 생기거나 토큰이 중복으로 쓰이지 않는다.
- **로그는 계속 남기지 않는다.** 정상 흐름은 조용히 두고, 거부는 로그가 아니라 카운터로 센다. 공격 중에는 거부가 초당 수백 건일 수 있어 거부마다 `warn`을 남기면 로그가 쌓여 서버를 괴롭히기 때문이다.

| 상황 | 로그·지표 |
|---|---|
| 토큰이 있어 통과 | 없음 |
| 토큰이 없어 거부(429) | `debug`만(`GlobalExceptionHandler`). 평소엔 안 보인다 |
| 거부 횟수 | 카운터 `ratelimit.rejected{bucket=public \| single_fetch}`. IP는 태그에 넣지 않는다 |
| Redis 오류(일반은 통과, 단건은 거부) | `warn` 1줄. 5초에 한 번만 |
| 진입 IP 기록 | `RequestIpLoggingFilter`가 `debug`로 `remoteAddr`만 |

## 6. 흐름 예시

통은 방문자(IP)마다 따로 있다. 아래 "통"은 모두 그 방문자 전용 통이다.

```
방문자 1.2.3.4가 GET /api/public/ott-services 를 1분에 70번 호출
 요청 1~20  : 1.2.3.4의 통에 토큰 20개가 있어 통과(순간 몰림)
 요청 21~60 : 초당 1개씩 채워지는 토큰으로 통과
 요청 61~   : 토큰 없음 → 429 { errorCode: RATE_LIMIT_EXCEEDED }, Retry-After: 1
 다른 방문자 5.6.7.8 : 키가 달라 영향 없음
```

단건 수집 버킷은 검색 서비스가 "DB에 없어 TMDB를 불러야 하는 지점"에서만 `check(clientIp)`로 소모한다(Task 050이 연결). DB에 있는 작품 검색은 일반 버킷만 쓴다.

## 7. 파일 추적과 읽는 순서

요청 하나가 지나가는 순서대로 파일을 따라간다(`backend/src/main/java/com/ottnavi/global/` 기준).

```
요청 → PublicRateLimitFilter.shouldNotFilter        대상 경로인지 판정
     → PublicRateLimitFilter.doFilterInternal       request.getRemoteAddr()로 check 호출
     → PublicRateLimiter.check
         1. RateLimitSupport.FailureWindow          최근 Redis 오류면 5초간 건너뜀(통과)
         2. RedisRateLimitBucketProvider.getBucket  키 rl:public:{ip}의 버킷을 Redis에서 얻음(첫 호출에 연결)
            → Bucket.tryConsumeAndReturnRemaining   Bucket4j가 Redis 안에서 토큰 1개 소모(원자적)
         3. 토큰 없음 → RateLimitSupport.toCeilSeconds로 대기 초 올림 → RateLimitExceededException
     → (예외) HandlerExceptionResolver → GlobalExceptionHandler.handleRateLimitExceeded
                                                    429 ProblemDetail + Retry-After 헤더
     → (통과) chain.doFilter → 다음 필터 … 컨트롤러

검색 서비스(Task 050) → SingleFetchRateLimiter.check   TMDB 호출 직전에만. 구조는 위와 같고 Redis 오류면 거부
```

| 단계 | 파일 | 하는 일 |
|---|---|---|
| 입구 | `ratelimit/PublicRateLimitFilter` | 경로 판정, `remoteAddr`로 검사 호출, 예외를 429로 위임 |
| 판단 | `ratelimit/PublicRateLimiter` | 일반 버킷 소모, fail-open, 거부 카운터 |
| 판단(단건) | `ratelimit/SingleFetchRateLimiter` | 단건 수집 버킷 소모, fail-closed |
| 저장소 | `ratelimit/RedisRateLimitBucketProvider` | Bucket4j를 Redis(Lettuce)에 연결, 키 만료 설정 |
| 공통 계산 | `ratelimit/RateLimitSupport` | 버킷 설정 만들기, 초 올림, 오류 후 건너뛰기 표시 |
| 조립·설정 | `ratelimit/RateLimitConfig`, `RateLimitProperties`, `application.yml` | 빈 등록, `app.rate-limit.*` 값, `enabled=false`면 전부 끔 |
| 응답 변환 | `error/RateLimitExceededException`, `error/GlobalExceptionHandler` | 예외 → 429 + `Retry-After` |
| 로그 | `security/RequestIpLoggingFilter` | `debug`에 `remoteAddr`만 기록 |

### 단건 수집 버킷은 아직 연결되지 않았다 (Task 050에서 연결)

`SingleFetchRateLimiter.check()`를 부르는 코드는 **지금 없다.** 검색 API가 Task 050에서 만들어지기 전이라 049에서는 부품과 빈 등록까지만 했다. 050에서 아래처럼 연결한다.

```java
// 컨트롤러: HTTP 요청을 아는 곳이므로 IP를 여기서 꺼내 서비스로 내려보낸다
titleSearchService.search(query, request.getRemoteAddr());

// 서비스: DB 먼저 조회하고, 없어서 TMDB를 불러야 하는 순간에만 소모한다
if (found.isEmpty()) {
    singleFetchRateLimiter.check(clientIp);   // 한도 초과면 예외(429), TMDB를 부르지 않는다
    tmdbClient.search(query);
}
```

| 상황 | `check()` | 소모하는 버킷 |
|---|---|---|
| DB에 작품이 있음 | 호출 안 함 | 일반 버킷(필터)만 |
| DB에 없어 TMDB를 불러야 함 | TMDB 호출 직전에 호출 | 단건 수집 버킷(IP당 분당 10회) |
| 한도 초과 | 예외 → TMDB 호출 안 함 | 429 + `Retry-After` |

연결이 빠지면 TMDB 한도 보호가 동작하지 않으므로, 050의 통합 테스트가 "DB에 없는 작품 검색 → 단건 수집 → 한도 초과 429"를 확인한다(ROADMAP 050 완료 기준).

읽는 깊이는 아래와 같다.

| 순서 | 파일 | 읽는 깊이 |
|---|---|---|
| 1 | `PublicRateLimitFilter` | 꼭 읽기 (경로 판정, 429 위임) |
| 2 | `PublicRateLimiter` | 꼭 읽기 (소모·fail-open) |
| 3 | `SingleFetchRateLimiter` | 훑기 (fail-closed 차이) |
| 4 | `RedisRateLimitBucketProvider` | 훑기 (Bucket4j 연결, 지연 연결 이유) |
| 5 | `RateLimitSupport`, `RateLimitConfig`, `RateLimitProperties` | 넘어가기 |

## 8. 테스트가 확인하는 것

| 테스트 | 확인 | 막으려는 사고 |
|---|---|---|
| 필터 경로 판정 | 적용 4경로만 검사, `/%61pi/...`·세미콜론 우회도 같은 경로로 판정 | 인코딩 우회로 제한 회피 |
| 한도 초과 | 429와 `Retry-After`(올림) | 헤더 없이 막아 프론트가 대기 시간을 모름 |
| IP별 분리 | 다른 IP는 영향 없음 | 한 명 때문에 모두 막힘 |
| 헤더 무시 | `x-real-ip`·`X-Forwarded-For`를 직접 읽지 않음 | Vercel IP로 전원이 한 버킷에 묶임, 헤더 위조 |
| fail-open / fail-closed | 일반은 통과, 단건은 거부, 5초 건너뜀 | Redis 장애가 서비스 전체 장애가 되거나 TMDB 한도 소진 |
| 설정 끔 | `enabled=false`면 필터·검사기 빈이 없음 | 테스트가 한도에 걸림 |

Redis 실연동 테스트(`PublicRateLimitRedisIntegrationTest`)는 Docker가 필요해 작성만 해 두었고, 처음 실행 때 Bucket4j 8.21과 Lettuce 7.5의 실제 호환과 요청당 Redis 연산 수를 확인한다.
