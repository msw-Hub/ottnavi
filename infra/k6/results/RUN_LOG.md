# k6 측정 진행 기록 (Task 100)

한 줄에 시각(KST) · 명령 · 결과를 쓴다. 원본 요약 JSON은 같은 폴더에 있다.

| 시각 | 명령 | 결과 |
|---|---|---|
| 2026-11-02 21:15 | `docker compose -f infra/docker-compose.yml --profile perf ps`, `curl -s http://127.0.0.1:8081/actuator/health` | 4개 컨테이너 healthy, health UP. backend-perf 448.5MiB/1GiB, OOMKilled=false, RestartCount=0 |
| 2026-11-02 21:16 | `MSYS_NO_PATHCONV=1 docker compose -f infra/docker-compose.yml --profile perf run --rm --no-deps k6 run --summary-export /scripts/results/20261102-smoke-token.json /scripts/smoke-token.js` | checks 7/7. 서명 토큰 200, 망가진 서명 401, 설정 없는 계산 400 VALIDATION_FAILED(field monthlyBudget). `__ENV.BASE_URL`·`__ENV.JWT_SECRET` 읽힘(플래그 없이). 첫 실행 med 135.9ms(데워지기 전) |
| 2026-11-02 21:17 | 같은 명령(ADMIN 토큰으로 `/api/admin/system-metrics` 확인 추가, `summaryTrendStats`에 p(99) 추가) | checks 8/8, 요청 med 7.9ms·p(99) 25.5ms. 메모리 지표 200: 힙 used 104MiB/max 495MiB, RSS 462MiB, 메모리 풀 이름이 Eden/Tenured라 Serial GC로 보인다 |
| 2026-11-02 21:33 | `docker compose … exec -T postgres psql -U ottnavi -d ottnavi_perf -v ON_ERROR_STOP=1 -f - < infra/k6/seed-perf-popularity.sql` | 합성 인기 스냅샷 1,536행(2026-10-26~11-02 8일 × 192행/일, 서비스 1~6 × 매체 2 × 15개 + 쿠팡 TV 12개) |
| 2026-11-02 21:34 | psql 조회로 `data/wishlist-pool.json`(300), `detail-miss-keys.json`(3000), `detail-hit-keys.json`(50), `search-terms.json`(30), node로 `worst-wishlist.json`(20, 7개 서비스 모두 덮음) 생성 | 완료 |
| 2026-11-02 21:34 | `k6 run /scripts/prepare-users.js` | checks 630/630. user_setting 50, wishlist_item 580(사용자 1~40·49~50 각 10개, 41~48 각 20개) |
| 2026-11-02 21:35 | `-e MODE=variants k6 run … /scripts/plan.js`(→ `20261102-2135_variants_C1.json`) | W1 4.73/3.65s, W2 2.92/2.67s, W3 3.90/4.21s, W4 3.42/3.50s. 모두 EXACT, 절약형·간편형 후보 2,097,152, 추천형 64,000(Redis planCalc 값). 힙 최대 130MB, GC 203→1592회 |
| 2026-11-02 21:37 | 같은 명령 다시(캐시 비우지 않음) | 8건 모두 캐시 히트 97~162ms(같은 예산 변화라 같은 해시) → 캐시 히트 경로 확인. S5 입력은 W3로 정함 |
| 2026-11-02 21:42 | `bash infra/k6/run-measure.sh read-baseline.js read_C1_r1 440` | `up -d --no-deps --wait`가 backend-perf를 한 번 재생성함(설정 같음: 1g·CPU 1·-Xmx512m 확인). 첫 요청 ott-services 376 / search 311 / detail 133 / popularity 190 / plan 297ms. search med 73.3 p95 88.2 p99 124.5 max 203.9, detail-miss med 7.5 p95 9.2, detail-hit med 2.2 p95 3.1, popularity med 2.1 p95 2.7, 오류 0%. 힙 최대 122MB, RSS 494MB, cgroup 564MB, docker CPU 최대 50%, OOM 없음 |
| 2026-11-02 21:50:53~21:58:15 | `run-measure.sh read-baseline.js read_C1_r2 440` (본 측정 약 21:51:40~21:58:13) | search med 71.7 p95 81.4 p99 86.7 max 117.1, detail-miss med 6.6 p95 8.2, detail-hit med 2.3 p95 3.1, popularity med 2.1 p95 2.8, 오류 0%. 힙 124MB, RSS 504MB, CPU 최대 52%, OOM 없음 |
| 2026-11-02 21:58:15~22:05:39 | `run-measure.sh read-baseline.js read_C1_r3 440` (본 측정 약 21:59:05~22:05:36: S1 ~22:01:05, S2a 22:01:15~22:02:15, S2b 22:02:25~22:03:25, S3 22:03:35~22:05:35) | **PC 부하 흔적 의심(22:02 사용자 PC CPU 급증과 S2a 끝·S2b가 겹침)**. detail-hit p95 24.2 p99 306.5 max 572.2(r1·r2는 p95 3.1, max 9 안팎), detail-miss p95 10.7(r1·r2 8.2~9.2), docker CPU 최대 70%(r1·r2 50~52%). search·popularity는 r1·r2와 비슷. 중앙값 계산에서 빼고 r4로 다시 잼 |
| 2026-11-02 22:06:05~22:13:41 | `run-measure.sh read-baseline.js read_C1_r4 440` (r3 대체, 본 측정 약 22:07:05~22:13:38) | search med 78.1 p95 92.4 p99 110.9 max 168.6, detail-miss med 8.2 p95 13.3, detail-hit med 2.8 p95 3.9, popularity med 2.7 p95 3.5, 오류 0%. CPU 최대 44%, OOM 없음. r1·r2보다 전반적으로 10~25% 느림(사용자 PC가 50%대로 바쁜 상태에서 잼, 부하 흔적 약간 의심) |
| — | 안정 판정 기준 | 같은 시나리오 다른 회차의 중앙값보다 med 또는 p95가 **+25% 넘게, 그리고 +2ms 넘게** 느리면 불안정(간섭 의심). 빠른 쪽으로 벗어난 것은 표본이 적어 생긴 흔들림으로 보고 재측정 대상에서 뺀다. 보조로 docker CPU 최댓값·sampler GC가 다른 회차와 크게 다른지 본다 |
| 2026-11-02 22:13:58~22:18:17 | `run-measure.sh plan.js s4miss_C1_r1 230 -e MODE=typical -e CACHE=miss` | **PC 부하 흔적 의심(22:02~22:18 간섭 구간 안)**. plan-calc med 42.9 p95 99.0(r2·r3 대비 +31%) max 406.9, PUT p95 17.5(+50%), docker CPU 최대 100%(r2·r3 47~56%), 워밍업 최악 입력 5.8초(다른 회 3.8~4.4초). → r4로 다시 잼 |
| 2026-11-02 22:18:17~22:22:20 | 같은 명령 r2 | plan-calc med 38.9 p95 60.3 p99 117.4 max 227.5, PUT med 8.3, 오류 0%, 힙 129MB, OOM 없음 |
| 2026-11-02 22:22:20~22:26:15 | 같은 명령 r3 | plan-calc med 39.3 p95 75.8 p99 142.8 max 284.8. 서버 로그로 캐시 미스 342/342 확인, 서버 측 계산 med 37ms |
| 2026-11-02 22:26:15~22:31:58 | `… -e MODE=typical -e CACHE=hit` r1·r2·r3 | med 40.7/42.3/40.9, p95 93.3/61.7/93.9, 오류 0%. 서버 로그로 캐시 미스 0건 확인(r3). r2 p95가 낮은 것은 빠른 쪽 흔들림이라 재측정하지 않음 |
| 2026-11-02 22:31:58~22:43:40 | `… -e MODE=worst -e VARIANT=W3` r1·r2·r3 | med 3627/3685/3754ms, p95 3814/4045/3984, max 3822/4058/4009. 엔진 3종 합계 3.4~3.85초(Redis 값), 후보 추천형 64,000·절약형·간편형 2,097,152, 모두 EXACT. CPU 최대 102~106%, 힙 최대 103~120MB, GC 1113~1177회/1.4~1.5초, OOM 없음 |
| 22:44 판정 | 읽기 | 간섭 구간에 걸린 r3(S2b 불안정)·r4(S2a p95 +45%·+4.1ms 불안정)를 빼고 r5를 새로 잼 → 최종 중앙값은 r1·r2·r5 |
| 2026-11-02 22:44:46~22:52:19 | `run-measure.sh read-baseline.js read_C1_r5 440` (재측정) | search med 75.1 p95 84.4, detail-miss med 6.6 p95 8.4, detail-hit med 2.3 p95 3.1(max 186.9 한 건), popularity med 2.2 p95 2.9, 오류 0%. CPU 최대 51%, OOM 없음 → 안정 |
| 2026-11-02 22:52:19~22:56:12 | `run-measure.sh plan.js s4miss_C1_r4 230 -e MODE=typical -e CACHE=miss` (재측정) | plan-calc med 39.3 p95 98.3 p99 188.9 max 285.1, PUT p95 12.4, CPU 최대 64%. p95가 r1(99.0)과 비슷해 S4 p95는 원래 60~99ms로 흔들리는 값으로 봄. r1은 PUT p95·CPU 최대·워밍업 시간이 함께 튀어 간섭 의심을 유지하고, 최종은 r2·r3·r4 |
| 2026-11-02 22:57 | `EXPLAIN ANALYZE`(ottnavi_perf, 검색 '사랑') | 목록·count 모두 title Seq Scan(32,364행 걸러냄), 49ms + 32ms |
| 2026-11-02 22:57 | `docker inspect ottnavi-backend-perf-1` | C1 그대로(1g·CPU 1), healthy, OOMKilled=false, RestartCount=0 |
| 2026-11-02 23:00 | `stress.js` 수정, `run-measure.sh`에 `K6_CSV=1`(gzip CSV 시계열)과 docker stats 줄 시각(awk `systime()`) 추가, `stages.cjs`(단계별 표) 추가 | 계산 사용자 배정: plan 모드는 VU n → 사용자 n(최대 32 VU라 1~40에서 겹치지 않음), mixed 모드는 계산 순번 % 40 + 1, 예산 변화는 0~999원 안. `k6 inspect` 4개 모드 통과 |
| 2026-11-02 23:06:59~23:09:33 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6smoke_mixed_C1 120 -e MODE=mixed -e SMOKE=1` (C1) | 5→20 req/s 1분, 오류 0%, plan-calc 75건 med 48ms, search med 75ms. CSV 93KB, 단계 표 동작 확인 |
| 2026-11-02 23:09:33~23:11:57 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6smoke_plan_C1 120 -e MODE=plan -e SMOKE=1` (C1) | 1→2 VU, plan-calc 802건 med 63ms p95 99ms, 8.9건/s, **2 VU에서 이미 CPU 100%(바쁜 표본 중앙 99.95%)** |
| 2026-11-02 23:15 | 결정 변경 기록 | C2(512m) 판정은 하지 않는다(사용자 결정: 구독 메모리는 1GB 확정, 남은 질문은 1GB 유지 대 1.5GB). 대신 S6 단계별로 메모리 여유(1GiB까지 MiB)·RSS·힙·GC 비중·스레드를 기록한다. `stages.cjs`에 이 열을 추가했다 |
| 2026-11-02 23:12:18~23:17:40 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6read_C1_r1 420 -e MODE=read` (C1) | **160 req/s 단계에서 중단**(읽기 p95>1초 abortOnFail, 시작 +266초). 80 req/s: p95 116ms, 오류 0%. 160: 실제 48.5 req/s, search p95 2,574ms, dropped 115, VU 160, CPU 최대 57%·중앙 38%, 컨테이너 메모리 최대 533MiB(1GiB까지 491MiB), 스레드 182. OOM 없음 |
| 2026-11-02 23:17:40~23:23:17 | 같은 명령 r2 | 160 단계 중단(+270초). 80: p95 89.6ms. 160: 실제 63.5 req/s, search p95 1,609ms, dropped 58, CPU 최대 63%, 메모리 최대 519MiB, 스레드 111 |
| 2026-11-02 23:23:17~23:28:45 | 같은 명령 r3 | 160 단계 중단(+262초). 80: p95 107.8ms. 160: 실제 33.8 req/s, search p95 3,281ms, dropped 147, CPU 최대 68%, 메모리 최대 541MiB, 스레드 211, GC 비중 2.1%. 세 회 모두 같은 단계에서 멈춰 안정(간섭 흔적 없음) |
| 2026-11-02 23:29 | `stress.js`에 `RATES` 환경 변수(도착률 단계 지정) 추가 | 한계 근처 세분 측정용 |
| 2026-11-02 23:29:14~23:36:51 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6plan_C1_r1 440 -e MODE=plan` (C1) | 중단 없이 32 VU까지 완주(계산 p95 2초 임계 안 넘음). 처리량 최대 4 VU 113.9 req/s(PUT 포함, 계산 약 57건/s) → 32 VU 85.3 req/s로 감소. p95 1 VU 84 → 32 VU 622ms. CPU 1 VU부터 중앙 91~102%. GC 비중 0.6% → 8.3%. 메모리 최대 542MiB(여유 482MiB). 오류 0%, OOM 없음 |
| 2026-11-02 23:36:51~23:44:28 | 같은 명령 r2 | 완주. 처리량 최대 4 VU 85.1 req/s → 32 VU 77.6. p95 32 VU 698ms. GC 비중 최대 10.6%. 메모리 최대 500MiB |
| 2026-11-02 23:44:28~23:52:05 | 같은 명령 r3 | 완주. 처리량 최대 4 VU 96.7 → 32 VU 75.6. p95 32 VU 704ms. GC 최대 9.1%. 메모리 최대 510MiB. 세 회 단계별 p95 차이 ±25% 안(안정) |
| 2026-11-02 23:52:05~23:57:45 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6mixed_C1_r1 420 -e MODE=mixed` (C1) | **160 req/s 단계에서 중단**(+270초). 80: p95 114.5ms, CPU 최대 100%·중앙 57%. 160: 실제 57.2 req/s, 모든 API가 느려짐(popularity p95 833, search 3,268, plan-calc 5,661ms), dropped 317, VU 338, CPU 중앙 88.6%, 메모리 최대 580MiB(여유 444MiB), 스레드 223, GC 8.2% |
| 2026-11-02 23:57:45~00:02:21 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6worst_C1_r1 260 -e MODE=worst` (C1, 추가 측정) | 최악 입력 동시 계산: 1 VU p95 4.3초, 2 VU 8.6초, 4 VU 20.0초(코어 하나를 나눠 씀). 처리량 계산 약 0.23건/s로 고정. GC 비중 8.1→13.2%. 힙 최대 131MB, 메모리 최대 477MiB. 오류 0% |
| 2026-11-03 00:02:21~00:07:12 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6readfine_C1_r1 360 -e MODE=read -e RATES=80,100,120,140,160` (C1, 한계 세분) | **140 req/s 단계에서 중단**(+222초). 100: p95 138(search 202), 120: p95 210(search 426), dropped 0, CPU 중앙 31%. 140: 실제 84.6 req/s, search p95 3,350ms, dropped 149, CPU 중앙 33%, 스레드 203 |
| 2026-11-03 00:07:47 | `docker inspect ottnavi-backend-perf-1`, 로그 WARN 집계(23:00 이후) | C1 그대로(1GiB·스왑 없음·CPU 1·`-Xmx512m`), healthy, OOMKilled=false, RestartCount=0. WARN은 재기동마다 나는 기동·종료 경고(springdoc, TMDB 토큰 없음, 요청 제한 꺼짐, Batch reader close 예외) 11회씩뿐이고 ERROR·연결 풀 타임아웃은 없음. 결과 폴더 14MB |

## 검색 개선 전후 비교: C0(인덱스 없음, 개선 전)

- 조건 이름이 바뀌었다. 위 표의 `C1`은 "1GiB 기본 조건"이라는 옛 이름이고, 아래의 **C0 = 트라이그램 인덱스 없음(Flyway V11 이미지 그대로)**, 이후 C1 = 인덱스만, C2 = 인덱스 + 후속 변경이다. 컨테이너 조건은 모두 1GiB·CPU 1·`-Xmx512m`.
- 시작 전 상태(03:4x): 4개 컨테이너 healthy, health UP, `flyway_schema_history` 최신 11, `title` 인덱스는 `title_pkey`·`uk_title_media_tmdb`뿐. postgres와 backend-perf는 2026-11-03 03:24(KST)에 다시 시작된 상태였다(위 측정 때와 다른 기동).
- 스크립트 변경: 검색어 30개 재구성(2글자 15 + 3글자 이상 15, 번갈아 배치, 모두 결과 20건 이상이라 개수 쿼리가 항상 실행됨), 검색 요청에 `tlen`(short·long) 태그, `read-baseline.js`에 S1b 2글자 연속 구간(5 VU·2분·쉼 0.5초, 처음 30초 `phase:early`·나머지 `late`)과 `SMOKE=1`, `stress.js` 검색어 선택 수정(이전에는 30개 중 12개만 쓰였다), `summarize.cjs` 하위 지표 일반화와 avg 추가, `stages.cjs` 검색 `search/short`·`search/long` 분리, `timeline.cjs` 추가.

| 시각 | 명령 | 결과 |
|---|---|---|
| 2026-11-03 03:50 | `k6 inspect --include-system-env-vars` read-baseline·stress·warmup·plan | 4개 통과. read-baseline 시작 시각 0s·2m10s·3m20s·4m30s·6m40s, sampler 8m50s |
| 2026-11-03 03:51:16 | `… run --rm --no-deps -e SMOKE=1 k6 run … read-baseline.js`(재기동·워밍업 없이 동작 확인) | 오류 0%. 하위 지표 키 `http_req_duration{api:search,tlen:short}` 형식 확인, summarize 출력 정상 |
| 2026-11-03 03:53:51 | `… -e MODE=read -e SMOKE=1 … stress.js --out csv` | 오류 0%, 검색 short 150·long 150건으로 균형, stages.cjs `search/short`·`search/long` 분리 확인 |
| 2026-11-03 03:55:39~04:05:15 | `K6_CSV=1 bash infra/k6/run-measure.sh read-baseline.js read_C0_r1 560` | S1 short avg 67.7 med 66.3 p95 78.4 p99 84.5 / long avg 68.7 med 67.6 p95 80.0 p99 85.5. S1b 전체 avg 66.1 p95 75.0 p99 80.0(early p95 78.1, late 72.9), 1,061건. detail-miss p95 15.5(r2·r3 8.0), detail-hit p95 2.8, popularity p95 2.6. 오류 0%. docker CPU 최대 38.6%, 메모리 최대 485.0MiB, OOM 없음 |
| 2026-11-03 04:05:24~04:14:59 | 같은 명령 r2 | S1 short avg 68.3 med 67.3 p95 78.5 p99 85.5 / long avg 68.9 med 67.7 p95 79.5 p99 86.7. S1b avg 65.3 p95 74.2 p99 80.7(early 78.0, late 72.9), 1,063건. 오류 0%. CPU 최대 61.5%(+402초, S1b 5 VU가 동시에 시작한 순간 한 표본), 메모리 최대 499.2MiB, OOM 없음 |
| 2026-11-03 04:15:09~04:24:45 | 같은 명령 r3 | S1 short avg 67.6 med 66.5 p95 77.7 p99 84.6 / long avg 68.1 med 67.4 p95 77.7 p99 85.6. S1b avg 67.0 p95 76.4 p99 81.9(early 77.3, late 74.4), 1,060건. 오류 0%. CPU 최대 54.4%, 메모리 최대 494.2MiB, OOM 없음 |
| 04:25 판정 | 읽기 C0 | 세 회 모두 안정(검색 p95 차이 3% 안). 중앙값: S1 short avg 67.7·p95 78.4·p99 84.6, long avg 68.7·p95 79.5·p99 85.6, S1b avg 66.1·p95 75.0·p99 80.7. C0에서 short·long 차이 없음(둘 다 순차 스캔), S1b도 구간 내내 평평함(early가 2~5ms 높은 것은 시작 순간 영향으로 봄) |
| 2026-11-03 04:24:58~04:33:11 | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6readfine_C0_r1 480 -e MODE=read -e RATES=100,110,120,125,130,135,140` | **중단 없이 140까지 완주.** 단계별 p95 77.6/73.5/73.3/73.4/73.7/74.4/75.5ms, 검색 p95 short 82→79, long 85→82, dropped 0, 스레드 35~36, CPU 중앙 18~20%(100 단계만 50%: 5→100 올림 직후), 메모리 최대 499.1MiB, 오류 0% |
| 2026-11-03 04:33:46~04:39:27 | `… s6readfine_C0_r2hi 480 -e MODE=read -e RATES=140,150,160,170,180,190,200`(위가 완주해 붕괴 지점을 찾으려 추가) | **180 단계에서 중단**(읽기 p95>1초 abortOnFail, +약 297초). 150 p95 78.5(검색 short 85·long 89), 160 p95 87.5(94·100), 170 p95 333.7(365·374) VU 47, 180 실제 163.2 req/s·p95 2,704.6(검색 2,733·2,743)·dropped 146·VU 197·스레드 223. CPU 중앙 23~27%, 메모리 최대 551.4MiB, 오류 0%, OOM 없음. 140 단계 p95 125는 5→140 급상승 직후라 참고만 |
| 04:40 판정 | 읽기 한계 C0 | 마지막 안정 160 req/s, 느려지기 시작 170, 무너짐 180. 이전 기록(120 안정·140 붕괴)과 다름: 같은 검색어(사랑·전쟁 등)의 80 req/s 평균이 이전 약 100ms → 지금 100 req/s에서 약 70ms. 원인은 확인하지 못했다(이전 측정 뒤 postgres·backend-perf 재시작, 검색어 구성 변경이 겹침). C1과의 비교는 이 C0(같은 날)을 기준으로 한다 |
| 04:40 | 이상 | `system-metrics`의 cgroup 사용량이 2,354~2,635MB로 1GiB 한도보다 크게 나옴(이전 측정 때는 519~580MB). 컨테이너 메모리 판정은 docker stats로 한다. 원인 미확인 |

