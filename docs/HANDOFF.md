# 세션 이어가기 (HANDOFF)

> 2026-10-13 작성. 계정이나 대화 세션이 바뀌어도 작업을 이어가기 위한 문서다. 진행 상황의 기준은 `docs/ROADMAP.md`이고, 이 문서는 ROADMAP에 흩어져 있거나 대화에만 있던 내용을 한곳에 모은 것이다. 오래되면 ROADMAP과 `git log`가 기준이다.
> 작업 규칙(커밋·PR·에이전트·설명 방식)은 `CLAUDE.md`의 "작업 시 지킬 것"과 "협업 방식"에 있다. 새 세션은 먼저 CLAUDE.md, 이 문서, ROADMAP 해당 Task를 읽는다.

## 0. 2026-10-17 범위 개정과 인수인계 요약 (먼저 읽는다)

계정이 바뀌어 이어가는 시점의 요약이다. 아래 1~4절은 2026-10-16 기준이라 이 절과 어긋나면 이 절이 우선이다.

- **첫 공개 = `v0.1.0`, 2단계 일부 포함**(PRD 11절 42번, 사용자 결정). 변경 내역(068~070)·번들·독점(075~079)·탈퇴(080~084)·정리(089·090·101)·홈·탐색(신설 106~108)이 공개 전이고, 알림(071~074)·LLM(085~088)·관측(096~099)·성능(100)·정확도 보강(103)은 공개 뒤 3단계로 이월했다. 102(`v0.2.0`)는 3단계 공개 태그로 재정의했다. Task는 총 108개이고 진행은 66/108이다.
- **묶음 순서**: 0 연결 마무리(`f-api-connect` PR·코드래빗·병합, 사용자 실서버 확인) → 1 홈·탐색(106 목업 → 107 API → 108 연결) → 2 변경 내역(068~070, **069는 opus 설계 먼저**) → 3 번들·독점(075~079, **076은 opus 설계 먼저**) → 4 탈퇴(080은 사용자 결정, 081·082·084는 한 PR로 묶기 가능) → 5 정리(089·090·101) → 6 공개(092~095). 추정 시점은 11월 중순~하순이다(옛 추정 10월 27일~11월 5일은 폐기).
- **열린 브랜치**: `feature/f-api-connect`는 원격에 `3afb747`까지만 푸시돼 있고 `d165253`, `66a951d`는 푸시 전이다. PR은 아직 없다. `feature/f-home`(워크트리 `ottnavi-frontend`)은 `f-api-connect` 위에 쌓은 홈 1차안(Task 106, 미커밋 변경)이다. 병합은 `f-api-connect` 먼저다. Squash 병합 뒤에는 `git rebase --onto origin/develop <옛 f-api-connect 끝 커밋> feature/f-home`으로 옮겨 별도 PR을 올린다.
- **Task 107 인기 순위 설계(확정)**: TMDB `popularity`는 그날 값 하나만 주고 `trending`은 서비스·지역 필터가 없어서 우리가 만든다. 오늘 순위 = 서비스 7곳 × 영화·드라마별 `discover`(`watch_region=KR`, 서비스 필터, 인기순) 하루 1회(약 14호출). 최근 7일 = 일별 스냅샷 표(7~14일 보관)의 7일 평균. 가벼운 별도 수집 Job이고 새 Flyway 마이그레이션(V8 이후)이 필요하다. 매일 수집이 돌아야 하므로 Task 093(cron 활성화)이 필요하다. 공개 직후 7일 미만이면 있는 날 수만큼 평균한다. 화면 표기는 "TMDB 인기도 기준, OTT 공식 순위 아님". 107 설계에서 확정할 것: "신작"의 뜻(서비스에 새로 들어온 작품, 첫 적재분 제외로 가정), 찜 최소 표시 인원, 사용자가 적을 때 목록을 숨길지 인기작으로 채울지.
- **홈 1차안 상태(Task 106)**: 프론트 검증 4종(lint, format:check, test 47파일 535건, build)과 브라우저 콘솔 에러 0건을 확인했다. 홈 샘플 작품은 가상(tmdbId 9100xx)이라 카드를 누르면 상세가 "찾을 수 없음"이다. 홈 카드 찜은 임시 상태이고 `?homePreview=`, `features/home/mockTypes.ts`·`sampleData.ts`는 108에서 지운다. 검색 저장 뒤 이동 경로(`SettingsForm`, `useLogout`)가 이제 홈으로 가는 것이 맞는지 108에서 확인한다. 모바일 헤더는 관리자·개발용 전환이 겹치면 3줄이 될 수 있다. 최종 UI 방향(레이아웃·문구·정보 구조)은 사용자 결정이고 나머지 중간 사안은 추천대로 진행한다.
- **관리자 화면 확인**: 구글 로그인 가입자는 항상 `USER`이고 `ADMIN`은 DB의 해당 사용자 `role`을 직접 바꿔야 한다. 목업 모드(`VITE_USE_MOCK=true`)의 "개발용" 역할 전환은 화면 확인용 가짜 스위치라 DB를 바꾸지 않고 운영·실제 모드에서는 보이지 않는다.
- **환경 함정(새 워크트리)**: `ottnavi-frontend`에는 git 제외 파일 `frontend/.env.local`이 없다. 목업으로 볼 때는 `$env:VITE_USE_MOCK="true"; npm run dev`처럼 명령 앞에 붙인다. `npm run api:generate`는 줄바꿈 노이즈가 커서 `git diff --ignore-cr-at-eol`로 실제 변경을 본다. Playwright 스크린샷은 메인 워크트리 `.playwright-mcp\`에 저장된다. 에이전트가 개발 서버를 안 끄고 끝나는 일이 있으니 `Get-NetTCPConnection -LocalPort <포트>`로 PID를 찾아 종료한다.
- **사용량 한도**: opus는 위험 Task(069, 076)에만, 병렬 에이전트는 쓰지 않고 한 Task씩 완결한다. 에이전트가 한도로 중단되면 `SendMessage`로 이어서 마무리시킨다.

## 1. 현재 상태 (2026-10-16 기준)

| 항목 | 내용 |
|---|---|
| 진행 | Phase 1 22/23, Phase 2 14/14 ✅, Phase 3 30/30 ✅, 합계 66/108 (106~108 신설, 0절) (백엔드 045~051·053~056·059·060·062~066 병합, 프론트 연결 052·057·058·061·067은 구현·테스트를 마치고 PR 전. 048은 R11-25·26 확정으로 완료, 드라마 구간 최대 힙 재측정은 Task 095) |
| 완료한 최근 Task | 031 계약, 032~035 스키마·엔티티, 036~037 디자인, 038~040 엔진, 041 TMDB 클라이언트, 042 판정·러닝타임 규칙, 043 수집 Job 후보 단계, 044 상세·시즌·판정 Step, 045 수집 관리자 API, 046 제공처 매핑 API, 047 수집 cron(비활성), 049 공개 API 요청 제한, 050 작품 검색 API, 051 작품 상세 API(050·051은 DB 조회만, 단건 수집은 이월) |
| 열린 브랜치 | `feature/f-api-connect`(워크트리 `ottnavi-frontend`, **PR 전, 푸시 예정**): 계약 33개 반영 생성물 갱신 `8e08d66`, 057 인증 `cb3206f`·권한 없음 홈 링크 보정 `69f76b1`, 058 온보딩·찜 `8109b73`, 061 관리자 `e2c6734`, 067 플랜 흐름 `760b0c6`·스타일 보정 `59725b5`, 052 공개 화면 `32c5811`, 그리고 이 문서 갱신 커밋. 병합된 것: 066 PR #73(`b2380fb`), 065 PR #72(`0819c96`), 064 PR #71(`e5745fc`), 062·063 PR #70, 059·060 PR #69, 050+051 PR #65(`3dc136e`), 순서 변경 문서 PR #66(`0967a03`), 053·054 PR #67(`91d83dd`), 055·056 PR #68(`9b0a31c`) |
| 다음 Task | 064·065·066은 PR #71·#72·#73으로 병합했고 계약의 `x-planned` operation은 0개, 구현 API는 33개다. 프론트 실제 연결 052·057·058·061·067은 2026-10-16 밤에 구현·테스트까지 마쳤고(RTL·목업 기준, 실서버·실제 Google 로그인은 **미확인**) PR 전이다. 이 PR을 병합하면 Phase 3 완료로 처리한다. 그다음은 Phase 4(2단계 화면 목업 068·071·075·081·086·097 등)와 Phase 5(MVP 배포 Task 092·095 등)이고 순서는 ROADMAP 3.2·각 Phase를 따른다. 매월 재계산(Task 069)·SUBSCRIBE_GUIDE outbox 행 생성(Task 073)은 066에서 하지 않았다(R-6) |
| 쉬림프 | 045까지 완료 처리, 046·047 등록됨(PR 병합 후 `verify_task`). 앞으로 Task는 착수할 때 등록하고 `execute_task`, 끝나면 `verify_task`(점수 90 안팎) |
| 워크트리 | `ottnavi`(메인, `develop` 유지·최신화, 프론트·문서), `ottnavi-backend`(**백엔드 작업은 항상 여기서 한다**, 2026-10-14 사용자 결정. 현재 `feature/b3-collect-admin-api`), `ottnavi-frontend`(프론트 연결 `feature/f-api-connect`), `ottnavi-docs`(문서 작업, 옛 브랜치), `ottnavi-backend-test`(옛 브랜치). 같은 브랜치를 두 워크트리에서 동시에 열 수 없다 |

## 2. 다음 작업 순서

1. (완료) PR #61은 2026-10-14에 Squash 병합했고 메인 워크트리 `develop`을 최신화했다.
2. (완료) **Task 045**(수집 실행·복구·이력 관리자 API)는 PR #62로 2026-10-15 병합했다(결정과 기록은 ROADMAP Task 045, 설명은 `docs/guides/COLLECT_GUIDE.md` 11절, `COLLECT_ADMIN_QA.md`). **Task 046+047**(제공처 매핑 API, 수집 cron 워크플로 비활성)은 PR #63, **Task 048+049**(첫 전체 수집 측정 기록, 공개 API 요청 제한)는 PR #64로 병합했다. **Task 050+051**(검색·상세 API)은 `feature/b4-title-search-detail`에서 구현·검증을 끝냈고 PR이 남았다(DB 조회만 하며 단건 수집은 후속 Task로 이월, ROADMAP Task 050 기록).
3. (완료) 048 결정 마무리: R11-25 기본 러닝타임 영화 100분·회차 43분, R11-26 MVP는 Supabase 무료 유지. 최대 힙 재측정은 Task 095. **052(공개 화면 실제 연결)는 백엔드 055·056 이후로 미뤘다**(2026-10-16, 임시 목업 분기와 V-FS 중복을 피한다. 탐색 결과는 ROADMAP Task 052).
4. (완료, PR 전) 프론트 연결: 057(인증, 인증 코드는 사용자 검토) · 058(온보딩·찜) · 061(관리자) · 067(플랜 흐름) · 052(공개 화면)을 `feature/f-api-connect` 한 브랜치에 모았다. 백엔드 053~066은 모두 병합됐다.
5. 지금 할 일 순서: (1) 브랜치 푸시 → PR 올리기(`gh pr create --base develop`) (2) CodeRabbit 지적 정리(`coderabbit-triage`, 반영은 한 번에 모아 푸시) (3) 사용자가 아침에 실서버 확인(3절 시나리오)과 4절의 UI 결정 (4) 병합(Squash) 뒤 Phase 3 완료 처리(ROADMAP 진행표는 이미 30/30으로 적혀 있으므로 PR 번호만 기록) (5) 이어서 ROADMAP 기준으로 다음 Task(Phase 4·5).
6. (2026-10-17 개정) 2단계 화면 목업 중 068·075·081은 첫 공개 전(0절 묶음 순서)이고, 071·086·097은 공개 뒤 3단계로 이월했다.

## 3. 사용자가 직접 할 일 ([H])

- **Task 005 도메인 구매**: Task 092 직전, 2026-11-04 이전. WHOIS 개인정보 보호, 등록업체 2단계 인증, 연장 안내는 링크가 아니라 업체 사이트 직접 접속, 카카오톡 알림(ROADMAP Task 005).
- **Redis 비밀번호·TMDB 토큰 재설정은 하지 않기로 했다**(2026-10-16 사용자 결정: 채팅에 노출됐지만 대화를 여러 번 압축·초기화했고 중요한 값이 아니라고 판단). 운영 공개 전(Task 092 최종 점검)에 한 번 더 판단한다.
- (완료 2026-10-16) **Google OAuth 값 확인**: 클라이언트는 Task 002에서 만들었고, 로컬 `.env`에 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`이 있고 승인된 리디렉션 URI에 `http://localhost:5173/api/login/oauth2/code/google`이 등록돼 있음을 사용자가 확인했다. 운영 URI는 Task 092 최종 점검 때 추가한다.
- **TMDB 공식 로고 파일 준비(Task 052 마무리, 선택)**: https://www.themoviedb.org/about/logos-attribution 에서 받아 `frontend/public/`에 둔다. 없으면 About은 자리표시 박스를 유지한다. 파일을 두면 `AboutScreen`의 자리표시를 교체한다(Task 003 238행의 사용 조건은 아직 확인하지 않았다).
- **아침 실서버 확인 시나리오(프론트 연결 PR 전, 2026-10-16 밤 구현분)**: 에이전트는 실서버·실제 Google 로그인을 확인하지 못했다. 백엔드(local)와 프론트(`VITE_USE_MOCK` 끔)를 띄우고 순서대로 본다.
  1. 로그인: Google 로그인 → `/auth/callback` → 첫 로그인이면 `/settings`로 가는지(`isOnboardingDone=false` 분기)
  2. 새로고침 유지: 새로고침해도 로그인이 유지되는지(앱 시작 때 refresh)
  3. 401 재발급: AT 만료 뒤(짧은 TTL로) 요청이 재발급 1회 → 재시도로 성공하는지, 로그아웃 후 보호 화면은 로그인으로 가는지
  4. 관리자: ADMIN 계정(DB에서 `role` 변경 후 재발급)으로 상품·가격(겹침 오류 포함), 제공처 매핑, 수집 202/409, 변경 작품. USER 계정은 권한 없음 화면
  5. 설정·찜: 온보딩 저장 → 시즌 찜 → 우선순위 변경 → 다 봤음 → 새로고침 후 유지, 21번째 찜 409
  6. 플랜: 요금 확인·수정 → 계산 → 결과 → 저장 → 새로고침 → "내 현재 플랜", 화면 금액이 API 응답과 같은지
  7. 409·404 재현: 409는 계산 뒤 `targetDate`를 다른 날짜로 지정해 수집을 다시 실행하고 저장(ROADMAP Task 067 방법), 404는 두 탭에서 한쪽이 저장한 뒤 다른 탭이 저장
  8. 공개: 비로그인 검색(결과 있음·없음) → 상세, 상태 배지가 DB 값과 같은지, 포스터·줄거리 null 작품, 시즌 많은 드라마
  9. 콘솔: 비로그인 방문자의 refresh 401 로그가 남는 것은 알려진 이월이다(4절)
