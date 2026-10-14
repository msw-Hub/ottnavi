# 세션 이어가기 (HANDOFF)

> 2026-10-13 작성. 계정이나 대화 세션이 바뀌어도 작업을 이어가기 위한 문서다. 진행 상황의 기준은 `docs/ROADMAP.md`이고, 이 문서는 ROADMAP에 흩어져 있거나 대화에만 있던 내용을 한곳에 모은 것이다. 오래되면 ROADMAP과 `git log`가 기준이다.
> 작업 규칙(커밋·PR·에이전트·설명 방식)은 `CLAUDE.md`의 "작업 시 지킬 것"과 "협업 방식"에 있다. 새 세션은 먼저 CLAUDE.md, 이 문서, ROADMAP 해당 Task를 읽는다.

## 1. 현재 상태 (2026-10-15 기준)

| 항목 | 내용 |
|---|---|
| 진행 | Phase 1 22/23, Phase 2 14/14 ✅, Phase 3 7/30, 합계 43/105 (Task 044 완료, PR #61 2026-10-14 병합. Task 045는 백엔드 구현 완료이고 프론트 반영·PR이 남아 아직 세지 않았다) |
| 완료한 최근 Task | 031 계약, 032~035 스키마·엔티티, 036~037 디자인, 038~040 엔진, 041 TMDB 클라이언트, 042 판정·러닝타임 규칙, 043 수집 Job 후보 단계, 044 상세·시즌·판정 Step |
| 열린 PR | 없음(Task 045 PR은 아직 올리지 않았다). 작업 브랜치는 `feature/b3-collect-admin-api`(백엔드 워크트리)이고 변경은 아직 커밋하지 않았다 |
| 다음 Task | **045 마무리**(같은 PR에서 `frontend-dev`가 프론트 반영: 타입 재생성, `collectRules.ts` 단순화, 목업, SCR-16 버튼, 그 뒤 `contract-checker`로 계약 최종 확인) → 046 제공처 매핑 API → 047 cron(비활성) → 048 첫 전체 수집 |
| 쉬림프 | 044까지 완료 처리. 앞으로 Task는 착수할 때 등록하고 `execute_task`, 끝나면 `verify_task`(점수 90 안팎) |
| 워크트리 | `ottnavi`(메인, `develop` 유지·최신화, 프론트·문서), `ottnavi-backend`(**백엔드 작업은 항상 여기서 한다**, 2026-10-14 사용자 결정. 현재 `feature/b3-collect-admin-api`), `ottnavi-docs`(문서 작업, 옛 브랜치), `ottnavi-backend-test`(옛 브랜치). 같은 브랜치를 두 워크트리에서 동시에 열 수 없다 |

## 2. 다음 작업 순서

1. (완료) PR #61은 2026-10-14에 Squash 병합했고 메인 워크트리 `develop`을 최신화했다.
2. **Task 045**(수집 실행·복구·이력 관리자 API): 백엔드 구현은 끝났다(결정과 기록은 ROADMAP Task 045, 설명은 `docs/guides/COLLECT_GUIDE.md` 11절). 남은 것: 같은 PR에 프론트 반영(`frontend-dev`, 계약에 필수 필드 `canRecover`·`canRestart`가 늘어 생성 타입·목업이 맞지 않으면 CI가 깨진다), 계약 최종 확인(`contract-checker`), 사용자의 설명 문서 검토·"깨뜨려 보기", PR.
3. 045~048(수집 API, 매핑 API, cron, 첫 전체 수집) → 049(공개 API 요청 제한, 언제든 가능) → 050·051(검색·상세 API) → 052(공개 화면 실제 연결).
4. 인증: 053·054 → 057(프론트 인증 연동, 인증 코드는 사용자 검토). 회원 입력: 055·056 → 058. 요금·관리자: 059·060 → 061. 플랜: 062~066 → 067.
5. 2단계 화면 목업(068·071·075·081·086·097)은 Task 095(MVP 공개) 이후.
6. 프론트에는 지금 독립 작업이 없다. 프론트 Task(052, 057, 058, 061, 067)는 모두 백엔드 API가 먼저 필요하다.

## 3. 사용자가 직접 할 일 ([H])

- **Task 005 도메인 구매**: Task 092 직전, 2026-11-04 이전. WHOIS 개인정보 보호, 등록업체 2단계 인증, 연장 안내는 링크가 아니라 업체 사이트 직접 접속, 카카오톡 알림(ROADMAP Task 005).
- **Redis 비밀번호 재설정**: 채팅에 노출된 것으로 보이는 Redis 비밀번호를 그대로 쓰고 있다. **Redis에 Refresh Token 등 민감 값을 저장하기 전(Task 053 이전)에 재설정**한다.
- Supabase Data API는 꺼 둔 상태다(2026-10-13 사용자 확인).

## 4. 미결·이월 항목 (한곳에 모음)

**Task 044는 완료했다**(2026-10-14, 결정과 기록은 ROADMAP Task 044). 이월 질문(DTO 변환, 방영 0회 시즌, `episode_count` 출처, 영어 줄거리, `MAPPED`만 판정, `Availability` 플래그 검사, 이력·낙관적 락, `WatchUnit` 갱신, `collect/step` 분리, Reader 정렬)은 모두 결정·구현됐다. 남은 것은 아래 048 항목이다: 실제 TMDB 응답으로 수집, chunk 안 TMDB 호출 동안의 연결 점유 확인. 날짜가 다른 Job의 동시 실행(상세 Reader 포함)은 Task 045의 전체 동시 실행 금지로 해결했고, 5xx로 실패하면 관리자 API의 restart(또는 같은 날짜로 다시 시작)로 이어서 한다.

**Task 045로 넘겼던 항목은 해소됐다**(백엔드 구현 완료, 프론트 반영·PR 남음): 다른 날짜의 동시 실행은 전체 동시 실행 금지로, 완료·실행 중 409 매핑과 `tier` enum `ALL`은 구현·계약에 반영, 비동기 실행은 수집 전용 비동기 `JobOperator`로 해결했다.

**배포 전 사용자 확인(Task 045)**: Cloudtype에 `ADMIN_BATCH_TOKEN`이 아직 등록돼 있지 않다. Cloudtype 환경 변수는 **배포할 때만 입력할 수 있어서**, `main` 배포 단계에서 값을 넣어야 한다(값이 없으면 prod가 기동하지 못한다. 이 값은 GitHub Actions 시크릿 `ADMIN_BATCH_TOKEN`과 같아야 한다). 배포 단계에서 사용자에게 알린다. 재배포 때 옛 컨테이너와 새 컨테이너가 겹쳐 도는지도 확인한다(겹치면 "전체 동시 실행 금지"의 서비스 락이 두 서버에 걸치지 않아 수집이 동시에 돌 수 있다. 확인 전까지 알려진 한계).

**Task 048로**: 인기도·평점 UPDATE 부하 측정, TMDB discover 500쪽(1만 건) 상한에 걸리는 서비스 여부, `|`(OR) 구분자의 TMDB 공식 문서 확인, 운영 환경 연결 점유, 약 2분 추정 실측.

**Task 050·055·056·063·064·066·069로**: 같은 새 작품을 동시에 검색하면 `uk_title_media_tmdb`로 500이 날 수 있음(050), 검색 단건 수집 한도 소진 시 응답(050), 첫 설정 저장 경합(055), 찜 20개 직렬화(056), 플랜 계산 입력은 한 플랜의 찜·달만 묶는다와 월 번호 0~2(063), 소속 일치 통합 테스트(064), `previous_plan_id`는 같은 사용자의 플랜(069), ACTIVE 교체 순서(066·069), `change_summary` 형식(069).

**프론트(Task 037에서 확인하지 못한 것)**: 작품 상세·찜 추가 다이얼로그의 44px 모양, 모바일 다이얼로그 닫기 버튼과 제목 겹침, 관리자·`/login`·`/settings`·`/pricing` 시각 회귀, 푸터 가운데 정렬의 눈 확인, `WISHLIST_LIMIT_EXCEEDED` 409의 실제 브라우저 확인. 전체 `npm run test`가 화면 테스트 5초 타임아웃으로 가끔 실패(단독 재실행은 통과, 원인 미확정).

**화면 문구 결정(추천안 적용 중)**: 요금 확인일 표기, 90일 초과 안내 문구, 시즌 순서 줄 노출 조건. PRD 11절에 UNKNOWN 확정 항목을 추가할지(추천 추가). 수집 이력 100건 한계는 써 보고 판단.

**기타**: 현재 플랜 조회 API(Task 057 근처), 내 정보·역할 조회 API(057), TMDB 실제 응답 확인(소비 Task 전에 로컬에서 1회 호출: Retry-After, 한국어 줄거리 `""`/null 등), `BaseTimeEntity` 주석("기본 매핑은 TIMESTAMP")이 Hibernate 7에서 사실인지(`@JdbcTypeCode` 없이도 `validate` 통과 확인됨, 주석 정정 필요), `Instant` 필드에는 일관성을 위해 어노테이션을 유지, 고도화 후보(인물·장르 검색, 메인 추천작: PRD 미반영, `title_cast.person_tmdb_id`·이름과 `title_genre.genre_id` 인덱스 상기), `domain/rule` 이름은 유지, Javadoc 보충은 하지 않기로 함(CodeRabbit Docstring Coverage 경고는 무시), `OttServiceQueryService` 정리는 #60에서 완료.

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
