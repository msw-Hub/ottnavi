# 트러블슈팅: Redis 연결 실패와 서버를 켠 뒤 첫 요청이 느린 문제

> 이 문서는 사용자가 요청해 만든 **정리 문서**이고 근거 문서가 아니다(규칙의 근거는 PRD·ERD·TECH, 코드가 기준).
> 2026-11-01에 Grafana Cloud를 켜고 처음 관측한 내용을 시간 순서로 기록한다. 로그·수치는 실제로 본 값이고, 해석은 **추정**이라고 표시한다.
> 상태: **원인 미확정, 개선 진행 중**(브랜치 `feature/fix-cold-start-popular-detail`). 개선을 적용한 뒤 결과를 6절에 이어 쓴다.

## 0. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 증상 | 서버를 켠 뒤 처음 들어온 요청들이 7~20초 걸렸고, 같은 시각에 Redis 연결 실패 경고가 남았다 |
| 영향 | 비로그인 방문자는 요청 제한이 건너뛰어(fail-open) 막히지는 않지만 느리다. 홈 캐시 비우기도 한 번 실패했다 |
| 확인한 사실 | 05:41:39 요청 제한용 Redis 연결 생성이 8.3초 만에 실패, 같은 순간 캐시 비우기도 실패, CPU 약 99% |
| 원인(추정) | 가장 유력: 첫 요청과 인기 수집이 겹쳐 CPU가 포화되고, 서버가 **첫 사용 때 만드는** Redis 연결이 시간 제한을 넘겨 실패. 유휴 끊김·Redis 쪽 단절은 콘솔 확인으로 거의 배제 |
| 배제한 것 | Redis와의 거리(서울 리전), DB 지연, 타임아웃 설정 부족(이미 명령 2초·연결 5초) |
| 개선(이 PR) | 기동 직후 Redis 연결 미리 만들기(실패하면 30초 뒤 1회 재시도). keepalive 설정은 기본값이 이미 켜져 있어 제외, 끊긴 동안 명령 즉시 거절은 재발급·로그인 오류 처리 뒤 별도 작업 |

## 1. 문제 발생

### 배경
- 운영 구성: Vercel(프론트) → Cloudtype(Spring Boot, 메모리 1GB) → Supabase(PostgreSQL, 서울 풀러), Redis Cloud 무료 플랜(서울 `ap-northeast-2-1`).
- Redis 사용처: 공개 API 요청 제한(Bucket4j), 홈·작품 상세 캐시(`@Cacheable`), 로그인 토큰 재발급 저장.
- 이전 기록: `docs/HANDOFF.md`에 "Redis 연결 10초 타임아웃·유휴 뒤 fail-open 로그"가 이월돼 있었다. 빈도를 몰라서 **Grafana를 켠 뒤 `ratelimit.store_error`로 확인**하기로 했다. 첫 배포 때(Task 020)에는 "Redis Cloud 첫 호출이 11.5초 걸린 관측"도 `application.yml`에 주석으로 남아 있다.

### 일정
| 시각(KST) | 일 |
|---|---|
| 2026-11-01 03:48 전후 | 이전 배포(프로필 `prod`)를 24시간 측정해 메모리 기준값을 정함 |
| 03:52 | 사용자가 Grafana 환경 변수 5개와 프로필 `prod,observability`를 저장해 재배포 |
| 03:59:47 | 서버 기동 완료(`Started OttnaviApplication in 379.91 seconds`) |
| 05:40:56 | **부팅 후 첫 HTTP 요청** |
| 05:41:39 | Redis 연결 실패 경고 |

## 2. 문제 확인 (실제로 본 것)

