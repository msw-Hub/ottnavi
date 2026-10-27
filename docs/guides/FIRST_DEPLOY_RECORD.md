# 첫 공개 배포 기록 (Task 092~095, 2026-10-26 ~ )

> **초안.** 배포와 첫 수집을 진행하면서 겪은 문제, 알게 된 값, 결정을 모은 기록이다. 근거 문서가 아니고, 규칙은 PRD·TECH·ROADMAP이 기준이다.
> 비밀 값(비밀번호, 토큰, 키)은 적지 않는다. 변수는 이름만 적는다.
> `[확인 못함]`은 이 기록을 쓴 시점에 직접 확인하지 못한 것이다. 완료 뒤 채우거나 지운다.

## 1. 흐름 요약

| 시각(KST) | 일 |
|---|---|
| 10-26 | PR #101(cron `CRON_ENABLED` 안전장치·낡은 주석), #102(내 메뉴) 병합. 쌓은 PR 충돌을 리베이스로 정리 |
| 10-26 | 운영 준비: Redis Cloud 새 DB 생성(14일 비활성으로 삭제돼 있었음), Supabase `public` 스키마 재생성, Cloudtype 메모리 1GB 구독 |
| 10-26 | PR #103(`develop`→`main`, Merge commit `64407f7`) 병합 → Vercel Production 자동 배포, Cloudtype 빌드·배포 |
| 10-26 23:00~ | 환경 변수 입력, 재배포 3회 끝에 `/actuator/health` UP (원인은 Redis 호스트·첫 연결, 3절) |
| 10-26 | Google 로그인 성공(`userId=1`), 운영 ADMIN 지정, 상품·가격 17개 입력 |
| 10-27 00:00:15 | 첫 전체 수집 시작(`executionId=1`, `tier=ALL`) |
| 10-27 08:34 | 첫 전체 수집 `COMPLETED`(8시간 33분 48초), 재계산 Job 이어서 완료 |
| 10-27 오전 | 스모크. 큰 문제는 없었고 정렬·안내 문구·흐름 피드백이 나왔다(5절 "스모크 피드백") |
| 10-27 오후 | PR #104(`feature/f-smoke-fixes`)로 피드백 반영, 검수(`pr-reviewer`·`contract-checker`) 뒤 지적 반영 |

## 2. 운영 구성 (이름만)

