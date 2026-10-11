# OpenFeign 사용법과 개념 (TMDB 클라이언트 코드로 배우기)

> 2026-10-12 작성. Task 041(TMDB Feign 클라이언트)을 구현하며 오간 설명을 정리한 학습 문서다. 규칙의 근거 문서가 아니다(근거는 `docs/TECH.md` 3절·6절, `docs/TASK041_GUIDE.md`).
> 코드 예시는 `backend/src/main/java/com/ottnavi/infra/tmdb/` 아래 실제 코드에서 가져왔다. 줄 수를 줄이려고 일부를 생략했으면 `// ...`로 표시했다.
> 근거 수준: **코드 확인**(이 저장소 코드를 읽음), **테스트로 확인**(통과한 테스트가 있음), **문서 기반**(공식 문서·라이브러리 소스를 읽은 안내서의 내용. 실행으로는 확인하지 않음).

## 1. 한눈에 보기

**OpenFeign**은 "인터페이스에 이 주소로 GET 요청을 보낸다고 *선언*만 적으면, HTTP 호출 코드를 대신 만들어 주는 도구"다. 외부 API(우리는 TMDB)를 부를 때 쓴다.

가장 단순한 사용은 이 세 가지다.

```
① yml에 주소를 둔다  →  ② 인터페이스에 @FeignClient와 @GetMapping을 선언한다  →  ③ 메서드를 부르면 요청이 나간다
```

TMDB 클라이언트는 이 뼈대 위에 네 가지 기능을 더했다. 배치가 TMDB를 수만 번 부르기 때문이다.

| 더한 기능 | 이유 | 담당 |
|---|---|---|
| 인증 | TMDB는 모든 요청에 읽기 액세스 토큰을 요구한다 | `TmdbFeignConfig`의 인증 인터셉터 |
| 호출 속도 제한 | TMDB 한도(초당 약 40회)를 넘으면 차단당한다 | `TmdbConfig`의 버킷, `TmdbRateLimiter`, 속도 제한 인터셉터 |
| 실패 분류와 재시도 | 429·5xx·타임아웃은 일시적이라 다시 하면 성공할 수 있다 | `TmdbErrorDecoder`, `TmdbRetryer` |
| 오류 번역과 호출 통계 | 소비자가 Feign을 몰라도 되고, 호출량을 센다 | `TmdbClient`, `exception/` |

## 2. OpenFeign 기본 개념

### 2.1 최소 예제 (TMDB가 아니어도 모양은 같다)

```yaml
# application.yml
app:
  tmdb:
    base-url: https://api.themoviedb.org/3
```

```java
// 인터페이스만 쓴다. 구현 코드는 없다
@FeignClient(name = "tmdb", url = "${app.tmdb.base-url}")
public interface TmdbApi {

    @GetMapping("/movie/{id}")
    TmdbMovieDetail getMovie(@PathVariable("id") long movieId,
                             @RequestParam("language") String language);
}
```

```java
// 쓰는 쪽: 일반 메서드처럼 부른다
TmdbMovieDetail detail = tmdbApi.getMovie(550, "ko-KR");
```

이 메서드를 부르면 `GET https://api.themoviedb.org/3/movie/550?language=ko-KR`이 나가고, 응답 JSON이 `TmdbMovieDetail`로 바뀌어 돌아온다.

### 2.2 구현 코드는 누가 만드나
앱이 시작될 때 Spring이 `@FeignClient`가 붙은 인터페이스를 찾아 **구현체(프록시)를 자동으로 만든다.** 이 프로젝트에서는 `global/config/FeignConfig.java`의 `@EnableFeignClients(basePackages = "com.ottnavi.infra")`가 그 탐색을 켠다. 즉 **외부 연동 클라이언트는 `com.ottnavi.infra` 아래에만 둬야** 찾아진다(코드 확인).