### 2-1. 관측 도구 연결 확인
- 로그(Loki, 쿼리 `{app="ottnavi", env="prod"}`): 기동 로그 3줄이 도착했다.
```
2026-11-01 03:53:57.293 info [main] c.o.OttnaviApplication - Starting OttnaviApplication v0.0.1-SNAPSHOT using Java 17.0.11 with PID 11 ...
2026-11-01 03:53:57.589 info [main] c.o.OttnaviApplication - The following 2 profiles are active: "prod", "observability"
2026-11-01 03:59:47.890 info [main] c.o.OttnaviApplication - Started OttnaviApplication in 379.91 seconds (process running for 399.785)
```
- 지표(Prometheus): `jvm_*`, `hikaricp_*`, `process_cpu_usage`, `ottnavi_process_rss_bytes`, `ottnavi_container_memory_used_bytes`, `ratelimit_store_error_total`, `tmdb_call_total`, `http_server_requests_milliseconds_*`가 도착했다.
- 전송 실패 경고(`otlp|publish` 검색)는 없었다.

### 2-2. 부팅 후 약 100분간 요청이 없었다
서버 로그의 `Initializing Spring DispatcherServlet`은 **처음 요청이 들어올 때** 찍히는 줄이다.
```
05:40:56 INFO [nio-8080-exec-9] o.a.c.c.C.[Tomcat].[localhost].[/]  : Initializing Spring DispatcherServlet 'dispatcherServlet'
05:40:57 INFO [nio-8080-exec-9] o.s.web.servlet.DispatcherServlet   : Completed initialization in 305 ms
```
기동(03:59:47)과 첫 요청(05:40:56) 사이 약 100분 동안 HTTP 요청이 없었다. 그래서 이 구간의 메모리·CPU 지표는 "요청 없는 상태"의 값이다.

### 2-3. 05:41의 로그 (인기 수집과 Redis 경고)
```
05:41:02 INFO  [popularity-1] TaskExecutorJobLauncher : Job: [FlowJob: [name=popularityJob]] launched ...
05:41:02 INFO  [nio-8080-exec-9] c.ottnavi.home.job.PopularityLauncher : 인기 순위 수집을 시작했다: executionId=14
05:41:27 INFO  [popularity-1] c.o.h.service.PopularityCollectService : 인기 순위 수집 종료: 날짜=2026-11-01, 대상=14곳, 실패=0곳, 저장=206행
05:41:39 WARN  [nio-8080-exec-4] c.o.g.r.RedisRateLimitBucketProvider : 요청 제한용 Redis 연결 생성에 실패했다: 소요 ms=8297, 기동 후 경과 ms=6503189, 근본원인=io.netty.channel.StacklessClosedChannelException
05:41:39 WARN  [nio-8080-exec-4] c.o.global.ratelimit.PublicRateLimiter : 요청 제한 저장소 오류로 검사를 건너뜁니다(fail-open): RedisConnectionException, 근본원인=io.netty.channel.StacklessClosedChannelException
05:41:39 WARN  [popularity-1] c.ottnavi.home.service.HomeCacheEvictor : 홈 캐시를 비우지 못했다(TTL이 지나면 만료된다): cache=homePopularity, error=org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis
05:41:39 INFO  [popularity-1] ... : Job: [FlowJob: [name=popularityJob]] completed ... status: [COMPLETED] in 25s313ms
05:41:41 INFO  [nio-8080-exec-8] c.o.g.r.RedisRateLimitBucketProvider : 요청 제한용 Redis 연결을 만들었습니다: 소요 ms=1901, 기동 후 경과 ms=6511489
```
읽는 법:
- 두 번째 `Redis 연결 생성에 실패`는 이미 있던 연결이 끊긴 것이 아니라 **새 연결을 만드는 단계의 실패**다(요청 제한용 연결은 처음 쓰는 순간에 만들도록 설계돼 있다).
- 실패한 연결(요청 제한)과 캐시 비우기(인기 수집 스레드)는 서로 다른 경로인데 **같은 05:41:39**에 함께 실패했다.
- 2초 뒤에는 연결이 1.9초 만에 만들어졌다(성공해도 느렸다).
- 인기 수집 자체는 정상이다(14곳, 실패 0, 206행).