## 검색 개선 전후 비교: C1(트라이그램 인덱스만, 매번 계획 세우기 설정 없음)

- 상태: 메인이 04:42에 V12 jar로 backend-perf를 재생성했다. 시작 전 확인(04:50) 결과 Flyway 12, `title` 인덱스 `idx_title_original_title_trgm`·`idx_title_title_trgm`·`title_pkey`·`uk_title_media_tmdb`, 1GiB·CPU 1, OOMKilled=false, RestartCount=0. 이미지·환경 변수는 바꾸지 않았고 각 회는 `run-measure.sh`의 restart만 했다. 스크립트·검색어는 C0와 같다(`timeline.cjs`에 태그 조건 인자만 추가).
- 주의: `20261103-000221_s6readfine_C1_r1`은 옛 이름(1GiB 기본 조건)의 파일이다. 이번 C1의 한계 파일은 `20261103-052041_s6readfine_C1_r1`, `20261103-052919_s6readfine_C1_r2hi`다.

| 시각 | 명령 | 결과 |
|---|---|---|
| 2026-11-03 04:50:54~05:00:30 | `K6_CSV=1 bash infra/k6/run-measure.sh read-baseline.js read_C1_r1 560` | S1 short avg 120.1 med 119.5 p95 130.1 p99 143.0 / long avg 9.8 med 8.5 p95 19.5 p99 23.5. S1b avg 121.5 p95 134.1 p99 145.4(early p95 135.9, late 133.8), 968건. 오류 0%. CPU 최대 41.8%, 메모리 최대 547.3MiB, OOM 없음 |
| 2026-11-03 05:00:54~ | 같은 명령 r2 | S1 short avg 119.9 med 119.5 p95 129.1 p99 135.6 / long avg 9.5 med 8.4 p95 19.1 p99 22.8. S1b avg 122.5 p95 136.4 p99 144.8(early 133.6, late 136.6), 965건. 오류 0%. CPU 최대 41.9%, 메모리 최대 491.1MiB, OOM 없음 |
| 2026-11-03 05:11:00~ | 같은 명령 r3 | S1 short avg 119.6 med 120.0 p95 129.5 p99 134.3 / long avg 9.6 med 8.6 p95 18.6 p99 21.9. S1b avg 119.7 p95 131.7 p99 138.8(early 135.7, late 129.1), 969건. 오류 0%. CPU 최대 55.2%, 메모리 최대 495.6MiB, OOM 없음 |
| 05:20 판정 | 읽기 C1 | 세 회 안정(검색 p95 차이 5% 안). 중앙값: short avg 119.9·p95 129.5·p99 135.6(C0 대비 +77%·+65%), long avg 9.6·p95 19.1·p99 22.8(C0 대비 -86%·-76%), S1b avg 121.5·p95 134.1·p99 144.8(C0 대비 p95 +79%). S1b 20초 구간 평균은 세 회 모두 117~125ms로 평평했다. **S1 첫 short 요청부터 이미 느렸다**(r1 1~40번째 대부분 117~165ms, 66~72ms가 섞임). 즉 전환은 S1b가 아니라 워밍업~S1 시작 무렵에 이미 일어났다 |
| 05:10 | 읽기 전용 계획 확인(`begin read only` 안에서 PREPARE + `plan_cache_mode` 강제, rollback) | 개수 쿼리 기준. 사용자 정의 계획: '%사랑%'은 Seq Scan(비용 4,058.9), 30.0ms. '%크리스마스%'는 trgm Bitmap(비용 1,777.7), 0.57ms. 일반 계획(비용 1,025.1): '%사랑%'은 두 trgm 인덱스 전체(33,004행씩) + 재검사 제거 32,364행, 59.0ms(약 2.0배). '%크리스마스%'는 0.45ms. 일반 계획 비용이 short·long 어느 쪽의 사용자 정의 계획 비용보다 낮아, 검색어 구성과 관계없이 5회 뒤 일반 계획이 선택되는 조건이다 |
| 2026-11-03 05:20:41~ | `K6_CSV=1 bash infra/k6/run-measure.sh stress.js s6readfine_C1_r1 480 -e MODE=read -e RATES=100,110,120,125,130,135,140` | 중단 없이 140까지 완주. 단계별 p95 137.7/135.1/134.9/134.4/144.1/134.2/133.5ms(C0 73~78), 검색 p95 short 159/145/146/154/175/143/143, long 19/18/17/17/24/17/17. dropped 0, 스레드 33~34, CPU 중앙 18~23%(100 단계만 51%), 메모리 최대 496.2MiB, 오류 0% |
| 2026-11-03 05:29:19~05:35:09 | `… s6readfine_C1_r2hi 480 -e MODE=read -e RATES=140,150,160,170,180,190,200` | **180 단계에서 중단**(읽기 p95>1초, +약 302초, C0와 같은 단계). 150 p95 138.2(short 155·long 23), 160 144.2(170·45), 170 203.6(273·156) VU 16, 180 실제 175.7 req/s·p95 1,788.3(1,937·1,824)·dropped 85·VU 136·스레드 158. CPU 중앙 23~25%, 메모리 최대 539.2MiB, 오류 0%, OOM 없음. 140 첫 단계 p95 512는 5→140 급상승 직후(detail-miss도 522)라 참고만 |
| 05:36 판정 | 읽기 한계 C1 | 마지막 안정 160(C0와 같음), 170에서 느려지기 시작(p95 204, C0 334), 무너짐 180(C0와 같음). 한계가 오르지 않은 이유(계산): 검색 1건의 평균 DB 점유가 C0 약 68ms, C1 (short 약 130 + long 약 7)/2 ≈ 69ms로 거의 같아 연결 5개 기준 상한(약 71 검색/s ≈ 전체 178 req/s)이 그대로다. 풀 대기 수는 재지 않아 추정이다 |
| 05:36 | 이상 | `system-metrics` cgroup 값은 C1 컨테이너(04:42 재생성)에서 490~648MB로 정상 범위로 돌아왔다. C0 때의 2.3~2.6GB는 그 이전 컨테이너 인스턴스에서만 나타났다. 원인 미확인 |