### 2.3 어노테이션 읽는 법
| 어노테이션 | 뜻 |
|---|---|
| `@FeignClient(name, url, configuration)` | 이 인터페이스는 외부 API 클라이언트다. `name`은 설정 키와 연결되고, `url`은 기본 주소, `configuration`은 이 클라이언트 전용 부품 목록이다. |
| `@GetMapping("/movie/{id}")` | HTTP GET, 경로. 이 어노테이션은 Spring MVC에서 쓰던 것을 그대로 쓴다. |
| `@PathVariable("id")` | 경로의 `{id}` 자리에 들어갈 값 |
| `@RequestParam("language")` | 쿼리 문자열(`?language=...`). 값이 `null`이면 쿼리에서 빠진다(테스트로 확인). |

### 2.4 용어
| 용어 | 뜻 |
|---|---|
| 인터셉터(`RequestInterceptor`) | 요청이 나가기 **직전**에 끼어들어 헤더를 붙이는 등 처리하는 부품. 시도마다(재시도 포함) 실행된다. |
| `ErrorDecoder` | 실패 응답(4xx·5xx)을 받았을 때 "이 실패를 어떤 예외로 바꿀까, 재시도할까"를 정하는 부품 |
| `Retryer` | 재시도 가능한 실패가 났을 때 "다시 할까, 얼마나 기다릴까"를 정하는 부품. 호출마다 `clone()`해서 쓴다. |
| 타임아웃 | 연결 제한 시간(`connectTimeout`), 응답 제한 시간(`readTimeout`). yml에 둔다. |
| 버킷 | 호출 한 번에 토큰 1개를 쓰는 "토큰 통". 일정 속도로 다시 채워져서 호출 속도를 제한한다(Bucket4j 라이브러리). |
| 백오프 | 실패 후 다시 시도하기 전 기다리는 시간을 점점 늘리는 방식 |
| DTO | 응답 JSON을 받는 자바 그릇(`record`). 우리는 TMDB 응답마다 하나씩 만든다. |

## 3. 우리 코드 지도

### 3.1 파일과 역할 (`com.ottnavi.infra.tmdb`)
| 파일 | 역할 | 한 줄 |
|---|---|---|
| `TmdbApi` | **Feign 인터페이스** | TMDB 주소 10개를 선언한 목록. 구현은 Spring이 만든다. |
| `dto/` (16개) | 응답 그릇 | TMDB JSON을 받는 `record`들 |
| `TmdbMediaType` | 영화/드라마 enum | `listProviders`가 `/watch/providers/movie`와 `/tv`로 갈려서 필요 |
| `TmdbProperties` | 설정 그릇 | yml의 `app.tmdb.*`를 담는 `record` |
| `TmdbConfig` | **앱 전체 부품 등록** | 설정 그릇 등록, 토큰 통(버킷), 속도 제한기 |
| `TmdbFeignConfig` | **Feign 전용 부품 등록** | 인증, 속도 제한 인터셉터, 오류 분류, 재시도 |
| `TmdbRateLimiter` | 속도 제한기 | `acquire()`로 토큰 1개를 얻는다 |
| `TmdbErrorDecoder` | 실패 분류 | 429·5xx는 재시도 가능, 그 밖의 4xx는 재시도 불가 |
| `TmdbRetryer` | 재시도 규칙 | 횟수와 대기 시간 |
| `exception/` (4개) | 최종 실패 종류 | `TmdbException`(부모), `TmdbNotFoundException`, `TmdbRequestException`, `TmdbUnavailableException` |
| `TmdbClient` | **소비자용 창구** | `TmdbApi`를 감싸 고정값 채움, 오류 번역, 통계 |