### 2-4. 지표
| 지표 | 값 | 비고 |
|---|---|---|
| `ratelimit_store_error_total{bucket="public"}` | 1 | 기동 후 첫 요청 구간에서 발생. 이후 최근 5분 증가량 0 |
| `process_cpu_usage` 04:01 / **05:43** / 06:03 | 0.993 / **0.987** / 0.281 | 기동 직후와 첫 요청·인기 수집이 겹친 순간 CPU가 거의 100% |
| 전체 평균 응답 시간 05:44(최근 5분) | 약 2.16초 | |
| DB 연결 풀 | 연결 5(대기 5·사용 0·기다림 0), 타임아웃 0, 획득 최대 6.8ms, 사용 최대 98ms | DB는 막히지 않았다 |

기동 후 누적 경로별 평균 응답 시간(밀리초, 첫 요청 구간이 섞여 있다):

| 경로 | 평균 |
|---|---|
| `/api/public/wishlisted-titles`(1건) | 20,898 |
| `/api/public/newly-available-titles` | 17,703 |
| `/api/public/popularity-rankings` | 12,352 |
| `/api/admin/popularity/executions`(202) | 8,891 |
| `/api/auth/refresh` | 7,038 |
| `/api/me/plans/current` | 4,541 |
| `/api/public/ott-services` | 4,349 |
| `/api/me/wishlist-items` | 2,954 |

이후 서버가 데워진 뒤에는 최근 5분 상위 경로가 1.4~2.5초, **브라우저(F12 Network)에서 잰 가장 느린 요청은 0.5~0.8초**였다(`/api/me/plans/current` 814ms, `/api/public/ott-services/1/exclusive-titles` 565ms, `/api/me/wishlist-items` 523ms).

### 2-5. Redis Cloud 콘솔 (2026-11-01 확인)
콘솔 그래프의 시각은 UTC로 보인다(그래프의 "Sat 20:38"은 KST 05:38. 서버 로그 시각과 맞는다).
- 한도: 연결 최대 **30개**. 메모리 7.2 / 30MB, 네트워크 354.9MB / 5GB(기간 기준은 미확인), 리전 `ap-northeast-2-1`(서울).
- **연결 수(Connections)**: 어제 23:00(UTC)부터 약 20시간 동안 **계속 2개**를 유지하다가 UTC 19:00 무렵(KST 04:00, 서버 재배포 시각)에 **0개로 떨어졌고**, UTC 20:40 무렵(KST 05:40, 첫 요청 시각)에 다시 2개가 됐다. 최소 0, 평균 1.86, 최대 2.
- **지연(Latency)**: 최대 0.12ms, 평균 0.01ms. 05:41 전후에도 스파이크가 없다. **초당 명령(Ops/sec)**: 평균 약 0.01, 최대 0.53(05:40 무렵 첫 요청이 몰린 구간).
- **활동 로그**: 05:41 전후(UTC 10월 31일 20:41)에 점검·설정 변경 기록이 없다. 가장 가까운 점검은 10월 28일(UTC 15:06~16:11). 현재 데이터베이스 설정은 데이터 영속화 끔, RESP3 켬, 제거 정책 `allkeys-lru`이다(접속 허용 범위 등 보안 설정은 이 문서에 적지 않는다).

## 3. 원인 분석

### 3-1. 코드로 확인한 사실
| 확인 | 내용 |
|---|---|
| 타임아웃 | `application.yml`: 명령 `timeout: 2s`, `connect-timeout: 5s`. HANDOFF의 "10초"는 옛 표현이다 |
| 요청 제한 연결 | `RedisRateLimitBucketProvider`가 **처음 쓸 때** 연결을 만든다(기동 때 만들면 Redis가 죽어 있을 때 앱이 못 뜨기 때문). 실패하면 다음 호출에서 다시 시도한다 |
| 보호 장치 | 저장소 오류가 나면 일정 시간 Redis를 건너뛰는 `FailureWindow`와 fail-open이 이미 있다 |
| Redis 접속 | 운영은 Redis Cloud 무료 플랜 평문(TLS 없음) 접속 |
| 설정 한도 | 연결 5초인데 실제로는 8.3초가 걸렸다(타이머가 늦게 동작한 정황) |

