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

- 운영이 로컬의 약 1.8~2배 느리다. 대역폭(100Mbps)보다 vCPU 0(공유 CPU)이 원인일 가능성이 크다 `[확인 못함: Cloudtype CPU 그래프]`.
- 첫 전체 수집 예상 소요: 약 11~12시간(로컬 6시간 17분의 약 1.9배, 추정). 실제 값은 `[첫 수집 뒤 기록]`.
- 이후 단계(`movieDetail`, `tvDetail`, 시즌, 판정)의 시간·최대 메모리 `[첫 수집 뒤 기록]`.

## 5. 결정과 근거

- **메모리 1GB**: 기동 직후 302~426MB, 엔진 최악 입력의 힙 457~507MB(`-Xmx512m`에서 완주), 수집 청소 후 힙 최대 67MB(로컬). 0.5GB는 여유 없음, 1.5GB는 근거 없음. 드라마 상세·시즌 구간의 최대 메모리는 첫 수집에서 확정
- **`JAVA_TOOL_OPTIONS=-Xmx512m`**: 컨테이너의 50~60% 원칙. 수집 뒤 조정
- **CPU 증설**: 보류. 수집이 끝난 뒤 기동 시간·응답 시간·일상 수집 시간을 보고 정한다. 선결제·월 자동 결제라 일할 계산 여부를 먼저 확인 `[확인 못함]`
- **Cloudtype 도메인 연결**: 하지 않음. Vercel이 `/api`를 넘기는 구조라 필요 없다
- **상품·가격**: 조사일 2026-10-05 값으로 17개 입력(단품 7, 저가형 단품 5, 번들 5). 관리자 API와 같이 구성 서비스 `tier`는 모두 `STANDARD`, 광고형·베이직은 `low_cost=true`. 가격은 공개 전에 한 번 더 확인
- **`CRON_ENABLED`**: 첫 수집이 끝나고 스모크 통과 뒤에 만든다

## 6. 알게 된 점 (수집)

- 수집 Job은 서버 기동 때 자동 실행되지 않고(`spring.batch.job.enabled: false`) 관리자 API로만 시작한다. 재배포가 수집을 다시 돌리지 않는다. 다만 수집 중 재배포하면 중단된다
- 수집·인기 수집·재계산·정리는 서로 동시에 시작할 수 없다(409 `BATCH_ALREADY_RUNNING`)
- `tier=WEEKLY`는 후보 발견까지 전체 흐름을 돌고 상세·판정 대상이 "찜하지 않은 나머지 전부"다. 초기에는 거의 전부가 WEEKLY라 ALL과 규모가 비슷하다
- 상세 단계는 `fetched_at`으로 거르지 않고 전부 다시 받는다. 방영 중 드라마는 회차가 늘어 상세가 바뀌므로 "최근에 받았으면 생략"은 안 맞는다. 개봉한 지 오래된 영화와 종영된 드라마만 생략하는 방안을 후속으로 검토

## 7. 남은 일

- [ ] 첫 수집 완료 뒤 4절 빈칸 채우기(단계별 시간, 최대 메모리, 실패 건수, DB 용량)
- [ ] 스모크: 로그인 유지, 검색, 찜, 플랜 계산·저장, 요금 확인, 관리자 화면
- [ ] `BACKEND_BASE_URL` 시크릿 등록(주소 확인 뒤), `CRON_ENABLED` 켜기, 일요일 WEEKLY를 켤지 결정
- [ ] CPU 증설 결정, 다음 결제일 확인
- [ ] 개인정보처리방침 화면(`/privacy`)과 Google 동의 화면 Branding 입력·프로덕션 게시
- [ ] 검색 엔진 등록(공개 시점), `main`에 `v0.1.0` 태그
- [ ] 로그 정리: `spring.data.redis.repositories.enabled`, `HV000271` 경고
- [ ] 상품 가격 재확인(조사일 2026-10-05, 쿠팡플레이는 비공식 자료)