### 3.2 호출 흐름
```
소비자(Task 042~050)
   │  tmdbClient.getMovie(550, "ko-KR", "credits")
   ▼
TmdbClient           고정값(KR, ko-KR 등) 채움 · 결과 카운트(tmdb.call) · 실패를 TmdbException으로 번역
   │
   ▼
TmdbApi              Feign 인터페이스(구현은 Spring이 자동 생성)
   │
   ▼  요청이 나가기 직전, TmdbFeignConfig의 부품이 차례로 실행된다
   ├─ 인증 인터셉터        "Authorization: Bearer 토큰" 헤더를 붙인다
   └─ 속도 제한 인터셉터   TmdbRateLimiter.acquire() → 버킷에서 토큰 1개 (없으면 최대 2초 대기, 넘으면 포기)
   ▼
TMDB 서버
   │
   ├─ 성공(200)  JSON → DTO 변환 → TmdbClient가 "success" 카운트 → 소비자에게 반환
   │
   └─ 실패       TmdbErrorDecoder  재시도할 만한 실패인가?
                  ├─ 429·5xx·타임아웃  →  TmdbRetryer  기다린 뒤 재시도 (횟수 초과면 포기)
                  └─ 400·401·404 등     →  재시도 없이 바로 실패
                 ▼ 최종 실패
              TmdbClient가 Feign 예외를 TmdbException 3종으로 번역해 던진다
```

## 4. 요청 한 번의 생애

### 4.1 정상: 영화 상세
1. 소비자가 `tmdbClient.getMovie(550, "ko-KR", "credits")`를 부른다.
2. `TmdbClient`가 `call("movie_detail", ...)`로 감싸 `TmdbApi.getMovie`를 부른다.
3. Feign이 `GET https://api.themoviedb.org/3/movie/550?language=ko-KR&append_to_response=credits`를 조립한다.
4. 인증 인터셉터가 `Authorization: Bearer ...`를 붙이고, 속도 제한 인터셉터가 토큰 1개를 얻는다.
5. 200 응답의 JSON을 Jackson이 `TmdbMovieDetail`로 바꾼다.
6. `tmdb.call{endpoint=movie_detail, result=success}`가 1 늘고 DTO가 소비자에게 간다.

### 4.2 429(너무 많이 호출함)
1. 429 응답이 오면 `TmdbErrorDecoder`가 "재시도 가능"으로 분류한다.
2. `TmdbRetryer`가 대기 시간을 정한다. `Retry-After` 헤더가 있으면 그 시간을 쓰고, 없으면 0.5초에서 시작해 두 배씩(최대 4초, ±20% 흔들림).
3. 총 3번 시도해도 실패하면 포기하고 `TmdbClient`가 `TmdbUnavailableException`으로 번역한다. 이 예외는 `EXTERNAL_API_ERROR`(502)로 응답된다.
4. `Retry-After`가 5초를 넘으면 기다리지 않고 바로 포기한다.

### 4.3 404(그런 작품이 없다)
재시도하지 않고 바로 `TmdbNotFoundException`을 던진다. 호출한 수집 배치는 이 작품만 건너뛰고 다음으로 간다(안내서 7.1절).

### 4.4 타임아웃(응답이 안 온다)
연결 3초, 응답 5초를 넘으면 Feign이 상태 `-1`인 `RetryableException`으로 감싼다. 타임아웃은 총 2번까지만 시도한다(오래 붙잡으면 배치가 DB 연결을 계속 쥔다).

## 5. 부품 자세히 보기

### 5.1 `TmdbApi` — 무엇을 보내고 받는가
10개 호출이 선언돼 있다: `discoverMovies`, `discoverTvs`, `searchMovies`, `searchTvs`, `getMovie`, `getTv`, `getTvSeason`, `getMovieWatchProviders`, `getTvSeasonWatchProviders`, `listProviders`. 파라미터 이름은 TMDB 문서의 이름을 그대로 쓴다.

```java
@FeignClient(
        name = "tmdb",                              // application.yml의 spring.cloud.openfeign.client.config.tmdb 키와 같아야 타임아웃이 적용된다
        url = "${app.tmdb.base-url}",
        configuration = TmdbFeignConfig.class       // 이 클라이언트에만 적용할 부품
)
public interface TmdbApi {

    @GetMapping("/discover/movie")
    TmdbPage<TmdbMovieSummary> discoverMovies(
            @RequestParam("with_watch_providers") String withWatchProviders,
            @RequestParam("watch_region") String watchRegion,
            @RequestParam("with_watch_monetization_types") String monetizationTypes,
            @RequestParam("language") String language,
            @RequestParam("page") int page);
    // ...
}
```