### 3-2. 원인 후보와 근거
| 후보 | 근거 | 반증·한계 |
|---|---|---|
| (a) 유휴 연결을 중간 네트워크 장비가 끊음 | 부팅 후 100분간 요청이 없었다. 클라우드의 NAT·로드밸런서는 유휴 TCP를 끊기도 한다(제3자 글) | **거의 배제.** Redis Cloud 연결 수는 05:41 이전에 **0개**였다(끊긴 연결이 아니라 연결 자체가 없었다). 이전 프로세스는 같은 연결 2개를 약 20시간 유휴로 유지했다. 설치된 Lettuce 7.5.2는 TCP keepalive가 기본 켜짐(유휴 13초) |
| (b) 서버 CPU 포화로 연결·타이머 지연 | 05:43 CPU 약 99%, 5초 제한이 8.3초로 늘어남, 첫 요청과 인기 수집(25초)이 같은 시각, vCPU를 구독하지 않은 작은 컨테이너. Redis 연결은 **첫 요청 때 처음 만들어졌다**(콘솔 연결 수 0 → 2) | 인과 증명은 못 했다. 같은 시점의 다른 요인과 구분하지 못했다 |
| (c) Redis 쪽 일시 단절 | 서로 다른 두 경로가 같은 순간 실패, 직후 2초 뒤에는 연결 성공 | **약함.** 같은 시각 Redis 지연 최대 0.12ms, 점검·설정 변경 기록 없음 |

### 3-3. 배제한 가설
| 가설 | 판단 | 근거 |
|---|---|---|
| Redis가 멀어서 느림 | 배제 | Redis Cloud 리전이 서울 |
| DB가 느려서 느림 | 배제(주원인 아님) | 연결 사용 최대 98ms, 획득 최대 6.8ms. 다만 데워진 뒤 평균 사용은 약 271ms로 요청 시간의 큰 몫이다(5-3 참고) |
| 타임아웃이 너무 길다 | 배제 | 이미 명령 2초·연결 5초 |
| 연결 수 부족(상한 30) | 배제 | 사용 연결은 최대 2개 |
| Redis 용량·트래픽 부족 | 배제 | 메모리 7.2/30MB, 네트워크 354.9MB/5GB |
| Grafana가 일으킨 문제 | 배제에 가까움 | 이 문제는 Grafana 이전(HANDOFF 이월, Task 020 관측)부터 있었다. 메모리도 켠 뒤 눈에 띄게 늘지 않았다 |

### 3-4. 현재까지의 해석 (추정)
부팅 후 서버는 Redis 연결을 **첫 사용 때** 만드는데, 부팅 후 100분간 요청이 없어 Redis 연결이 0개였다(콘솔 확인). 05:41에 첫 요청과 인기 수집이 동시에 들어오면서 JVM이 처음 쓰는 코드를 로딩·컴파일하느라 CPU가 포화됐고, 그 사이 연결을 처음 맺는 작업이 시간 제한을 넘겨 실패했다(추정). 느린 요청들은 이 구간에서 생겼고, 서버가 데워진 뒤에는 0.5~0.8초로 안정됐다. 유휴 끊김(a)과 Redis 쪽 단절(c)은 근거가 약하다. 따라서 **기동 직후 연결을 미리 만들어 두는 것(예열)이 실질적인 개선 수단**이다.

## 4. 시도한 것