## 검색 개선 전후 비교: C2(인덱스 + 연결마다 `SET plan_cache_mode = force_custom_plan`)

- 구성: 메인이 만든 `infra/k6/compose.plan-cache.yml`을 얹는다. 기본 `SPRING_APPLICATION_JSON`(요청 제한 끄기)에 `spring.datasource.hikari.connection-init-sql`만 더했고, 메모리·CPU·`JAVA_TOOL_OPTIONS`·이미지는 C1과 같다(두 compose 파일 대조). 모든 실행에 `PERF_COMPOSE_EXTRA=infra/k6/compose.plan-cache.yml`을 주었고, 실행마다 끝난 뒤 컨테이너 환경에 `force_custom_plan`이 있는 것을 `docker inspect`로 확인했다(모두 1건).
- 시작 전(05:38) 확인: trgm 인덱스 2개, 1GiB·CPU 1, OOMKilled=false, RestartCount=0.

| 시각 | 명령 | 결과 |
|---|---|---|
| 2026-11-03 05:38:19~05:47:53 | `PERF_COMPOSE_EXTRA=infra/k6/compose.plan-cache.yml K6_CSV=1 bash infra/k6/run-measure.sh read-baseline.js read_C2_r1 560` | S1 short avg 65.4 med 64.7 p95 71.7 p99 76.8 / long avg 10.2 med 9.1 p95 19.6 p99 22.4. S1b avg 67.5 p95 77.3 p99 92.3(early 75.9, late 78.8), 1,060건. S1 첫 short 요청 82·85ms 뒤 65~72ms. 오류 0%. CPU 최대 39.5%, 메모리 최대 488.0MiB, OOM 없음 |
| 2026-11-03 05:48:11~05:57:44 | 같은 명령 r2 | S1 short avg 65.5 p95 70.1 p99 76.2 / long avg 10.3 p95 19.5 p99 23.8. S1b avg 66.2 p95 75.0 p99 81.1(early 78.4, late 73.6). 오류 0%. CPU 최대 38.7%, 메모리 최대 486.0MiB |
| 2026-11-03 05:57:44~06:07:19 | 같은 명령 r3 | S1 short avg 65.5 p95 70.9 p99 81.9 / long avg 10.3 p95 19.4 p99 22.9. S1b avg 66.9 p95 76.2 p99 82.3(early 77.4, late 75.1). 오류 0%. CPU 최대 35.4%, 메모리 최대 486.7MiB |
| 06:07 판정 | 읽기 C2 | 세 회 안정. 중앙값: short avg 65.5·p95 70.9·p99 76.8, long avg 10.3·p95 19.5·p99 22.9, S1 전체 avg 37.9·p95 68.9, S1b avg 66.9·p95 76.2·p99 82.3. S1b 20초 구간 평균 63.8~69.2ms로 평평 |
| 2026-11-03 06:07:30~06:15:44 | `… stress.js s6readfine_C2_r1 480 -e MODE=read -e RATES=100,110,120,125,130,135,140` | 완주. 단계별 p95 70.8/68.2/68.8/68.2/68.2/68.3/68.5, 검색 short 73~93 · long 17~19, dropped 0, 스레드 32~34, CPU 중앙 19~21%(100 단계 52%), 메모리 최대 503.8MiB, 오류 0% |
| 2026-11-03 06:15:44~06:23:59 | `… s6readfine_C2_r2hi 480 -e MODE=read -e RATES=140,150,160,170,180,190,200` | **200까지 완주.** 150~200 p95 69.4~72.6, 검색 short 76~83 · long 17, dropped 0, 스레드 35~36, CPU 중앙 23~28%, 메모리 최대 502.4MiB, 오류 0% |
| 2026-11-03 06:24:20~06:25:32 | `… s6readfine_C2_r3top 480 -e MODE=read -e RATES=200,225,250,275,300,325,350` | **무효(재기동 직후 급상승).** 10초 안에 5→200으로 올리자 첫 단계에서 CPU 중앙 97%, p95 2,167ms, 실제 41.1 req/s로 +22초에 중단. 한계가 아니라 데워지기 전의 급상승 탓으로 보고 단계를 나눠 다시 잼 |
| 2026-11-03 06:25:54~06:34:48 | `… s6readfine_C2_r4top 600 -e MODE=read -e RATES=100,150,200,225,250,275,300,325,350`(상한 350 근거: 검색 평균 DB 점유 약 37.5ms → 연결 5개로 검색 약 133건/s ≈ 전체 약 333 req/s) | **325 단계에서 중단**(+약 484초). p95: 200 72.7, 225 73.8, 250 74.5, 275 77.1(short 93·long 27), 300 116.2(145·84) 스레드 43, 325 실제 304.2 req/s·p95 2,303.3·dropped 713·VU 400(maxVUs 도달)·스레드 223, 상세·인기도 약 670ms로 함께 느려짐. CPU 중앙 41~43%, 메모리 최대 563.4MiB, 오류 0%, OOM 없음 |
| 06:35 판정 | 읽기 한계 C2 | 마지막 안정 275, 느려지기 시작 300, 무너짐 325(실제 최대 약 304 req/s). 붕괴 때 CPU 중앙 43%라 CPU 포화는 아니고 검색 경로(연결 5개)에서 먼저 막히는 모양은 C0·C1과 같다. 붕괴 단계에서 상세·인기까지 느려진 것은 C0·C1에 없던 모습이다(요청 스레드가 223개로 쌓여 전체가 줄을 선 것으로 추정) |

