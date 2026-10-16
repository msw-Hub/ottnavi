# 세션 이어가기 (HANDOFF)

> 2026-10-13 작성. 계정이나 대화 세션이 바뀌어도 작업을 이어가기 위한 문서다. 진행 상황의 기준은 `docs/ROADMAP.md`이고, 이 문서는 ROADMAP에 흩어져 있거나 대화에만 있던 내용을 한곳에 모은 것이다. 오래되면 ROADMAP과 `git log`가 기준이다.
> 작업 규칙(커밋·PR·에이전트·설명 방식)은 `CLAUDE.md`의 "작업 시 지킬 것"과 "협업 방식"에 있다. 새 세션은 먼저 CLAUDE.md, 이 문서, ROADMAP 해당 Task를 읽는다.

## 1. 현재 상태 (2026-10-16 기준)

| 항목 | 내용 |
|---|---|
| 진행 | Phase 1 22/23, Phase 2 14/14 ✅, Phase 3 25/30, 합계 61/105 (045~051·053~056·059·060·062~065 병합, 066은 구현·검증을 마치고 PR 전. 048은 R11-25·26 확정으로 완료, 드라마 구간 최대 힙 재측정은 Task 095) |
| 완료한 최근 Task | 031 계약, 032~035 스키마·엔티티, 036~037 디자인, 038~040 엔진, 041 TMDB 클라이언트, 042 판정·러닝타임 규칙, 043 수집 Job 후보 단계, 044 상세·시즌·판정 Step, 045 수집 관리자 API, 046 제공처 매핑 API, 047 수집 cron(비활성), 049 공개 API 요청 제한, 050 작품 검색 API, 051 작품 상세 API(050·051은 DB 조회만, 단건 수집은 이월) |
| 열린 PR | 066은 `feature/b7-plan-activate`(백엔드 워크트리)에서 PR을 올린다. 병합된 것: 065 PR #72(`0819c96`), 064 PR #71(`e5745fc`), 062·063 PR #70, 059·060 PR #69, 050+051 PR #65(`3dc136e`), 순서 변경 문서 PR #66(`0967a03`), 053·054 PR #67(`91d83dd`), 055·056 PR #68(`9b0a31c`) |
| 다음 Task | 064(플랜 계산 API)는 PR #71, 065(비교 측정 API)는 PR #72로 병합했고 066(`POST /api/me/plans` 저장, `GET /api/me/plan-drafts` 초안 조회, `GET /api/me/plans/current` 현재 플랜 조회)은 구현·테스트를 마쳤다(2026-10-16, PR 전). 이로써 계약의 `x-planned` operation은 0개이고 구현 API는 33개다. 다음은 **067**(요금 확인·계산·결과 흐름 실제 연결, 프론트)이고 프론트 실제 연결(052·057·058·061·067)은 묶어서 한다. 매월 재계산(Task 069)·SUBSCRIBE_GUIDE outbox 행 생성(Task 073)은 066에서 하지 않았다(R-6). 058·061에서 맞출 것: 목업이 서버와 다른 점(구독 중이 아닌데 planName, PATCH 빈 요청·경로 ID 400, 오류 우선순위, 소수 금액 400) |
| 쉬림프 | 045까지 완료 처리, 046·047 등록됨(PR 병합 후 `verify_task`). 앞으로 Task는 착수할 때 등록하고 `execute_task`, 끝나면 `verify_task`(점수 90 안팎) |
| 워크트리 | `ottnavi`(메인, `develop` 유지·최신화, 프론트·문서), `ottnavi-backend`(**백엔드 작업은 항상 여기서 한다**, 2026-10-14 사용자 결정. 현재 `feature/b3-collect-admin-api`), `ottnavi-docs`(문서 작업, 옛 브랜치), `ottnavi-backend-test`(옛 브랜치). 같은 브랜치를 두 워크트리에서 동시에 열 수 없다 |

## 2. 다음 작업 순서