반환 타입이 곧 응답의 모양이다. `TmdbPage<TmdbMovieSummary>`는 "목록 응답(`page`, `total_pages`, `results[]`)의 `results`가 영화 요약"이라는 뜻이다.

### 5.2 DTO — 응답을 받는 그릇
```java
@JsonIgnoreProperties(ignoreUnknown = true)     // 우리가 안 쓰는 필드는 버린다. Jackson 3은 기본이 "모르는 필드면 실패"다
public record TmdbMovieSummary(
        long id,
        String title,
        @JsonProperty("original_title") String originalTitle,   // JSON의 snake_case를 camelCase 필드로
        @JsonProperty("release_date") String releaseDate,       // 날짜도 String. TMDB가 ""를 줄 때가 있어 LocalDate로 받으면 실패할 수 있다
        // ...
) { }
```

- 모든 TMDB DTO에 `@JsonIgnoreProperties(ignoreUnknown = true)`가 필요하다(테스트로 확인: 모르는 필드가 있어도 매핑에 성공).
- null이 올 수 있는 숫자는 `Integer`로 받는다(원시 타입이면 null에서 실패할 수 있다).

### 5.3 `TmdbProperties` — yml의 그릇
```java
@ConfigurationProperties(prefix = "app.tmdb")      // yml의 app.tmdb 아래를 이 record에 담는다
public record TmdbProperties(String baseUrl, String apiToken, RateLimit rateLimit, Retry retry) { ... }
```
yml의 `base-url`은 `baseUrl`, `api-token`은 `apiToken`으로 자동 연결된다. 토큰은 `${TMDB_API_KEY:}`로 환경 변수에서 받고 기본값을 비워 둔다(토큰이 없어도 앱이 뜬다. 호출할 때 401로 드러난다). `toString()`은 `apiToken=***`로 가려서 로그에 토큰이 찍히지 않게 한다.

### 5.4 두 설정 파일의 차이
| | `TmdbConfig` | `TmdbFeignConfig` |
|---|---|---|
| 범위 | 앱 전체에서 하나 | TMDB Feign 클라이언트만 |
| `@Configuration` | **붙인다** | **붙이지 않는다** |
| 등록하는 것 | 설정 그릇(`@EnableConfigurationProperties`), 버킷(`tmdbBucket`), `TmdbRateLimiter` | 인증 인터셉터, 속도 제한 인터셉터, `ErrorDecoder`, `Retryer` |

`TmdbFeignConfig`에 `@Configuration`을 붙이지 않는 이유: 붙이면 컴포넌트 스캔에 잡혀 **앞으로 추가할 모든 Feign 클라이언트(예: 메일 API)에도 적용된다.** 그러면 TMDB 토큰이 엉뚱한 외부 API로 실려 나간다. `@FeignClient(configuration = TmdbFeignConfig.class)`로만 연결해 TMDB에만 적용한다(테스트로 확인: 앱 전체로 퍼지지 않는다).

버킷을 `TmdbConfig`에 두는 이유: `TmdbFeignConfig`는 Feign 하위 컨텍스트라 거기서 만들면 Feign 클라이언트가 늘 때 버킷도 늘어 "서버 전체 초당 25회" 제한이 깨진다. 앱 전체 설정인 `TmdbConfig`에 하나만 둬서 수집 배치(043)와 단건 검색(050)이 같은 버킷을 같이 쓴다.