- Supabase Data API는 꺼 둔 상태다(2026-10-13 사용자 확인).
- **배포 뒤 운영 비교 측정 1회(Task 065, MVP 완료 기준 ③)**: 배포 뒤 운영에서 `POST /api/admin/plan-benchmarks`를 ADMIN 토큰으로 한 번 호출해 운영 DB `calc_run`에 EXACT·GREEDY 행을 남기고, Cloudtype 측정 시간을 ROADMAP Task 065 결과표에 추가한다(Task 100의 R11-30 참고값). 토큰 값은 이 문서에 적지 않는다.

## 4. 미결·이월 항목 (한곳에 모음)

**Task 044는 완료했다**(2026-10-14, 결정과 기록은 ROADMAP Task 044). 이월 질문(DTO 변환, 방영 0회 시즌, `episode_count` 출처, 영어 줄거리, `MAPPED`만 판정, `Availability` 플래그 검사, 이력·낙관적 락, `WatchUnit` 갱신, `collect/step` 분리, Reader 정렬)은 모두 결정·구현됐다. 남은 것은 아래 048 항목이다: 실제 TMDB 응답으로 수집, chunk 안 TMDB 호출 동안의 연결 점유 확인. 날짜가 다른 Job의 동시 실행(상세 Reader 포함)은 Task 045의 전체 동시 실행 금지로 해결했고, 5xx로 실패하면 관리자 API의 restart(또는 같은 날짜로 다시 시작)로 이어서 한다.