1. (완료) PR #61은 2026-10-14에 Squash 병합했고 메인 워크트리 `develop`을 최신화했다.
2. (완료) **Task 045**(수집 실행·복구·이력 관리자 API)는 PR #62로 2026-10-15 병합했다(결정과 기록은 ROADMAP Task 045, 설명은 `docs/guides/COLLECT_GUIDE.md` 11절, `COLLECT_ADMIN_QA.md`). **Task 046+047**(제공처 매핑 API, 수집 cron 워크플로 비활성)은 PR #63, **Task 048+049**(첫 전체 수집 측정 기록, 공개 API 요청 제한)는 PR #64로 병합했다. **Task 050+051**(검색·상세 API)은 `feature/b4-title-search-detail`에서 구현·검증을 끝냈고 PR이 남았다(DB 조회만 하며 단건 수집은 후속 Task로 이월, ROADMAP Task 050 기록).
3. (완료) 048 결정 마무리: R11-25 기본 러닝타임 영화 100분·회차 43분, R11-26 MVP는 Supabase 무료 유지. 최대 힙 재측정은 Task 095. **052(공개 화면 실제 연결)는 백엔드 055·056 이후로 미뤘다**(2026-10-16, 임시 목업 분기와 V-FS 중복을 피한다. 탐색 결과는 ROADMAP Task 052).
4. 인증: 053·054(구현 완료) → 057(프론트 인증 연동, 인증 코드는 사용자 검토). 회원 입력: 055·056 → 058. 요금·관리자: 059·060 → 061. 플랜: 062~066(백엔드 구현 완료) → 067.
5. 2단계 화면 목업(068·071·075·081·086·097)은 Task 095(MVP 공개) 이후.
6. 프론트에는 지금 독립 작업이 없다. 프론트 Task(052, 057, 058, 061, 067)는 백엔드 API가 먼저 필요하고, API가 갖춰진 뒤 묶어서 연결한다.

## 3. 사용자가 직접 할 일 ([H])

- **Task 005 도메인 구매**: Task 092 직전, 2026-11-04 이전. WHOIS 개인정보 보호, 등록업체 2단계 인증, 연장 안내는 링크가 아니라 업체 사이트 직접 접속, 카카오톡 알림(ROADMAP Task 005).
- **Redis 비밀번호·TMDB 토큰 재설정은 하지 않기로 했다**(2026-10-16 사용자 결정: 채팅에 노출됐지만 대화를 여러 번 압축·초기화했고 중요한 값이 아니라고 판단). 운영 공개 전(Task 092 최종 점검)에 한 번 더 판단한다.
- (완료 2026-10-16) **Google OAuth 값 확인**: 클라이언트는 Task 002에서 만들었고, 로컬 `.env`에 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`이 있고 승인된 리디렉션 URI에 `http://localhost:5173/api/login/oauth2/code/google`이 등록돼 있음을 사용자가 확인했다. 운영 URI는 Task 092 최종 점검 때 추가한다.
- **TMDB 공식 로고 파일 준비(Task 052 전, 선택)**: https://www.themoviedb.org/about/logos-attribution 에서 받아 `frontend/public/`에 둔다. 없으면 About은 자리표시 박스를 유지한다.
- Supabase Data API는 꺼 둔 상태다(2026-10-13 사용자 확인).
- **배포 뒤 운영 비교 측정 1회(Task 065, MVP 완료 기준 ③)**: 배포 뒤 운영에서 `POST /api/admin/plan-benchmarks`를 ADMIN 토큰으로 한 번 호출해 운영 DB `calc_run`에 EXACT·GREEDY 행을 남기고, Cloudtype 측정 시간을 ROADMAP Task 065 결과표에 추가한다(Task 100의 R11-30 참고값). 토큰 값은 이 문서에 적지 않는다.

## 4. 미결·이월 항목 (한곳에 모음)