### 5.5 `TmdbRateLimiter` — 호출 속도 제한
```java
public void acquire() {
    try {
        // Bucket4j는 기다리는 tryConsume에 0을 허용하지 않아서, 0 이하면 기다리지 않는 tryConsume(1)을 쓴다
        boolean acquired = (maxWait.isZero() || maxWait.isNegative())
                ? bucket.tryConsume(1)
                : bucket.asBlocking().tryConsume(1, maxWait);
        if (!acquired) {
            throw new TmdbUnavailableException("TMDB 호출 한도 대기 시간을 넘겼다");
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new TmdbUnavailableException("TMDB 호출 대기 중 중단되었다", e);
    }
}
```
- 토큰 통의 알고리즘 자체는 라이브러리(Bucket4j)가 한다. 이 클래스는 "토큰을 못 얻으면 최대 2초 기다리고 그래도 안 되면 포기"라는 우리 규칙을 입힌 얇은 부품이다.
- **함정(테스트로 발견)**: `asBlocking().tryConsume(1, Duration.ZERO)`는 "Waiting value should be positive" 예외를 던진다. 그래서 대기 시간이 0이면 기다리지 않는 방식으로 나눈다.
- 인터셉터는 재시도마다 실행되므로 **재시도도 토큰을 쓴다.** 재시도도 TMDB에 가는 실제 요청이라 한도에 포함돼야 해서 의도한 동작이다.
- 숫자 점검: `capacity(10) + 1초 동안 채워지는 토큰(25) = 35`로 TMDB 한도(약 40) 아래다.

### 5.6 `TmdbErrorDecoder` — 실패를 분류한다
```java
boolean transientStatus = status == 429 || status >= 500;
if (!transientStatus) {
    // 4xx는 Retry-After가 붙어 있어도 재시도하지 않는다
    ...
    return base;
}
// 429·5xx는 RetryableException으로 만들어 Retryer가 다시 시도하게 한다
```
- 429·5xx → 재시도 가능(`RetryableException`)
- 400·401·404·422 등 → 재시도 불가(원래 예외 그대로). 다시 해도 같은 결과이고 TMDB 한도만 쓰기 때문이다.
- Feign 기본 `ErrorDecoder.Default`는 상태 코드를 따지지 않고 `Retry-After`가 있는 응답만 재시도 대상으로 본다. 그래서 우리가 상태 코드로 다시 나눈다.
- 응답 본문은 로그에 넣지 않는다(본문에 민감 정보가 있을 수 있다).

### 5.7 `TmdbRetryer` — 다시 할지, 얼마나 기다릴지
재시도 가능으로 분류된 실패에 대해서만 작동한다.
```java
attempt++;
int limit = isIoError(e) ? config.ioMaxAttempts() : config.maxAttempts();   // 타임아웃은 2번, 429·5xx는 3번
if (attempt >= limit) { throw e; }                                          // 횟수를 다 썼으면 포기

Long retryAfter = e.retryAfter();               // TMDB가 알려 준 "다시 해도 되는 시각"
if (retryAfter != null) {
    waitMillis = Math.max(0, retryAfter - System.currentTimeMillis());
    if (waitMillis > config.maxRetryAfter().toMillis()) throw e;            // 5초 넘게 기다리라면 포기
} else {
    waitMillis = backoffWithJitterMillis();                                 // 0.5초 → 1초 → ... (최대 4초), ±20% 흔들림
}
Thread.sleep(waitMillis);
```
- **판단(재시도할 실패인가)은 `TmdbErrorDecoder`, 실행(몇 번·얼마나 기다리나)은 `TmdbRetryer`.** 둘이 이어져 동작한다.
- ±20% 흔들림(지터)은 여러 요청이 같은 순간에 몰려 다시 부딪히는 것을 막는다.
- `clone()`이 필요한 이유: 이 클래스는 시도 횟수(`attempt`)를 들고 있다. Feign이 호출마다 새 복사본을 만들어 써야 한 호출의 실패가 다음 호출에 이어지지 않는다(테스트로 확인).

### 5.8 예외 4종 — 호출한 쪽에 알리는 최종 결과
| 예외 | 언제 | 호출한 쪽이 할 일 |
|---|---|---|
| `TmdbNotFoundException` | 404 (그 ID가 없다) | 이 작품만 건너뛰고 계속 |
| `TmdbUnavailableException` | 429·5xx·타임아웃·연결 오류·로컬 한도 초과·응답 해석 실패 | 일시 장애로 보고 실패 건수로 센다 |
| `TmdbRequestException` | 401·400·422 등(요청이나 토큰 설정 문제) | 모든 호출이 실패할 것이므로 Job을 멈추고 알린다 |