| 시도 | 결과 |
|---|---|
| Grafana Cloud 켜기(로그 Loki, 지표 Prometheus) | 로그·지표 도착 확인. 환경 변수 5개와 프로필 `prod,observability`를 한 번에 저장해 재배포(기동 379.91초) |
| 24시간 메모리 기준값 확정 | 켠 뒤 메모리가 늘지 않음. 약 1시간 423.64MB(이전 배포 같은 시점 504.43MB) |
| 연결 실패 시각·빈도 확인 | `ratelimit_store_error_total`=1, 05:41:39 단 한 번 |
| 지연 요청 분리(DB 아님, CPU 포화 확인) | Hikari 지표와 `process_cpu_usage`로 판단 |
| 재방문 측정 | 데워진 뒤 0.5~0.8초 |
| Lettuce 공식 문서 확인(context7) | 6.x 문서에는 "기본 keepalive 없음"이라고 돼 있었으나 **설치된 7.5.2 소스는 기본이 이미 켜짐**(`DEFAULT_SO_KEEPALIVE = true`, 확장 유휴 13초·간격 13초·3회, Java 17 NIO에서 적용)임을 구현 단계에서 소스로 확인했다(문서 버전 차이로 초기 서술이 틀렸음). 확장 keepalive는 Java 11+ NIO에서 가능, 끊긴 동안 명령은 기본적으로 큐에 쌓였다가 재전송(`REJECT_COMMANDS`로 거절 가능) |
| 검색 | Redis 서버 기본은 유휴 연결을 먼저 끊지 않는다. 끊김은 중간 장비 때문인 경우가 많다. Redis Cloud 고유 값은 못 찾음 |
| Redis Cloud 콘솔 확인 | 연결 상한 30·실사용 2개, 05:41 이전 연결 0개, 지연 0.12ms 이하, 점검 기록 없음(2-5) |

## 5. 해결 방향과 미해결

### 5-1. 개선 후보 (이 PR에서 구현)
| # | 변경 | 기대 효과(원인 후보) | 위험 |
|---|---|---|---|
| 1 | 기동 직후 백그라운드로 Redis 연결을 미리 만들기. 실패하면 설정한 시간(기본 30초) 뒤 실패한 쪽만 **1회** 다시 시도, 그래도 실패하면 로그만 남김 | 첫 방문자가 연결 생성을 기다리지 않음 (b) | 낮음 |
| 2 | ~~Lettuce keepalive 설정~~ (**제외 제안**) | 설치된 Lettuce 7.5.2는 기본으로 이미 켜져 있고(유휴 13초) 이전 프로세스가 유휴 연결을 약 20시간 유지했으므로 효과가 없다 | 코드만 늘어남 |
| 3 | ~~끊긴 동안 명령 즉시 거절(`REJECT_COMMANDS`)~~ (**이번 PR에서 제외**) | 재연결 중 요청이 Redis를 기다리지 않음 | 점검 결과: 캐시·요청 제한·캐시 비우기·방문자 통계는 오류 처리가 있어 안전하지만, **토큰 재발급(`AuthTokenService.refresh`)·로그아웃·데모 로그인은 catch가 없어 500**이 되고 즉시 거절은 이 500을 재연결의 짧은 순간까지 넓힌다. 오류 처리(503 등 새 오류 코드, 계약·프론트 확인)를 먼저 고쳐야 하는 별도 작업이다 |
- 앱이 직접 주기적으로 PING을 보내는 방식은 2번과 같은 일이라 생략한다.
- keepalive 값(60초 등)은 기본값(13초)이 이미 더 촘촘해 바꾸지 않는다.

### 5-2. 성공 기준 (적용 후 같은 방법으로 측정)
1. 서버 재시작이나 유휴 뒤 **첫 요청 응답이 수 초 이내**(20초대가 다시 나오지 않음).
2. `ratelimit_store_error_total` 증가가 거의 없음.
3. Loki에서 "Redis 연결 생성에 실패 / Unable to connect" 경고가 줄어듦.
4. 메모리·CPU에 눈에 띄는 증가가 없음(중단 기준: 메모리 RSS +100MB 또는 컨테이너 85%).