- **Cloudtype** 서비스 `ottnavi`, 배포 브랜치 `main`, 서브 디렉토리 `backend`, JDK 17, 포트 8080
  - Build: `./gradlew bootJar`
  - Start: `java -Dspring.profiles.active=prod -jar build/libs/backend-0.0.1-SNAPSHOT.jar`
  - 리소스: 메모리 구독 1GB, vCPU 구독 0. 선결제·월 자동 결제
  - 환경 변수(런타임 `Environment variables`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `ORIGIN_SECRET`, `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET`, `ADMIN_BATCH_TOKEN`, `TMDB_API_KEY`, `JAVA_TOOL_OPTIONS`(`-Xmx512m`)
  - 변수를 저장하면 곧바로 재배포된다(저장과 배포가 분리돼 있지 않다)
- **Vercel** Production = `main`. `vercel.json`의 `/api` rewrite가 Cloudtype 공개 주소로 넘긴다(서비스를 수정만 했으므로 주소 불변). `ORIGIN_SECRET`은 Cloudtype·Vercel·GitHub가 같은 값
- **GitHub Actions** 시크릿 `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`, `BACKEND_BASE_URL`(Cloudtype 공개 주소, 배포 뒤 확인해 등록). 변수 `CRON_ENABLED`는 첫 수집 안정 확인 뒤에 만든다(지금 없음 = 예약 실행은 모두 건너뜀)
- **Supabase** Session Pooler 5432, PostgreSQL 17.11, Flyway V1~V9
- **Redis Cloud** Free 30MB, 버전 8.6, eviction `allkeys-lru`, persistence 없음, TLS 꺼짐(평문), RESP3

## 3. 겪은 문제와 원인, 해결

| # | 증상 | 원인 | 해결 | 교훈 |
|---|---|---|---|---|
| 1 | Redis Cloud 무료 DB가 사라짐 | 14일 연속 명령이 없으면 영구 삭제(TASK003 3.3) | 새로 만들고 `REDIS_*` 3개 교체 | 공개 전 준비 기간이 길면 삭제된다. 만든 시점을 배포 직전으로 미루는 것이 낫다 |
| 2 | `/actuator/health` 503, 로그의 `Unable to connect`, `NXDOMAIN` | `REDIS_HOST`가 옛 DB의 주소였고 지역 부분(`ap-northeast`)에 글자가 빠져 있었다 | Redis Cloud 콘솔의 Public endpoint를 복사해 호스트·포트 분리 입력 | 호스트는 직접 입력하지 말고 복사. 비밀번호보다 주소를 먼저 의심 |
| 3 | 주소를 고친 뒤 첫 health가 한 번 더 실패 | `Connection initialization timed out after 2 second(s)`: 첫 연결의 핸드셰이크가 2초를 넘김 | 코드 변경 없음. 3초 뒤 연결 성공, 이후 UP | 첫 Redis 요청은 느리거나 실패할 수 있다(요청 제한은 fail-open). 필요하면 연결 시간 제한을 늘린다 |
| 4 | 프로필이 `local`로 뜰 위험 | `Build Variables`는 런타임에 전달되지 않는다(TECH 233행) | Start Command에 `-Dspring.profiles.active=prod` | 런타임 값은 `Environment variables`나 Start Command에 |
| 5 | 서버 기동이 338초 | vCPU 구독 0(공유·최소 CPU). 로컬은 5.7초 | 지금은 감수. 수집 뒤 CPU 증설 여부를 판단(5절) | 재배포마다 5~6분이 걸린다 |
| 6 | 관리자 화면 401 3건 | Access Token 30분 만료(`InsufficientAuthenticationException`), 인터셉터가 재발급 | 정상 동작 | 화면을 오래 열어 두면 나는 로그 |
| 7 | 로그에 `HV000271` 경고 | `@Valid`를 `List`에 직접 붙임(`UpdateMySettingsRequest.subscriptions`) | 기능 영향 없음. `List<@Valid X>`로 정리할 후속 항목 | |
| 8 | 기동 로그에 `Could not safely identify store assignment` 24줄 | JPA와 Redis 라이브러리가 함께 있어 모든 리포지토리를 Redis 후보로도 검사 | 후속: `spring.data.redis.repositories.enabled: false`(효과 [확인 못함]) | 로그가 길어 오류를 찾기 어렵다 |
| 9 | 쌓은 PR(#101→#102)을 아래부터 Squash 병합하자 위 PR이 충돌 | Squash로 원본 커밋이 합본으로 바뀜 | 위 PR을 `git rebase --onto develop <기점>` 후 `--force-with-lease` | 쌓은 PR은 위→아래→`develop` 순서로 병합(메모리 [[feedback-stacked-pr-merge-order]]) |
| 10 | 서비스별 인기 순위가 비어 있음 | 인기 순위는 별도 Job(`popularityJob`)이 만든 `popularity_snapshot` 기록으로 계산하는데, 첫 전체 수집(`collectJob`)에는 포함되지 않아 한 번도 돌지 않았다 | GitHub Actions `popularity` 워크플로를 수동 실행(수동 실행은 `CRON_ENABLED`와 무관) | 새 환경에서는 첫 수집 뒤에 인기 수집도 한 번 돌린다 |
| 11 | 빌드가 `EOFException`, `NoSuchFileException`(`build/test-results/.../in-progress-results-generic.bin`)으로 실패 | 같은 폴더에서 `gradlew build` 두 개를 겹쳐 돌리거나, 에이전트가 같은 시각에 테스트를 돌리며 `build/test-results/test`를 지웠다 | 겹치는 작업 없이 단독으로 다시 실행해 성공 | 빌드는 한 번에 하나만 돌린다 |
| 12 | 프론트 테스트가 관리자 상품 화면에서 5초 시간 초과 | 백엔드 빌드와 동시에 돌려 CPU가 모자랐다 | 빌드가 끝난 뒤 단독 재실행해 618건 통과 | 무거운 검증은 겹쳐 돌리지 않는다 |
| 13 | 에이전트가 "적용했다"고 보고했는데 파일이 그대로였다 | 이 PC의 `python` 명령이 실제로는 실행되지 않고 "Python "만 출력하는 스텁이라, 스크립트로 한 변경이 하나도 적용되지 않았다 | `git status`·`git diff --stat`과 `grep`으로 반영을 확인하고 Edit 도구로 다시 적용 | 에이전트 보고를 믿지 말고 파일을 직접 확인한다. 변경은 Edit 도구로 한다 |

## 4. 측정값

| 항목 | 값 |
|---|---|
| 메모리 기준(수집 전) | 426.29MB / 1GB |
| 영화 후보 단계 중 | 444.87MB |
| 영화 상세 시작 직후 / 15분 뒤 | 453.4MB / 454.93MB (평평) |
| 기동 시간 | 341.2초, 331.2초, 338.4초 (Spring 컨텍스트 초기화만 약 80초) |
| `providerListStep` | 3.9초, 받은 38곳 중 새로 저장 30곳 |
| `movieCandidateStep` | 15분 50초, 1,690쪽 (로컬 8분 3초) |
| `tvCandidateStep` | 7분 39초, 992쪽 (로컬 4분 13초) |
| 연결 풀 | 수집 중 최대 1~2개 사용, 끝난 뒤 `active=0, idle=5` |

| 단계 읽음/저장 (SQL 조회) | `movieCandidate` 33,737건(커밋 1,687), `tvCandidate` 19,758건(커밋 988), 건너뜀 0. 로컬은 33,638·19,591건 |
| `movieDetailStep` 진행(시작 후 42분 11초) | 6,780건 읽음(커밋 678, chunk 10), 건너뜀 0. 속도 약 2.68건/초(로컬 약 4건/초의 약 0.67배) |
| 영화 상세 완료 예상 | 전체 영화 19,835건 ÷ 2.68건/초 ≈ 약 2시간 3분, 시작 00:23:52 → 약 02:27 KST |
| 작품·시청 단위 | `title` 영화 19,835·드라마 13,355, `watch_unit` 6,900(영화 상세 진행분), `availability` 0(판정 전) |
| DB 용량(영화 상세 중) | 37MB (`pg_database_size`, 로컬 최종 167MB) |

- 후보 단계는 로컬의 약 1.8~2배였지만 상세 단계는 약 1.5배(0.67배 속도)다. 후보 단계 비율로 잡은 예상(2시간 27분~2시간 44분)보다 빠르다. 단계마다 CPU·네트워크 비중이 다르다.
- SQL로 `batch_step_execution`을 조회하면 진행 상황을 볼 수 있다(6절). 시각 컬럼의 시간대는 아래 "시각 컬럼 주의"를 따른다(처음에는 `AT TIME ZONE 'Asia/Seoul'`을 붙이면 한국 시간이라고 적었지만 첫 수집 결과에서 틀린 것이 확인됐다).
- 운영이 로컬의 약 1.8~2배 느리다. 대역폭(100Mbps)보다 vCPU 0(공유 CPU)이 원인일 가능성이 크다 `[확인 못함: Cloudtype CPU 그래프]`.
- 첫 전체 수집 예상은 약 11~12시간이었지만(로컬의 약 1.9배, 추정) 실제는 **8시간 33분 48초**(2026-10-27 00:00:15 ~ 08:34:03 KST, `executionId=1`, `COMPLETED`)로 로컬 6시간 17분의 약 1.36배다.

| 단계 (첫 전체 수집, 서버 로그 기준) | 소요 | 비고 |
|---|---|---|
| `refreshTierStep` | 1.5초 | DAILY로 올린 작품 0건, WEEKLY로 되돌린 작품 0건 |
| `providerListStep` | 3.9초 | 받은 38곳 중 새로 30곳 |
| `movieCandidateStep` | 15분 50초 | 1,690쪽 |
| `tvCandidateStep` | 7분 39초 | 992쪽 |
| `movieDetailStep` | 1시간 59분 21초 | 예상 약 2시간 3분과 거의 같음 |
| `tvDetailStep` | 2시간 28분 13초 | TMDB 404 1건 건너뜀, 재시도 1건(상태 -1, 시도 1/2) |
| `availabilityStep` | 3시간 42분 37초 | 가장 오래 걸림. 재시도 2건. 시즌 판정에서 작품 단위 제공처로 대체한 시즌 4,195개, 작품 단위 호출 1,679회 |
| 합계 | 8시간 33분 48초 | 끝난 뒤 독점 목록·홈·작품 상세 캐시 정리, 재계산 Job(`planRecalcJob`, `executionId=2`)이 0.5초에 `COMPLETED`(ACTIVE 플랜이 없어 대상 0건) |

- 최대 메모리는 수집이 끝난 뒤 **576.67MB / 1GB**(사용자 확인값, 수집 중 최댓값이 아니라 끝난 시점의 값). 수집 전 426MB에서 +150MB 수준이고 `-Xmx512m` 힙과 비힙을 합한 값이라 1GB 한도에 여유가 있다. OOM 로그 없음, 서버 재시작 없음(`Starting OttnaviApplication`이 기동 때 한 번뿐).
- 수집 중 실패는 없었다. `ERROR` 로그 0건, 경고는 TMDB 404 1건(`titleId=24499`, `tmdbId=338169`, 건너뜀)과 TMDB 재시도 4건(모두 다음 시도에서 통과로 보임, 최종 실패 로그 없음)뿐이다. `429`도 없었다.
- 규모(수집 직후): `title` 33,190, `watch_unit` 40,016, `availability` 280,112, DB 용량 171MB(로컬 167MB와 비슷). Supabase 무료 한도 500MB의 약 34%다. 이 규모에서 일상 수집으로 늘어나는 양은 아직 모른다.
- 시각 컬럼 주의: `batch_job_execution.start_time`에 `AT TIME ZONE 'Asia/Seoul'`을 붙이니 서버 로그보다 9시간 이른 값(시작 06:00, 종료 14:34)이 나왔다. 컬럼이 UTC 기준 `timestamp`라 `AT TIME ZONE`이 거꾸로 적용된 것으로 보인다 `[확인 못함: 컬럼 타입]`. 시각은 서버 로그(KST)를 기준으로 보고, "`AT TIME ZONE`을 붙이면 값은 KST"라는 처음 설명은 틀렸으므로 `AT TIME ZONE` 없이 조회하고 그 결과는 UTC로 읽는다.

## 5. 결정과 근거

- **메모리 1GB**: 기동 직후 302~426MB, 엔진 최악 입력의 힙 457~507MB(`-Xmx512m`에서 완주), 수집 청소 후 힙 최대 67MB(로컬). 0.5GB는 여유 없음, 1.5GB는 근거 없음. 드라마 상세·시즌 구간의 최대 메모리는 첫 수집에서 확정
- **`JAVA_TOOL_OPTIONS=-Xmx512m`**: 컨테이너의 50~60% 원칙. 수집 뒤 조정
- **CPU 증설**: 하지 않기로 했다(사용자 결정 2026-10-27). 근거: 첫 전체 수집이 8시간 34분(로컬의 1.36배)으로 끝났고 대부분 TMDB 응답 대기라 CPU가 병목이 아니다. 느린 것은 기동 5~6분과 재배포뿐이다. 공개 뒤 응답이 느려 보이거나 일상(DAILY) 수집이 새벽 안에 끝나지 않으면 다시 판단한다
- **일요일 WEEKLY 수집**: 지금은 켜지 않는다(사용자 결정 2026-10-27, 다른 작업을 마친 뒤 다시 정한다). 초기에는 ALL과 규모가 비슷해 8시간쯤 걸린다
- **GitHub 시크릿 이름 확인**: 이름만 보는 방법이 없어 건너뛴다. 인기 수집 수동 실행(`popularity` 워크플로)이 `BACKEND_BASE_URL`·`ORIGIN_SECRET`·`ADMIN_BATCH_TOKEN` 세 값을 모두 쓰므로 그 결과(202)로 대신 확인한다
- **프론트 주소**: `ottnavi.shop`(카페24 구매)을 Vercel에 연결해 `ottnavi.vercel.app`과 둘 다 열린다(사용자 확인 2026-10-27). Google 승인된 리디렉션 URI는 **`ottnavi.shop`만 등록**했고(`ottnavi.vercel.app`은 등록 안 함, 사용자 정정 2026-10-27) **대표 주소는 `ottnavi.shop`**이다. 로그인은 `ottnavi.shop`에서만 된다. `[확인 못함: Cloudtype의 APP_FRONTEND_ORIGIN·OAUTH2_REDIRECT_BASE_URL이 어느 주소인지. 대표 주소와 다르면 로그인 뒤 이동·쿠키 도메인이 어긋날 수 있어 스모크 때 확인]`
- **Cloudtype 도메인 연결**: 하지 않음. Vercel이 `/api`를 넘기는 구조라 필요 없다
- **상품·가격**: 조사일 2026-10-05 값으로 17개 입력(단품 7, 저가형 단품 5, 번들 5). 관리자 API와 같이 구성 서비스 `tier`는 모두 `STANDARD`, 광고형·베이직은 `low_cost=true`. 가격은 공개 전에 한 번 더 확인
- **`CRON_ENABLED`**: 첫 수집이 끝나고 스모크 통과 뒤에 만든다

### 스모크 피드백과 결정 (2026-10-27, PR #104)

스모크에서 큰 문제는 없었다. 아래는 직접 써 보며 나온 수정이다.

- **정렬 기준**: 독점 목록과 새로 볼 수 있게 된 작품 줄에 1993·1999년 작품이 먼저 보였다. 화면의 날짜는 그 서비스가 판권을 들인 날짜일 뿐 작품 개봉일이 아니라서, 작품 개봉일이 **최근 6개월 이내**인 것을 먼저 보인다. 그 안에서 독점은 인기도순, 새 작품은 입고 확인일 최신순이다. 검색·장르별·출연자별은 **최근 2년 이내** 우선, 그 안은 인기도순이다. 개봉일이 미래이거나 없는 작품은 맨 뒤로 보낸다
- **독점 캐시 이름**: `exclusiveTitles` → `exclusiveTitlesV2`. 독점 목록은 Redis에 24시간 저장돼, 이름을 바꾸지 않으면 배포 뒤에도 다음 수집이 끝날 때까지 옛 순서가 남는다
- **인기 순위의 의미를 바꿨다**: 처음에는 서비스별 KR 제공처 필터로 `popularity.desc` 상위 20개를 받았다. 첫 결과가 그레이 아나토미(2005), 로 앤 오더(1999), 패밀리 가이(1999), 심슨 가족(1989) 같은 미국 장수 드라마였다. TMDB 인기도는 한국이 아니라 **전 세계** 활동량이고 `watch_region=KR`은 "한국에서 그 서비스로 볼 수 있는가"에만 걸리기 때문이다(런닝맨 약 479점, 그레이 아나토미 약 11,181점). 그래서 **한국 제작(`with_origin_country=KR`) + 첫 공개일 최근 2년 이내**로 제한했다(설정 `app.popularity.recent-years: 2`). 순위의 뜻은 "최근 2년 안에 공개된 한국 작품 중 TMDB 인기도 순"이고, 한국 시청량 순위는 TMDB로 만들 수 없다. 장수 드라마는 첫 방영일이 옛날이라 이 제한에서 빠진다
- **`with_origin_country=KR`의 값 형식**은 TMDB 문서에 설명이 없다 `[확인 못함]`. 배포 뒤 인기 수집 결과가 한국 작품인지 보고, 이상하면 `with_original_language=ko`로 바꾼다(영어 대사 한국 제작물은 빠지고 외국 제작 한국어 작품은 들어오는 차이가 있다)
- **옛 인기 스냅샷**: 배포 전에 쌓인 `popularity_snapshot`은 옛 조건(전 세계, 개봉일 무제한)이라 7일 평균에 섞인다. 배포 뒤 이 표를 비우고(`DELETE FROM popularity_snapshot;`, 계산용 임시 데이터라 다시 만들어진다) 인기 수집을 수동 실행한다. 순서: 배포 → 삭제 → 수집
- **화면**: 홈 줄 가로 스크롤바 제거, `/pricing`의 계산 방식 안내(저렴한 요금제는 설정에서 바꿈), 설정으로 이동하면 4번 구역으로 스크롤·포커스, 계산 중 로딩을 중앙 진행 창으로, `/plan` 저장 전 화면의 "다시 계산하기"를 "찜 목록 고치러 가기"(`/wishlist`)로(같은 입력으로 다시 계산해도 결과가 같다), 홈 독점 카드의 "넷플릭스(있음)" 표시 제거, 새 작품 카드는 볼 수 있는 서비스 이름만 표시

## 6. 알게 된 점 (수집)

- 수집 Job은 서버 기동 때 자동 실행되지 않고(`spring.batch.job.enabled: false`) 관리자 API로만 시작한다. 재배포가 수집을 다시 돌리지 않는다. 다만 수집 중 재배포하면 중단된다
- 수집·인기 수집·재계산·정리는 서로 동시에 시작할 수 없다(409 `BATCH_ALREADY_RUNNING`)
- `tier=WEEKLY`는 후보 발견까지 전체 흐름을 돌고 상세·판정 대상이 "찜하지 않은 나머지 전부"다. 초기에는 거의 전부가 WEEKLY라 ALL과 규모가 비슷하다
- 상세 단계는 `fetched_at`으로 거르지 않고 전부 다시 받는다. 방영 중 드라마는 회차가 늘어 상세가 바뀌므로 "최근에 받았으면 생략"은 안 맞는다. 개봉한 지 오래된 영화와 종영된 드라마만 생략하는 방안을 후속으로 검토

- 수집 진행 SQL(읽기 전용, Supabase SQL Editor): `batch_step_execution`에서 `step_name`, `status`, `read_count`, `write_count`, `commit_count`, `last_updated`를 최신 `job_execution_id`로 조회한다. `last_updated`가 오래 멈춰 있으면 정체·중단을 의심한다. 전체 상태는 `batch_job_execution`(`STARTED`=진행 중)
- 수집 중에도 SQL 조회는 영향이 없다(`SELECT`만)

## 7. 남은 일

- [x] 첫 수집 완료(8시간 33분, 4절). 규모·DB 용량(171MB)도 4절에 기록함
- [ ] `/api/.env` 같은 비밀 파일 탐색 요청이 들어왔다(2026-10-27 11:36, 401로 막힘). 인터넷에 열린 서버라면 흔한 자동 탐색이고 `.env`는 저장소·서버에 노출되지 않는다는 점만 확인
- [x] 스모크(2026-10-27): 로그인 유지, 검색, 찜, 플랜 계산·저장, 요금 확인, 관리자 화면에서 큰 문제 없음. 나온 피드백은 PR #104로 반영(5절). 배포 뒤 다시 확인
- [x] `BACKEND_BASE_URL` 시크릿 등록(Cloudtype 기본 공개 주소, https, 경로 없이). 값 확인은 `popularity` 워크플로 수동 실행으로 대신한다
- [ ] `CRON_ENABLED` 켜기(PR #104 배포·확인 뒤), 일요일 WEEKLY를 켤지 결정(지금은 켜지 않음)
- [x] CPU 증설 결정: 증설하지 않음(5절)
- [ ] PR #104 병합 → `develop`→`main` 병합 → Cloudtype `배포하기`(기동 5~6분). 배포 뒤 `popularity_snapshot` 삭제 → `popularity` 워크플로 수동 실행 → 결과가 한국 작품인지 확인(`with_origin_country=KR` 값 형식 확인)
- [ ] 배포 뒤 확인: 독점 목록이 새 순서로 나오는지(캐시 이름 `exclusiveTitlesV2`), 계산 중 진행 창 모양과 Esc 동작, 진행 창이 열릴 때 낭독이 두 번 읽히지 않는지(검수 지적 6)
- [ ] 문서: 캐시 이름 `exclusiveTitles` → `exclusiveTitlesV2`를 `CLAUDE.md`, `ROADMAP.md`(641·1459·1467행), `TECH.md`, `PACKAGE_MAP.md`에 반영. 인기 순위 의미 변경을 ROADMAP Task 107과 HANDOFF에 반영. 092~095 진행 기록과 `CLAUDE.md` 현황 문구 갱신
- [ ] 개인정보처리방침 화면(`/privacy`)과 Google 동의 화면 Branding 입력·프로덕션 게시
- [ ] 검색 엔진 등록(공개 시점), `main`에 `v0.1.0` 태그
- [ ] 로그 정리: `spring.data.redis.repositories.enabled`, `HV000271` 경고
- [ ] 상품 가격 재확인(조사일 2026-10-05, 쿠팡플레이는 비공식 자료)