**Task 045로 넘겼던 항목은 해소됐다**(PR #62 병합): 다른 날짜의 동시 실행은 전체 동시 실행 금지로, 완료·실행 중 409 매핑과 `tier` enum `ALL`은 구현·계약에 반영, 비동기 실행은 수집 전용 비동기 `JobOperator`로 해결했다.

**배포 게이트(Task 046)**: 제공처 매핑 변경 API(`PATCH /api/admin/providers/{providerId}`)는 Task 054 전까지 인증이 없다(local 전용). **Task 054 완료 전에는 `main`에 병합·배포하지 않는다**(ROADMAP 순서상 MVP 배포 092 이후라 문제없다. 먼저 배포해야 하면 이 컨트롤러를 막는 조건을 추가한다).

**배포 전 사용자 확인(Task 045·047)**: GitHub 저장소 시크릿 `BACKEND_BASE_URL`·`ORIGIN_SECRET`·`ADMIN_BATCH_TOKEN`은 Task 093(cron 활성화) 때 등록한다(수집 워크플로 `collect.yml`이 읽는다). Cloudtype에 `ADMIN_BATCH_TOKEN`이 아직 등록돼 있지 않다. Cloudtype 환경 변수는 **배포할 때만 입력할 수 있어서**, `main` 배포 단계에서 값을 넣어야 한다(값이 없으면 prod가 기동하지 못한다. 이 값은 GitHub Actions 시크릿 `ADMIN_BATCH_TOKEN`과 같아야 한다). 배포 단계에서 사용자에게 알린다. 재배포 때 옛 컨테이너와 새 컨테이너가 겹쳐 도는지도 확인한다(겹치면 "전체 동시 실행 금지"의 서비스 락이 두 서버에 걸치지 않아 수집이 동시에 돌 수 있다. 확인 전까지 알려진 한계).

**인증(Task 053·054) 이월·확인 항목**: ① Redis Cloud 무료 플랜에서 Lua(`EVAL`) 실호출을 확인한다(문서상 Redis Cloud Standard는 EVAL·EVALSHA·SCRIPT LOAD를 지원하므로 배포 스모크에서 로그인·재발급으로 확인). ② 운영 배포 전 Cloudtype 환경 변수 `JWT_SECRET`·`GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`APP_FRONTEND_ORIGIN`·`OAUTH2_REDIRECT_BASE_URL`을 입력한다(배포 때만 입력 가능). ③ Google 콘솔에 운영 리디렉션 URI `https://ottnavi.vercel.app/api/login/oauth2/code/google`를 추가한다(Task 092). ④ 057(프론트)로 넘길 것: `npm run api:generate`(`AccessToken.role`), `/auth/callback`에서 refresh 호출과 `?error=login_failed` 처리, 401이면 재발급 한 번 뒤 원요청 재시도, 재발급 호출 합치기(single-flight), 재발급이 500이면 로그인 화면으로 보내지 않기, 로그아웃 때 메모리 AT 폐기, 요청의 `credentials` 설정. ⑤ Lettuce 예외 메시지에 키(familyId)가 로그에 남는지 확인한다. ⑥ `AuthUser.role`이 `UserRole` enum이라 `global.security`가 `user` 패키지를 참조한다(계층 허용을 결정하거나 문자열로 바꾼다). ⑦ 로그인 브라우저 확인(Task 053 완료 기준)은 사용자가 한다.

**인증 브라우저 확인 결과(2026-10-16, 사용자)**: 서버 쪽 로그인·재발급(연속 3회 200)·로그아웃(204 후 401)·`/api/me/settings` 404/401·관리자 API 403·`users` 계정당 1행·신규 가입은 USER로 확인했다. 인가 요청 쿠키 수명은 180초 그대로 두기로 했다(사용자 결정). **참고**: 당시 프론트 `AuthCallbackScreen`은 목업 전용이라 실제 로그인 직후에도 "로그인에 실패했습니다"가 떴다. Task 057 구현(PR 전)에서 실제 `POST /api/auth/refresh` 연동으로 교체했고, 실서버 확인은 3절 시나리오에 있다. 로컬 ADMIN 지정은 DB에서 `UPDATE users SET role='ADMIN' WHERE email=...`로 하고 다음 재발급 때 반영된다(운영 지정은 Task 095 ②).

**Task 048로**: 인기도·평점 UPDATE 부하 측정, TMDB discover 500쪽(1만 건) 상한에 걸리는 서비스 여부, `|`(OR) 구분자의 TMDB 공식 문서 확인, 운영 환경 연결 점유, 약 2분 추정 실측.

**Task 050·055·056·063·064·066·069로**: 같은 새 작품을 동시에 검색하면 `uk_title_media_tmdb`로 500이 날 수 있음(050), 검색 단건 수집 한도 소진 시 응답(050), 플랜 계산 입력은 한 플랜의 찜·달만 묶는다와 월 번호 0~2(063), 소속 일치 통합 테스트(064), `previous_plan_id`는 같은 사용자의 플랜(069), ACTIVE 교체 순서(066은 구현·테스트 완료, 069에서 같은 순서 재사용), `change_summary` 형식(069).

**프론트 연결(052·057·058·061·067) 사용자 UI 결정 대기와 이월(2026-10-16)**: 추천안이 있으면 괄호에 적었고 이의가 없으면 추천안으로 적용한다.

1. ui-designer 057·058 제안 6건: (1) 콜백 실패 화면의 같은 말 3번 반복(A 제목을 "다시 로그인해 주세요"로, 추천) (2) 설정 서버 400 목록의 영어 필드명(A 한글 이름표, 추천) (3) 권한 없음·404 화면 모양(C 현행 유지, 추천) (4) 로그인 확인 중·실패 화면에 h1 추가(A, 추천) (5) `break-keep` 전역 적용(추천 적용이나 앱 전체 영향이라 보류) (6) 찜 상한 안내 `role=alert` → `status`(A, 추천)
2. ui-designer 067 제안 7건: (1) "저장하면 바뀌어요"를 저장 버튼 위 안내 띠로(B, 추천) (2) 데스크톱 저장 버튼 하단 고정(B, 추천) (3) 저장 시각 표기 통일 "2026년 10월 16일 13:33"(`lib/format.ts`) (4) 작품 수 단위 "편"/"개" 통일(개, 추천) (5) 계산 실패 Alert 경고 아이콘(선택) (6) 재계산 안내 `role=status` 영역 상시 유지 (7) "이번 달 확정" 문구 수위 확인
3. 067 결정 3건: 현재 플랜을 `/plan` 안에 보일지 별도 `/plan/current` 화면과 헤더 링크로 둘지, 해지 예약 권장 달별 판정(PRD 기준 필요, 지금은 고정 문구), 요금 수정 뒤 옛 초안 안내 문구(서버가 변경 표시를 주지 않아 추정이 필요)
4. 057 이월: 비로그인 방문자도 앱 시작 때 refresh를 1회 호출해 콘솔에 401 로그가 남는다(추천: 그대로 둠). 헤더 내 메뉴 정리와 `MockRoleSwitcher`·푸터 개발용 이동 제거 시점은 사용자 UI 결정이다.
5. 061 이월: 가격 폼에 서버 `fieldErrors` 표시(ui-designer), 내 서비스 요금 목업과 관리자 상품 연결(기존 사용자 결정 유지).
6. 서버 측 이월(052): 단건 수집이 없어 `isExternalCollectFailed`가 항상 false이므로 외부 수집 실패 배너는 실서버에서 뜨지 않는다.
7. 못 한 확인: 058 설정 서버 400의 `fieldErrors` 필드명은 목업 추측, 형식 어긋난 JSON 응답, 067의 404 안내·진행 중 화면·저장 실패 문구(목업 시나리오 없음), 052 정상 경로 브라우저 확인(RTL만). 스크린샷은 `ottnavi\.playwright-mcp\`(`review-057-058\`, `review-067\`, `067-*.png`). 에이전트가 `.env`를 읽지 않으므로 `frontend/.env.local`에 `VITE_USE_MOCK=true`가 있는지도 확인한다.

**프론트 연결 결정 요약**(밤샘 로그 기준, 상세는 ROADMAP 각 Task 결과):

| 항목 | 선택 | 이유 |
|---|---|---|
| 관리자 권한 없음(057) | 안내 화면 + 홈 링크 | 홈 자동 이동보다 이유를 알 수 있다 |
| 재발급 요청(057) | 인터셉터 없는 별도 axios, `http.ts`에서 직접 호출 | 401 재귀와 생성 훅 순환 의존 방지 |
| 재발급 500·네트워크 오류(057) | 로그인으로 보내지 않고 오류 전달, 시작 시엔 "다시 시도" | 서버 장애를 로그아웃으로 오해하지 않게 |
| 로그아웃(057) | 서버 호출 뒤 AT·캐시 폐기, 실패해도 로컬 폐기 + 경고 | 화면에 로그인 상태가 남지 않게 |
| 상세 찜 상태(058) | 서버 찜 목록에서 파생, 수신 전 버튼 비활성 | 임시 캐시와 서버 값이 어긋나는 것 방지 |
| 첫 로그인 분기(058) | `isOnboardingDone=false`면 `/settings` | 설정 없이 계산하지 않게(사용자 검토 대상) |
| 관리자 화면(061) | 컴포넌트 미수정, 목업을 서버 규칙(400 → 404 → 409, 문구)에 맞춤 | 화면이 `errorCode`로 문구를 고르는 구조 |
| 플랜 화면(067) | 서버 상태(초안, 현재 플랜)만 기준, 저장 뒤 쿼리 무효화 후 성공 처리 | 새로고침·다른 탭에서도 같은 화면 |
| 409 / 404(067) | 409는 자동 재계산 + 안내, 404는 안내 + 재조회 | 수집이 새로 돌면 초안이 낡는다(409), 없는 초안을 다시 만들면 안 된다(404) |
| 공개 화면(052) | `VITE_USE_MOCK` 하나로만 분기, 429·404 문구 보강 | 임시 분기와 V-FS 중복을 피한다 |

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
