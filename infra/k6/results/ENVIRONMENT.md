# k6 측정 환경 기록 (Task 100)

측정일: 2026-11-02~03. 측정 시작 21:15(준비 확인), 본 측정 21:42 ~ 마지막 측정 종료 2026-11-03 00:07:12, 최종 확인 00:07:47. 시각은 모두 KST다.
C2(512MB) 판정은 사용자 결정(구독 메모리 1GB 확정)으로 하지 않았다. 측정은 모두 C1(1GiB, CPU 1, `-Xmx512m`)이다. 수집(Spring Batch)은 측정에 포함하지 않았다.

## 1. PC와 Docker

| 항목 | 값 | 근거 |
|---|---|---|
| PC | AMD Ryzen 5 5600X 6코어 12스레드, 메모리 약 32GB, Windows 11 | 사용자가 알려 준 값 |
| Docker Desktop 할당 | CPU 12개, 메모리 16,728,121,344바이트(약 15.6GiB) | `docker info` (NCPU, MemTotal) |
| Docker 엔진 | 28.3.2 | `docker info` (ServerVersion) |
| 디스크 여유 | C: 476G 중 44G 남음(91% 사용) | `df -h /c` (23:05) |
| 간섭 | 22:02 전후~22:18 다른 프로그램 업데이트로 PC CPU 급증·불안정(사용자 보고). 이 구간 회차는 RUN_LOG에 "PC 부하 흔적 의심"으로 표시하고 다시 쟀다 | 사용자 보고 + 데이터 판별 |

## 2. 측정 대상 컨테이너

| 항목 | C1(기본) | 근거 |
|---|---|---|
| 컨테이너 | `ottnavi-backend-perf-1`, 이미지 `ottnavi-backend-perf`(2026-11-02 21:02 빌드, `eclipse-temurin:17-jre` + `backend-0.0.1-SNAPSHOT.jar` 21:00:56) | `docker image inspect`, `infra/k6/Dockerfile.backend-perf` |
| 메모리 상한 | 1GiB(1,073,741,824), 스왑 없음(memswap 같은 값) | `docker inspect` HostConfig.Memory/MemorySwap |
| CPU 상한 | 1.0(NanoCpus 1,000,000,000) | `docker inspect` |
| JAVA_TOOL_OPTIONS | `-Xmx512m`(힙 상한 실제 495MiB, Serial GC가 자동 선택됨: 메모리 풀 이름 Eden Space/Tenured Gen) | 컨테이너 환경 변수, `/api/admin/system-metrics` |
| 프로필 | `local`(개발 JWT 키, 오리진 비밀 헤더 검사 꺼짐), observability 꺼짐 | 컴포즈 환경 변수 |
| 요청 제한 | 꺼짐(`SPRING_APPLICATION_JSON={"app":{"rate-limit":{"enabled":false}}}`) | 컴포즈 환경 변수 |
| DB 연결 풀 | HikariCP 최대 5(운영과 같음) | `application.yml` 9줄 |
| actuator | health만 노출("Exposing 1 endpoint") | 기동 로그 |
| Spring Boot / Java | Spring Boot 4.1.1, Java 17.0.20.1 | 기동 로그 |
| 기동 시간 | 약 27.7~28.2초 | 기동 로그 "Started OttnaviApplication in …" |
| 유휴 메모리 | 약 435~500MiB(docker stats) | `docker stats` |
| 소스 | 브랜치 `feature/q2-k6-performance`, 커밋 `5c60066502b5e514f7816924b5b94ed9d2a13dcc`(2026-11-02 20:31). `backend/` 소스는 이 커밋에서 바뀐 것이 없고 jar는 커밋 뒤(21:00)에 빌드됐다 | `git rev-parse HEAD`, `git status` |

측정 종료 뒤(00:07:47) `docker inspect`로 C1 설정이 그대로인 것을 확인했다(OOMKilled=false, RestartCount=0).

## 3. 주변 컨테이너와 도구

| 항목 | 값 |
|---|---|
| DB | `ottnavi-postgres-1`(postgres:17.6-alpine, PostgreSQL 17.6), 측정 DB `ottnavi_perf` 192MB. 개발 DB `ottnavi`와 같은 컨테이너지만 측정은 `ottnavi_perf`에만 붙는다 |
| Redis | `ottnavi-redis-perf-1`(redis:8.6-alpine, 저장 없음). 회차마다 `FLUSHALL` |
| k6 | `grafana/k6:1.0.0`(이미지 생성 2025-05-06), 컴포즈 서비스 `k6`, `BASE_URL=http://backend-perf:8080`. k6 컨테이너에는 CPU·메모리 상한이 없다 |

## 4. 데이터(측정 DB `ottnavi_perf`, 23:00 기준)

| 표 | 행 수 | 비고 |
|---|---|---|
| title | 33,004 | 운영 33,192(사용자 확인, 약 0.6% 차이) |
| watch_unit | 39,833 | 운영 40,018 |
| availability | 278,831 | 운영 280,126 |
| users | 50 | 측정용 `perf-user-1~50@example.invalid`, role USER |
| user_setting | 50 | `prepare-users.js` |
| wishlist_item | 580 | 사용자 1~40·49~50 각 10개, 41~48 각 20개 |
| popularity_snapshot | 1,536 | **합성 시드**(`seed-perf-popularity.sql`, 8일 × 192행) |
| subscription_product | 7 | 단품만, 번들 없음 |
| plan | 측정 중 계속 바뀜(초안 덮어쓰기) | |

측정 사용자 구성:
- 1~40: 평상시 계산(1곳 SUBSCRIBED, 예산 20,000~30,000원 + 측정마다 작은 변화, 월 900분 NORMAL, 찜 10개 MUST 3·WANT 4·MAYBE 3)
- 41~44: 최악 입력(찜 20개 모두 MUST, 7곳 NOT_SUBSCRIBED, 예산 100,000원 + 작은 변화)
- 45~48: 최악 입력(같은 찜 20개, 우선순위 MUST·WANT·MAYBE 반복)
- 49~50: 예비(평상시 구성)
- 토큰: k6가 HS256으로 직접 서명(`lib/auth.js`), 메모리 지표 표본만 ADMIN 역할

## 5. 운영과 다른 점(해석 주의)

| 항목 | 운영(Cloudtype) | 측정 |
|---|---|---|
| CPU | 구독 없음, 기동 380~414초 | 1코어 고정, 기동 약 28초 |
| DB | Supabase(네트워크 너머, 풀 5) | 같은 Docker 네트워크(지연 거의 없음, 풀 5) |
| Redis | Redis Cloud | 컨테이너 Redis |
| 요청 제한 | 켜짐(공개 API IP당 분당 60회) | 꺼짐 |
| 관측 | 지표·로그·추적 전송 | 꺼짐 |
| 인기 순위 데이터 | 실제 TMDB 수집 | 합성 시드 |
