# Phase 1 사람 작업 체크리스트 [H]

> 근거: `docs/ROADMAP.md` 5절. 코드 Task(006~011, 016~019, 021~022, 023 선행)는 쉬림프 태스크 매니저에 등록했고, 이 문서는 **사용자가 직접 하는 [H] Task 8개**만 다룬다.
> 각 Task의 `기록:`은 완료 후 이 문서가 아니라 ROADMAP.md의 해당 Task 아래에 남긴다(Task 012 이후에는 `feature/docs-*` 브랜치 PR). 비밀 값 자체는 어디에도 적지 않는다.

## 진행 순서와 기한

| 순서 | Task | 선행 | 기한 | 비고 |
|---|---|---|---|---|
| 1 | 002 외부 계정·키 발급 | 없음 | — | 004·015·020·022의 전제 |
| 1 | 003 약관·운영 정책 조사 | 없음 | — | 022의 전제(서비스 추가 가능 여부) |
| 1 | 005 도메인 구매 | 없음 | 2026-10-29 | |
| 2 | 004 KR 제공처 ID·요금표 조사 | 002 | — | 033 시드, 061 입력값 |
| 3 | 012 `develop` 생성·보호 규칙 | 코드 Task 006~011 완료 | — | 브랜치 전환점 |
| 4 | 014 Vercel 연결 | 012 (009 포함) | 2026-10-07 | |
| 5 | 015 비밀 값 정리·등록 | 002, 014 | — | |
| 6 | 020 Cloudtype 서비스·토큰 | 002, 015, 019(코드) | 2026-10-12 | 021(배포)의 전제 |

코드 Task와의 연결: 006~011 → **012** → 016·017 착수 / 019 완료 → **020** → 021.

---

## Task 002: 외부 서비스 계정 생성과 API 키 발급
- [ ] TMDB API 키(비영리 용도)
- [ ] Google Cloud OAuth 클라이언트. 로컬 리다이렉트 URI는 `http://localhost:5173/api/login/oauth2/code/google` 형태(TECH 4절 경로 규칙, 포트는 Vite 설정 확인 후). 운영 URI는 Task 092에서 추가
- [ ] Supabase 프로젝트(Session Pooler 5432), Redis Cloud 무료 DB, Cloudtype(무료), Vercel, GitHub 저장소 권한
- [ ] SMTP 시험용 발신 계정(Gmail 앱 비밀번호). HTTPS 메일 API 키는 Task 022의 SMTP 시험이 실패할 때만 발급
- [ ] 키는 저장소 밖(비밀번호 관리자 등)에만 보관. Gemini 키는 Task 085, Grafana Cloud는 Task 096에서 발급
- **버전 주의**: 로컬 compose(`infra/docker-compose.yml`)가 PostgreSQL 17.6, Redis 8.6이므로 운영도 같은 버전으로 만든다.
  - Supabase: 프로젝트를 만든 뒤 SQL 편집기에서 `SHOW server_version;`으로 17.6인지 확인한다(다르면 알려서 로컬 이미지를 맞춘다).
  - Redis Cloud: 데이터베이스 생성 때 버전 선택지(7.4, 8.2, 8.4, 8.6)에서 **8.6**을 고른다.
- **완료 기준(V-H)**: 서비스별 발급 여부·발급일·보관 위치(값 제외)를 기록. 저장소·채팅에 키가 없음을 확인

## Task 003: 약관·운영 정책 조사
- [ ] TMDB 약관: 비영리, 승인 로고·고지문, 6개월 보관, JustWatch 출처 표기 조건
- [ ] 7개 서비스 해지 예약 정책(R11-27, 안내 문구에만 영향)
- [ ] Cloudtype 무료·Hobby 조건, Supabase(500MB, 7일 비활성 일시정지)·Redis Cloud(30MB, 초당 100 ops) 한도
- [ ] Cloudtype 무료 플랜에서 운영 서비스 외 서비스를 하나 더 만들 수 있는지(Task 022 일회성 서비스의 전제, R-15)
- **완료 기준(V-H)**: 항목별 출처 URL·날짜·결론 기록. R11-27 결과를 10.2에 반영할지 판단. 서비스 추가가 불가하면 Task 022 진행 방식을 Claude에게 알려 협의

## Task 004: KR 제공처 ID 확정과 요금표 조사 (선행: 002)
- [ ] TMDB KR 제공처 목록에서 7개 서비스의 `provider_id`, 원본 라벨, tier(STANDARD/AD) 판정표
- [ ] 7개 서비스 STANDARD 단품 월 가격과 조사일
- [ ] 매핑표 → Task 033 시드 입력값, 가격표 → Task 061 관리자 입력값
- **완료 기준(V-H)**: 매핑표·가격표(출처, 조사일)와 매핑되지 않은 KR 제공처 목록 기록

## Task 005: 도메인 `ottnavi.shop` 구매 (기한 2026-10-29)
- [ ] 등록업체별 첫해·**갱신 가격** 비교. 월 운영비 1만원 이내(Hobby 6,600원 포함) 확인
- [ ] 구매. DNS 연결은 Task 092
- **완료 기준(V-H)**: 업체·첫해·갱신 가격·구매일 기록