**Task 044는 완료했다**(2026-10-14, 결정과 기록은 ROADMAP Task 044). 이월 질문(DTO 변환, 방영 0회 시즌, `episode_count` 출처, 영어 줄거리, `MAPPED`만 판정, `Availability` 플래그 검사, 이력·낙관적 락, `WatchUnit` 갱신, `collect/step` 분리, Reader 정렬)은 모두 결정·구현됐다. 남은 것은 아래 048 항목이다: 실제 TMDB 응답으로 수집, chunk 안 TMDB 호출 동안의 연결 점유 확인. 날짜가 다른 Job의 동시 실행(상세 Reader 포함)은 Task 045의 전체 동시 실행 금지로 해결했고, 5xx로 실패하면 관리자 API의 restart(또는 같은 날짜로 다시 시작)로 이어서 한다.

**Task 045로 넘겼던 항목은 해소됐다**(PR #62 병합): 다른 날짜의 동시 실행은 전체 동시 실행 금지로, 완료·실행 중 409 매핑과 `tier` enum `ALL`은 구현·계약에 반영, 비동기 실행은 수집 전용 비동기 `JobOperator`로 해결했다.

**배포 게이트(Task 046)**: 제공처 매핑 변경 API(`PATCH /api/admin/providers/{providerId}`)는 Task 054 전까지 인증이 없다(local 전용). **Task 054 완료 전에는 `main`에 병합·배포하지 않는다**(ROADMAP 순서상 MVP 배포 092 이후라 문제없다. 먼저 배포해야 하면 이 컨트롤러를 막는 조건을 추가한다).

**배포 전 사용자 확인(Task 045·047)**: GitHub 저장소 시크릿 `BACKEND_BASE_URL`·`ORIGIN_SECRET`·`ADMIN_BATCH_TOKEN`은 Task 093(cron 활성화) 때 등록한다(수집 워크플로 `collect.yml`이 읽는다). Cloudtype에 `ADMIN_BATCH_TOKEN`이 아직 등록돼 있지 않다. Cloudtype 환경 변수는 **배포할 때만 입력할 수 있어서**, `main` 배포 단계에서 값을 넣어야 한다(값이 없으면 prod가 기동하지 못한다. 이 값은 GitHub Actions 시크릿 `ADMIN_BATCH_TOKEN`과 같아야 한다). 배포 단계에서 사용자에게 알린다. 재배포 때 옛 컨테이너와 새 컨테이너가 겹쳐 도는지도 확인한다(겹치면 "전체 동시 실행 금지"의 서비스 락이 두 서버에 걸치지 않아 수집이 동시에 돌 수 있다. 확인 전까지 알려진 한계).

**인증(Task 053·054) 이월·확인 항목**: ① Redis Cloud 무료 플랜에서 Lua(`EVAL`) 실호출을 확인한다(문서상 Redis Cloud Standard는 EVAL·EVALSHA·SCRIPT LOAD를 지원하므로 배포 스모크에서 로그인·재발급으로 확인). ② 운영 배포 전 Cloudtype 환경 변수 `JWT_SECRET`·`GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`APP_FRONTEND_ORIGIN`·`OAUTH2_REDIRECT_BASE_URL`을 입력한다(배포 때만 입력 가능). ③ Google 콘솔에 운영 리디렉션 URI `https://ottnavi.vercel.app/api/login/oauth2/code/google`를 추가한다(Task 092). ④ 057(프론트)로 넘길 것: `npm run api:generate`(`AccessToken.role`), `/auth/callback`에서 refresh 호출과 `?error=login_failed` 처리, 401이면 재발급 한 번 뒤 원요청 재시도, 재발급 호출 합치기(single-flight), 재발급이 500이면 로그인 화면으로 보내지 않기, 로그아웃 때 메모리 AT 폐기, 요청의 `credentials` 설정. ⑤ Lettuce 예외 메시지에 키(familyId)가 로그에 남는지 확인한다. ⑥ `AuthUser.role`이 `UserRole` enum이라 `global.security`가 `user` 패키지를 참조한다(계층 허용을 결정하거나 문자열로 바꾼다). ⑦ 로그인 브라우저 확인(Task 053 완료 기준)은 사용자가 한다.

**인증 브라우저 확인 결과(2026-10-16, 사용자)**: 서버 쪽 로그인·재발급(연속 3회 200)·로그아웃(204 후 401)·`/api/me/settings` 404/401·관리자 API 403·`users` 계정당 1행·신규 가입은 USER로 확인했다. 인가 요청 쿠키 수명은 180초 그대로 두기로 했다(사용자 결정). **주의**: 프론트 `AuthCallbackScreen`은 아직 목업 전용이라 목업이 아닌 환경에서는 서버 결과와 상관없이 항상 "로그인에 실패했습니다"를 보인다(Task 057에서 실제 `POST /api/auth/refresh` 연동으로 교체). 그래서 실제 로그인 직후 "로그인 실패" 화면이 떠도 서버 로그인은 성공한 것이다. 로컬 ADMIN 지정은 DB에서 `UPDATE users SET role='ADMIN' WHERE email=...`로 하고 다음 재발급 때 반영된다(운영 지정은 Task 095 ②).

**Task 048로**: 인기도·평점 UPDATE 부하 측정, TMDB discover 500쪽(1만 건) 상한에 걸리는 서비스 여부, `|`(OR) 구분자의 TMDB 공식 문서 확인, 운영 환경 연결 점유, 약 2분 추정 실측.

**Task 050·055·056·063·064·066·069로**: 같은 새 작품을 동시에 검색하면 `uk_title_media_tmdb`로 500이 날 수 있음(050), 검색 단건 수집 한도 소진 시 응답(050), 플랜 계산 입력은 한 플랜의 찜·달만 묶는다와 월 번호 0~2(063), 소속 일치 통합 테스트(064), `previous_plan_id`는 같은 사용자의 플랜(069), ACTIVE 교체 순서(066은 구현·테스트 완료, 069에서 같은 순서 재사용), `change_summary` 형식(069).

**프론트(Task 037에서 확인하지 못한 것)**: 작품 상세·찜 추가 다이얼로그의 44px 모양, 모바일 다이얼로그 닫기 버튼과 제목 겹침, 관리자·`/login`·`/settings`·`/pricing` 시각 회귀, 푸터 가운데 정렬의 눈 확인, `WISHLIST_LIMIT_EXCEEDED` 409의 실제 브라우저 확인. 전체 `npm run test`가 화면 테스트 5초 타임아웃으로 가끔 실패(단독 재실행은 통과, 원인 미확정).

**화면 문구 결정(추천안 적용 중)**: 요금 확인일 표기, 90일 초과 안내 문구, 시즌 순서 줄 노출 조건. PRD 11절에 UNKNOWN 확정 항목을 추가할지(추천 추가). 수집 이력 100건 한계는 써 보고 판단.

**기타**: 현재 플랜 조회 API는 Task 066에서 구현됐다(`getCurrentPlan`, 저장한 플랜이 없으면 200에 `plan: null`). 내 정보·역할 조회 API(057), TMDB 실제 응답 확인(소비 Task 전에 로컬에서 1회 호출: Retry-After, 한국어 줄거리 `""`/null 등), `BaseTimeEntity` 주석("기본 매핑은 TIMESTAMP")이 Hibernate 7에서 사실인지(`@JdbcTypeCode` 없이도 `validate` 통과 확인됨, 주석 정정 필요), `Instant` 필드에는 일관성을 위해 어노테이션을 유지, 고도화 후보(인물·장르 검색, 메인 추천작: PRD 미반영, `title_cast.person_tmdb_id`·이름과 `title_genre.genre_id` 인덱스 상기), `domain/rule` 이름은 유지, Javadoc 보충은 하지 않기로 함(CodeRabbit Docstring Coverage 경고는 무시), `OttServiceQueryService` 정리는 #60에서 완료.

**ARCHIVED 플랜 보관 정책(2026-10-16 사용자 방침)**: MVP에서는 개수·기간 제한 없이 쌓는다(문서에 규칙 없음). 2단계(매월 재계산·변경 내역) 설계 때 **최근 N개만 유지**(저장·재계산 시 오래된 ARCHIVED 벌크 삭제)로 정한다. N은 그때 결정한다.

**로컬 정리(요청받을 때만)**: 병합된 로컬 브랜치와 옛 워크트리(`feature/f1-*`, `feature/f2-design-tokens`, `feature/b0-*`, `feature/b2-engine-*`, `feature/docs-*` 등), 워크트리 `ottnavi-docs`·`ottnavi-backend-test`는 병합이 끝난 옛 브랜치 상태다.

## 5. 환경과 도구 함정 (Windows 11)

- 셸 두 가지가 있다. Bash(Git Bash)와 PowerShell. Bash의 heredoc에 한글·따옴표가 섞이면 깨질 수 있으니 긴 파일은 Write 도구로 쓴다. `python`은 쓸 수 없고 node로 스크립트를 쓴다(줄바꿈 CRLF를 읽고 되돌려 쓸 때 보존한다).
- `gradlew`: Bash의 `./gradlew`는 된다. PowerShell의 `.\gradlew.bat`에서 한글 테스트 이름 필터는 따옴표로 감싼다. 사용자는 PowerShell에서 `.\gradlew.bat test --tests "..."`를 쓴다(`--tests` 붙여 쓰기).
- Docker: Bash에서 경로 변환을 막으려면 `MSYS_NO_PATHCONV=1`. 통합 테스트(Testcontainers)는 Docker Desktop이 켜져 있어야 한다.
- 저장소가 `core.autocrlf=true`라 작업 트리는 CRLF, 커밋은 LF다. `sed`로 대량 치환하면 줄바꿈이 깨질 수 있다(확인 필수).
- `git mv`로 폴더를 옮기면 **빈 폴더가 디스크에 남는다**(Git은 추적하지 않지만 IDE에 보인다). 이동 직후 빈 폴더를 지운다.
- 테스트 결과: `backend/build/test-results/test/*.xml`(합계), 보기 쉬운 HTML은 `backend/build/reports/tests/test/index.html`(워크트리마다 따로, 마지막으로 돌린 것만). 빌드가 UP-TO-DATE로 끝나면 `./gradlew test --rerun-tasks`로 새로 돌린다.
- 커밋 전 `git status --short`에서 `AM`(스테이지 후 수정)·`AD`가 있으면 옛 내용이 커밋된다. `git add backend/src ...`로 경로를 명시해 다시 스테이지하고, 커밋하면 안 되는 안내서(`docs/TASKxxx_GUIDE.md`)는 넣지 않는다.
- 비밀 값(`.env`, 토큰, 비밀번호)은 읽거나 출력하거나 커밋하지 않는다. 사용자 PC의 프로세스·게임은 조회하지 않는다.
- MCP(playwright, shadcn, shrimp)가 자주 끊긴다 → 사용자에게 `/mcp` 재연결 요청.

## 6. 문서 목록(설명용, 근거 아님)

설명용 문서는 모두 `docs/guides/` 폴더에 있다: `COLLECT_GUIDE.md`(수집 Job의 Step 6개, 후보와 판정의 차이, 읽는 순서), `COLLECT_ADMIN_QA.md`(수집 관리자 API 이해 노트: `BATCH_*` 표, 조회 방식, 락과 트랜잭션, 용어), `SPRING_BATCH_BASICS.md`(Job·Step·chunk·Reader·Processor·Writer), `AVAILABILITY_RULES.md`(제공 상태 판정·러닝타임), `PACKAGE_MAP.md`(백엔드 폴더 지도), `ENGINE_ALGORITHM.md`(계산 엔진), `FLYWAY_GUIDE.md`(Flyway와 엔티티로 테이블 만들던 방식의 차이), `JPA_COMPOSITE_KEY_GUIDE.md`(복합 기본키), `OPENFEIGN_GUIDE.md`(TMDB 클라이언트). 새 설명 문서는 짧게 쓰고 `docs/guides/`에 두어 PR에 함께 커밋한다.