### 5-3. 미해결·모르는 것
- 원인 (b)가 맞는지(CPU 포화가 연결 실패를 일으켰는지)는 개선 적용 뒤 첫 요청 응답 시간과 경고 로그로 확인해야 한다. 예열이 CPU가 바쁜 기동 직후에 돌기 때문에 첫 시도가 실패할 수 있어 재시도를 둔다.
- (해결) Redis Cloud 쪽 이벤트는 활동 로그·지연·연결 수로 확인했다(2-5). 유휴 연결 제한 값은 모르지만 유휴 연결이 약 20시간 유지돼 문제가 되지 않는 것으로 본다.
- 문제의 실제 빈도(기동 후 약 100분간 1회만 관측).
- 데워진 뒤에도 DB 연결 평균 사용 시간이 약 271ms이고 일부 사용자별 API가 0.5~0.8초다. 이번 수정의 범위는 아니고, 쿼리 수 점검은 별도 과제로 둔다.
- 서버가 얼마나 CPU를 쓰는지(수집과 사용자 요청이 겹치는 경우의 대책: 예열, 수집 시각 조정 등).

## 6. 적용 결과
(개선을 적용하고 운영에서 측정한 뒤 이어 쓴다.)

## 7. 같이 발견한 문제: 인기 1위인데 상세 정보가 없음
05:42에 `tv/289517`을 열었더니 홈 인기 1위인데 상세가 404였다.
```
05:42:21 WARN [io-8080-exec-10] c.o.global.error.GlobalExceptionHandler : 요청 처리 실패: errorCode=TITLE_NOT_FOUND, status=404, path=/api/public/titles/tv/289517, exception=TitleNotFoundException
05:42:25 WARN [nio-8080-exec-9] c.o.global.error.GlobalExceptionHandler : 요청 처리 실패: errorCode=TITLE_NOT_FOUND, status=404, path=/api/public/titles/tv/289517, exception=TitleNotFoundException
```
- 원인: 홈 순위는 `popularity_snapshot`(제목·포스터만 저장, `title` 표에 없는 작품도 보이도록 한 설계, V9 주석)에서 만들고, 상세 API(`TitleDetailQueryService.getDetail`)는 `title` 표에서만 찾는다. 신규 작품은 주간(WEEKLY) 수집의 후보 발견에서만 `title`에 들어온다(HANDOFF Task 077 이월: "최대 주 1회 반영"). 설명 전체는 이 문서가 아니라 설계 기록을 따른다.
- 해결(구현함, 백엔드): 인기 수집 Job(`popularityJob`)이 Step 3개(수집 `popularityCollectStep` → **상세 채우기 `popularityDetailFillStep`** → 스냅샷 삭제 `popularityPurgeStep`)로 바뀌었다. 앞 Step이 실패해도 다음 Step으로 넘어간다.
  - 대상: 순위 16개(기간 TODAY·WEEK × 전체+서비스 7곳)의 각 상위 20위(`app.popularity.fill.top-n`)를 전체-오늘, 전체-7일, 서비스1-오늘, … 순서로 합쳐 중복을 없애고 `title`에 이미 있는 작품은 뺀다. 한 번에 최대 20작품(`app.popularity.fill.max-titles`)이다. 후보는 캐시(`homePopularity`)를 거치지 않고 방금 쌓은 스냅샷으로 계산한다.
  - 저장: 작품마다 TMDB 호출(상세·시즌·제공처)을 **트랜잭션 밖에서** 끝낸 뒤, `title`·장르·출연진·시청 단위·제공 상태·제공 상태 이력을 **작품 하나당 트랜잭션 하나**로 저장한다. 작품은 있는데 제공 상태가 없는 중간 상태가 보이지 않는다.
  - 실패: TMDB 오류나 동시 삽입이 난 작품만 건너뛰고 다른 작품은 계속한다. TMDB에 작품이 없으면(404) 건너뛴다. 작품 제공처가 404면 작품은 저장하고 그 시청 단위의 제공 상태를 "모름"(KR 데이터 없음)으로 저장한다.
  - 관측: 끝에 로그 한 줄 `인기 작품 상세 채우기 종료: 후보=…, 이미 있음=…, 채움=…, TMDB에 없음=…, 실패=…, 상한으로 제외=…`를 남긴다(Loki `|~ "인기 작품 상세 채우기"`).
  - 한계: 반영은 **다음 인기 수집 때**다(요청 때 즉시 수집하는 단건 수집은 아니다). 상한을 넘은 작품은 다음 수집 때 채운다. 인기 수집이 끝나면 홈 캐시 5개를 모두 비운다. 관리자 화면에서 인기 수집을 수동 실행하면 바로 채워진다.
  - 상세 화면의 "상세 정보를 모으는 중" 문구는 프론트 작업이라 이번 백엔드 변경에 포함되지 않았다.