## Task 012: `develop` 브랜치 생성과 보호 규칙 설정 (선행: 코드 Task 006~011)
- [x] 미커밋 문서와 초기 세팅 커밋을 `origin/main`에 푸시 (2026-10-04)
- [x] `main`에서 `develop` 생성·푸시. 기본 브랜치는 `main` 유지 (2026-10-04)
- [ ] `main`·`develop` 보호: 직접 푸시 금지, PR 필수, `backend-ci`·`frontend-ci` 통과 필수, 강제 푸시·삭제 금지(리뷰 승인 필수 없음). CI 경로 필터를 쓰면 필수 검사가 보고되지 않아 PR이 막힐 수 있으니 필터 사용 여부 결정
- [x] Secret scanning·push protection 사용 가능 여부 확인 후 켬 (2026-10-04, 공개 저장소. Settings → Advanced Security에서 Secret Protection·Push protection 모두 켜져 있음을 확인)
- **완료 기준**: Task 010의 `develop` 대상 PR에서 두 워크플로 성공(실행 링크 기록, Task 010 검증 이관분. 첫 푸시는 이미 끝남). 보호 규칙 기록, `develop` 직접 푸시 거부 확인

## Task 014: Vercel 프로젝트 연결과 Production Branch 설정 (선행: 012, 기한 2026-10-07)
- [ ] GitHub 저장소 연결, Root Directory = `frontend`, Production Branch = `main`
- [ ] 빌드 명령·Node 버전을 CI와 같게
- [ ] R-17 실측: `vercel.json`(`routes` + `rewrites`)로 미리보기 배포가 설정 오류 없이 만들어지는지 확인
- **완료 기준**: 설정값 기록, `develop` 푸시로 미리보기 URL 루트 페이지 열림. 혼용 허용이면 그 사실을 기록. 거부되면 `/api` 프록시·헤더 주입·캐시 비활성화·SPA 대체를 모두 `routes`로 옮긴 수정본을 `feature/f0-vercel-routes` PR로 병합(Claude에게 요청). 어느 쪽이든 10.3 R-17 갱신

## Task 015: 비밀 값 저장 위치 정리와 등록 (선행: 002, 014)
범위: GitHub·Vercel·로컬만. Cloudtype은 Task 020.
- [ ] 저장 위치표 작성(값 제외)
  - GitHub Actions secrets: `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`
  - Vercel 환경 변수: `ORIGIN_SECRET`을 **Production과 Preview 둘 다**
  - Cloudtype 환경 변수(Task 020에서 등록): DB, Redis, TMDB, Google OAuth, `JWT_SECRET`, `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`, `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL`, 메일
  - 로컬: `.env`(커밋 금지)
- [ ] `JWT_SECRET` 256비트 이상, `ADMIN_BATCH_TOKEN`·`ORIGIN_SECRET`은 긴 난수로 이때 생성. `VITE_` 변수에 비밀 값 금지
- [ ] 노출 시 재발급 절차 한 줄씩
- **완료 기준(V-H)**: 위치표와 GitHub·Vercel(Production·Preview)·로컬 등록일 기록, 저장소에 비밀 값 없음 확인

## Task 020: Cloudtype 서비스 생성과 환경 변수·배포 토큰 등록 (선행: 002, 015, 019, 기한 2026-10-12)
- [ ] R-12 결정: 배포 방식(GitHub Actions 배포 액션 여부)과 배포 토큰 이름·발급 위치를 Cloudtype 문서로 확인해 결정 (결과를 Claude에게 알려야 Task 021 워크플로 작성 가능)
- [ ] 무료 플랜에 운영 백엔드 서비스 생성(Java 17, 포트 8080, `prod` 프로필). Task 022 일회성 서비스와 이름으로 구분
- [ ] 환경 변수 등록(015 위치표의 Cloudtype 항목). 첫 배포에 필요 없는 값(Google OAuth, TMDB, 메일)은 Task 092에서 채워도 됨
- [ ] GitHub Actions secrets에 Cloudtype 배포 토큰(R-12 이름)과 백엔드 주소 등록
- **완료 기준(V-H)**: 서비스 이름·생성일, 등록한 변수 이름 목록(값 제외), R-12 결정 기록, 10.3 R-12 갱신

---

## 완료한 설정 기록 (Task 번호 밖의 작업)
- **GitHub CLI(`gh`)**: 설치와 `gh auth login` 완료(2026-10-04). PR 생성에 쓴다.
- **CodeRabbit**: 저장소에 연결했고 루트 `.coderabbit.yaml`로 `develop` 대상 PR 리뷰·한국어·생성 파일 제외를 설정했다(PR #1에서 동작 확인). Autofix·Autopilot은 쓰지 않는다. 만약 켜져 있다면 코멘트 속 Coding task 화면에서 Autopilot을 끈다.

## 사용자가 직접 실행하는 Git/배포 작업 (Claude는 요청 시에만)
- Task 012의 푸시·브랜치 보호
- Task 021: feature → `develop` PR(squash) 후 `develop` → `main` PR(merge commit) 병합 = 첫 배포, 이후 `main` 태그
- Task 022: 일회성 Cloudtype 서비스 배포·시험·삭제, 시험 브랜치 삭제, R11-11 결정