부모 `TmdbException`은 `BusinessException`을 상속하고 오류 코드는 모두 `EXTERNAL_API_ERROR`(502)다. TMDB가 준 429도 여기에 모이며, 우리 서비스의 공개 API 요청 제한(`RATE_LIMIT_EXCEEDED`, 429)과 다른 개념이라 섞지 않는다. 예외 메시지는 API 응답에 노출될 수 있어서 URL·응답 본문·토큰을 넣지 않고 고정 문구만 쓴다. 이 예외들은 재시도를 위한 것이 아니라, **재시도를 다 하고도 실패했을 때** 알리는 결과물이다.

### 5.9 `TmdbClient` — 소비자용 창구
```java
public TmdbPage<TmdbMovieSummary> discoverMovies(String withWatchProviders, int page) {
    return call(EP_DISCOVER_MOVIE,
            () -> api.discoverMovies(withWatchProviders, REGION, MONETIZATION_FLATRATE, LANGUAGE_KO, page));
}

private <T> T call(String endpoint, Supplier<T> action) {
    try {
        T result = action.get();
        count(endpoint, RESULT_SUCCESS);            // 성공 카운트
        return result;
    } catch (FeignException e) {
        Failure failure = toFailure(e);             // 상태 코드로 TmdbException 3종 중 하나로 번역
        count(endpoint, failure.result());          // 결과 종류별 카운트
        throw failure.exception();
    }
}
```
- 소비자는 `discoverMovies("8", 1)`처럼 필요한 값만 넘긴다. `watch_region=KR`, `language=ko-KR`, `flatrate`는 여기서 채운다.
- 모든 호출을 `call()`로 감싸 **카운터 `tmdb.call{endpoint, result}`** 를 센다. 태그 값은 정해진 집합이라 작품 ID·검색어 같은 값은 태그에 넣지 않는다(카운터가 무한히 늘어나는 것을 막는다). `result`는 `success`, `not_found`, `rate_limited`, `server_error`, `client_error`, `io_error`, `local_limit`, `decode_error` 8가지다.
- 소비자는 `TmdbApi`를 직접 부르지 않는다. 직접 부르면 고정값, 오류 번역, 통계를 각자 해야 한다.

## 6. 헷갈리기 쉬운 것

| 헷갈리는 것 | 정리 |
|---|---|
| `TmdbApi`와 `TmdbClient` | `TmdbApi`는 Feign 인터페이스(OpenFeign 용어로는 이것이 "클라이언트")이고, `TmdbClient`는 그것을 감싼 우리 창구다. 소비자는 `TmdbClient`만 쓴다. |
| `ErrorDecoder`와 `Retryer` | `ErrorDecoder`는 "이 실패를 재시도할까"를 **분류**하고, `Retryer`는 "몇 번, 얼마나 기다리며"를 **실행**한다. |
| `TmdbConfig`와 `TmdbFeignConfig` | 앞은 앱 전체 부품(버킷, 설정 그릇), 뒤는 TMDB 호출에만 붙는 부품(인증, 오류 분류, 재시도). |
| 예외 4종의 목적 | 재시도를 위한 것이 아니라 재시도 후 최종 실패를 알리는 결과물이다. |
| `TmdbRateLimiter`와 Task 049의 요청 제한 | 전자는 **서버 전체**가 TMDB 한도를 넘지 않게 하는 안전망이고, 후자는 익명 방문자 한 명의 남용을 막는 별개 장치다(IP별 Redis 버킷). |
| 설정 위치 | 타임아웃·로그 수준은 `TmdbFeignConfig`가 아니라 yml(`spring.cloud.openfeign.client.config.tmdb`)에 있다. `name = "tmdb"`가 이 키와 같아야 적용된다. |