### C0·C1·C2 비교 요약 (평상시는 3회 중앙값 ms, 한계는 1회)
| 지표 | C0 인덱스 없음 | C1 인덱스만 | C2 인덱스 + 매번 계획 세우기 |
|---|---|---|---|
| S1 short 평균 / p95 | 67.7 / 78.4 | 119.9 / 129.5 | **65.5 / 70.9** |
| S1 long 평균 / p95 | 68.7 / 79.5 | 9.6 / 19.1 | **10.3 / 19.5** |
| S1 전체 평균 / p95 | 68.2 / 79.3 | 64.8 / 126.4 | **37.9 / 68.9** |
| S1b(2글자 연속) 평균 / p95 | 66.1 / 75.0 | 121.5 / 134.1 | 66.9 / 76.2 |
| 재기동 뒤 첫 검색 요청 | 218.6 / 223.1 / 279.4 | 296.1 / 289.3 / 223.1 | 278.5 / 279.6 / 222.0 |
| 읽기 마지막 안정 단계 | 160 req/s | 160 req/s | **275 req/s** |
| 느려지기 시작 | 170(p95 333.7) | 170(p95 203.6) | 300(p95 116.2) |
| 무너진 단계 | 180(실제 163.2) | 180(실제 175.7) | **325(실제 304.2)** |
| 무너질 때 검색 p95 | 2,733(short)·2,743(long) | 1,937·1,824 | 2,357·2,296 |
| 컨테이너 메모리 최대(평상시 / 한계) | 499.2 / 551.4MiB | 547.3 / 539.2MiB | 488.0 / 563.4MiB |
| OOM·재시작 | 없음 | 없음 | 없음 |