- 이 개선은 Redis 문제와 별개지만 같은 PR 브랜치에서 커밋을 나눠 진행한다.

## 8. 배운 것과 운영 메모
1. 지표는 일이 처음 생길 때 만들어진다. 어떤 지표가 안 보이면 오류가 아니라 **그 일이 아직 안 일어난 것**일 수 있다(`ratelimit_store_error_total`, `tmdb_call_total`, `http_server_requests_*`).
2. 서버 로그의 `Initializing Spring DispatcherServlet` 시각은 **부팅 후 첫 요청 시각**이다. 평균 지연을 볼 때 이 시각 이전은 요청이 없던 구간이다.
3. 누적 평균은 첫 요청 구간 때문에 높게 나온다. 현재 상태를 보려면 최근 5분 `rate`로 본다.
4. 새 연결 생성 실패와 유휴 연결 끊김은 다른 문제다. 로그 문구(`연결 생성에 실패` vs `Unable to connect`)로 구분해 본다.
5. 인기 수집·일간 수집이 도는 시간대는 CPU가 포화될 수 있다. 서버 재배포와 수집 시각이 겹치지 않게 한다(기존 KST 06:30~09:00 회피 규칙).

## 부록: 사용한 쿼리
Loki(로그): `{app="ottnavi", env="prod"}`, `{app="ottnavi", env="prod", level=~"(?i)warn|error"}`, `{app="ottnavi", env="prod"} |~ "(?i)otlp|publish"`

Prometheus(지표):
```
ottnavi_process_rss_bytes
ottnavi_container_memory_used_bytes
process_cpu_usage
increase(ratelimit_store_error_total[5m])
sum(rate(http_server_requests_milliseconds_sum[5m])) / sum(rate(http_server_requests_milliseconds_count[5m]))
topk(5, sum by (uri) (rate(http_server_requests_milliseconds_sum[5m])) / sum by (uri) (rate(http_server_requests_milliseconds_count[5m])))
sum(rate(hikaricp_connections_usage_milliseconds_sum[5m])) / sum(rate(hikaricp_connections_usage_milliseconds_count[5m]))
```

## 참고 자료
- Lettuce `SocketOptions`(keepalive): https://github.com/redis/lettuce/blob/main/src/main/java/io/lettuce/core/SocketOptions.java
- Lettuce `ClientOptions`(`autoReconnect`, `disconnectedBehavior`): https://github.com/redis/lettuce/blob/main/docs/advanced-usage/client-options.md
- Redis 클라이언트 처리: https://redis.io/docs/latest/develop/reference/clients/
- (제3자) Redis TCP keepalive 설정 글: https://oneuptime.com/blog/post/2026-03-31-redis-tcp-keepalive-config/view