## 7. 설정(`application.yml`)
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          tmdb:                  # @FeignClient(name = "tmdb")와 같은 이름
            connectTimeout: 3000   # ms. 연결이 안 되면 빨리 실패해 DB 연결을 붙잡지 않는다
            readTimeout: 5000      # ms
            loggerLevel: basic     # 요청 메서드·URL·상태·시간만. 본문·헤더(토큰)는 남기지 않는다
app:
  tmdb:
    base-url: https://api.themoviedb.org/3
    api-token: ${TMDB_API_KEY:}    # 읽기 액세스 토큰(Bearer). 기본값을 비워 토큰 없이도 기동된다
    rate-limit:
      capacity: 10          # 통 크기(순간에 몰아 쓸 수 있는 최대 호출 수)
      refill-tokens: 25     # refill-period마다 채워지는 토큰 수 (→ 초당 25회)
      refill-period: 1s
      max-wait: 2s          # 토큰이 없을 때 최대 기다리는 시간. 넘으면 포기
    retry:
      max-attempts: 3          # 429·5xx 총 시도 횟수(첫 시도 포함)
      io-max-attempts: 2       # 타임아웃·연결 오류 총 시도 횟수
      initial-backoff: 500ms   # 첫 재시도 전 대기. 이후 두 배씩
      max-backoff: 4s          # 지수 대기의 상한
      max-retry-after: 5s      # 서버가 이보다 오래 기다리라 하면 포기
```
수치의 근거는 안내서 결정 D1(버킷), D2(재시도)다. 최악의 경우 한 호출이 걸리는 시간은 타임아웃 × 재시도 횟수 + 대기라서, 수집 배치가 DB 연결을 오래 쥐지 않도록 짧게 잡았다.

## 8. 함정과 교훈 (이 Task에서 실제로 부딪힌 것 포함)
1. **Jackson 3은 모르는 필드에서 실패한다.** 모든 TMDB DTO에 `@JsonIgnoreProperties(ignoreUnknown = true)`가 필요하다.
2. **날짜는 `""`로 올 수 있다.** `String`으로 받고 변환은 소비 쪽에서 한다.
3. **`TmdbFeignConfig`에 `@Configuration`을 붙이지 않는다.** 다른 Feign 클라이언트로 설정이 퍼진다.
4. **`TMDB_API_KEY`에 기본값을 둔다(`${TMDB_API_KEY:}`).** 없으면 값이 없는 환경(운영 첫 배포 등)에서 앱이 뜨지 않는다.
5. **Bucket4j는 대기 시간 0을 허용하지 않는다.** 테스트 T16-a가 이것을 잡았다.
6. **토큰을 로그에 남기지 않는다.** `TmdbProperties.toString()`을 가리고, `Request` 객체를 문자열로 찍지 않는다(요청 헤더에 토큰이 있다).
7. **외부 호출은 트랜잭션 밖에서 한다**(수집 배치만 예외). 한 호출이 최악에 오래 걸려도 DB 연결을 오래 쥐지 않도록 타임아웃과 재시도를 짧게 했다.
8. **코드를 믿는 근거는 문서 인용이 아니라 테스트다.** 안내서의 코드는 문서와 소스를 읽고 쓴 것이라 실행 전에는 틀릴 수 있었고(함정 5), 통과한 테스트가 동작을 증명한다.
9. **`@ConfigurationProperties`의 `prefix`는 `"app.tmdb"`다.** 구현 중 `"app.tmdb.*"`로 입력된 적이 있었다. 그 상태에서도 테스트가 통과했지만 이유는 확인하지 못했다. `"app.tmdb"`로 고친 뒤 TMDB 테스트가 문제없이 통과하는 것을 확인했다(2026-10-12, 사용자 실행). 설정 이름 끝에 `.*`를 붙이지 않는다. `.*`는 "그 아래 전부"를 뜻하는 설명 주석의 표기일 뿐 prefix 값이 아니다.

## 9. 다른 외부 API를 붙일 때의 순서 (메일 API, LLM 등)
1. 외부 API 문서를 보고 호출할 주소와 응답 모양을 정한다.
2. `com.ottnavi.infra.{이름}` 패키지에 **인터페이스**(`@FeignClient`)와 **DTO**(`record`)를 만든다. 패키지가 `com.ottnavi.infra` 밖이면 `@EnableFeignClients`가 찾지 못한다.
3. yml에 주소와 설정을 두고 `@ConfigurationProperties` record를 만든다. 비밀 값은 환경 변수로 받고 기본값을 비운다.
4. 인증이 필요하면 `RequestInterceptor`를 만들어 전용 `*FeignConfig`에 등록한다(**`@Configuration`을 붙이지 않는다**).
5. 실패 처리가 필요하면 `ErrorDecoder`와 `Retryer`를 만든다. 재시도할 실패와 안 할 실패를 먼저 나눈다.
6. 호출 속도 제한이 필요한지 판단한다(상대 API에 한도가 있으면 필요).
7. 소비자가 Feign을 몰라도 되도록 **창구 클래스**로 감싸고 오류를 우리 예외로 번역한다.
8. 가짜 서버(WireMock)로 정상, 429, 5xx, 타임아웃, 404, 이상한 JSON을 테스트한다. 실제 API는 테스트에서 부르지 않는다.

## 10. 테스트 지도 (`src/test/java/com/ottnavi/infra/tmdb/`)
| 증명하려는 것 | 테스트 |
|---|---|
| 요청이 올바르게 나간다(헤더, 경로, 쿼리), JSON이 DTO로 바뀐다 | `TmdbClientTest` |
| 프로퍼티·인증 헤더, `toString`에 토큰이 없다 | `TmdbFeignConfigTest`, `TmdbPropertiesTest` |
| 속도 제한(즉시 포기, 대기 후 통과, 대기 초과 포기, 인터럽트) | `TmdbRateLimiterTest` |
| 실패 분류(429·5xx 재시도, 400·401·404 불가) | `TmdbErrorDecoderTest` |
| 재시도 규칙(횟수, `Retry-After`, 복제) | `TmdbRetryerTest`, `TmdbRetryTest` |
| 최종 예외 번역과 요청 횟수, 응답 해석 실패 | `TmdbErrorTest` |
| 타임아웃 후 포기 | `TmdbTimeoutTest` |
| 호출 통계 `tmdb.call` | `TmdbMetricsTest` |
| 토큰이 DEBUG 로그에도 안 남는다 | `TmdbTokenLogTest` |
| 실제 앱에서 조립된다(토큰 없이 기동, 설정 안 퍼짐) | `TmdbClientIntegrationTest` |

테스트는 진짜 TMDB가 아닌 WireMock(가짜 서버)을 부른다. 결과는 통과해도 Gradle이 개수를 출력하지 않으므로 `build/test-results/test/TEST-*.xml`의 `tests/failures/errors`로 확인한다.

## 11. 출처와 한계
- **이 문서의 근거**: 이 저장소의 실제 코드(`infra/tmdb/`, `global/config/FeignConfig.java`, `application.yml`), 통과한 테스트, `docs/TASK041_GUIDE.md`.
- **안내서의 근거**: 공식 문서(Spring Cloud OpenFeign, Bucket4j, WireMock, Micrometer, TMDB `developer.themoviedb.org`)와 라이브러리 소스(`feign-core 13.6.1`, `spring-cloud-openfeign-core 5.0.3`, `bucket4j 8.21.0`)를 `backend-dev` 에이전트가 읽은 내용이다. 실행으로 확인한 것은 테스트가 통과한 범위뿐이다.
- **확인하지 못한 것**: TMDB가 429에 `Retry-After`를 실제로 보내는지, 실제 TMDB 응답의 한국어 줄거리가 `""`인지 `null`인지, discover의 페이지 상한 등은 실제 호출로만 알 수 있다(안내서 10절). `prefix`를 `"app.tmdb.*"`로 잘못 쓴 상태에서 테스트가 통과한 이유(8절 9번)도 확인하지 못했다(고친 뒤에는 통과를 확인했다).
