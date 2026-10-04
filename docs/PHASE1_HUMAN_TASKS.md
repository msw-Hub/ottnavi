# Phase 1 사람 작업 체크리스트 [H]

> 근거: `docs/ROADMAP.md` 5절. 코드 Task(006~011, 016~019, 021~022, 023 선행)는 쉬림프 태스크 매니저에 등록했고, 이 문서는 **사용자가 직접 하는 [H] Task 8개**만 다룬다.
> 각 Task의 `기록:`은 완료 후 이 문서가 아니라 ROADMAP.md의 해당 Task 아래에 남긴다(Task 012 이후에는 `feature/docs-*` 브랜치 PR). 비밀 값 자체는 어디에도 적지 않는다.
> 외부 서비스(Vercel, Cloudtype, Supabase 등)의 화면 구성과 메뉴 이름은 바뀔 수 있다. 아래 경로는 작성 시점(2026-10-04) 기준의 안내이고, 다르면 각 서비스 공식 문서를 우선한다.

## 진행 순서와 기한

| 순서 | Task | 선행 | 기한 | 비고 |
|---|---|---|---|---|
| 1 | 002 외부 계정·키 발급 | 없음 | — | **완료(2026-10-05)**. 004·015·020·022의 전제 |
| 1 | 003 약관·운영 정책 조사 | 없음 | — | **완료(2026-10-05)**. 결과는 `docs/TASK003_POLICY_PRICING.md` |
| 1 | 005 도메인 구매 | 없음 | Task 092 직전(1년 사용 기간을 늦추려고 미룸) | 구매 전에는 `*.vercel.app`·Cloudtype 기본 주소 사용 |
| 2 | 004 KR 제공처 ID·요금표 조사 | 002 | — | 033 시드, 061 입력값 |
| 3 | 012 `develop` 생성·보호 규칙 | 코드 Task 006~011 완료 | — | **완료(2026-10-04)** |
| 4 | 014 Vercel 연결 | 012 (009 포함) | 2026-10-07 | |
| 5 | 015 비밀 값 정리·등록 | 002, 014 | — | |
| 6 | 020 Cloudtype 서비스·토큰 | 002, 015, 019(코드) | 2026-10-12 | 021(배포)의 전제 |

코드 Task와의 연결: 006~011 → **012** → 016·017 착수 / 019 완료 → **020** → 021.

**지금 바로 착수할 수 있는 것**: 014. 기한이 가까운 것은 **014(10-07)**, **020(10-12)** 이다. 002·003은 완료했다. 005(도메인 구매)는 1년 사용 기간을 늦추려고 Task 092 직전까지 미룬다.

---

## 비밀 값 보관 규칙 (Task 002·015·020 공통)

### 원칙
1. **원본은 비밀번호 관리자에만 둔다.** 발급받은 키·토큰·비밀번호는 먼저 비밀번호 관리자(예: Bitwarden, 1Password)에 항목별로 저장한다. 서비스 화면에서 한 번만 보여 주는 값(Google OAuth 클라이언트 보안 비밀, 일부 토큰, Supabase 데이터베이스 비밀번호 등)은 화면을 닫기 전에 저장한다.
2. **서비스가 값을 읽는 곳에만 복사해 넣는다.** 아래 "등록 위치"에 적힌 곳 외에는 값을 두지 않는다.
3. **채팅(Claude 포함)·커밋·PR·이슈·스크린샷·문서에 값을 적지 않는다.** Claude에게 질문할 때도 **변수 이름만** 말한다. 값이 화면에 보이는 스크린샷은 가리고 올린다.
4. **`VITE_` 변수에는 비밀 값을 넣지 않는다.** `VITE_`로 시작하는 변수는 빌드 결과(브라우저)에 그대로 들어간다.
5. **로컬 `.env`는 커밋하지 않는다.** `.gitignore`가 `.env`·`.env.*`를 막고 `.env.example`만 예외로 둔다. 새로 만든 파일이 `git status`에 보이면 커밋 전에 멈춘다.
6. **개발용과 운영용 값은 서로 다르게 만든다.** 특히 `JWT_SECRET`·`ADMIN_BATCH_TOKEN`은 로컬 `.env`(개발용)와 Cloudtype(운영용)에 같은 값을 쓰지 않는다. 운영 DB·Redis 접속 정보는 로컬 `.env`에 넣지 않는다(로컬은 docker compose 기본값을 쓴다).

### 비밀 값별 발급처와 등록 위치

| 이름 | 발급·생성 | 등록 위치 | 비고 |
|---|---|---|---|
| `TMDB_API_KEY` | TMDB 계정 설정 → API | 로컬 `.env`, Cloudtype 환경 변수(Task 020 또는 092) | v3 API 키와 읽기 액세스 토큰이 함께 발급된다. 어느 쪽을 쓸지는 수집 구현 때 정하므로 둘 다 비밀번호 관리자에 보관 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google Cloud 콘솔 → OAuth 클라이언트 | 로컬 `.env`, Cloudtype(Task 092 최종 점검 때) | 클라이언트 보안 비밀은 생성 직후에 저장 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`(운영) | Supabase 프로젝트(Session Pooler 5432) | **Cloudtype 환경 변수만** | 로컬 `.env`에는 넣지 않는다 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`(운영) | Redis Cloud 데이터베이스 | **Cloudtype 환경 변수만** | 로컬 `.env`에는 넣지 않는다 |
| `JWT_SECRET` | 직접 생성(아래 명령) | 로컬 `.env`(개발용), Cloudtype(운영용) | 256비트 이상, 두 값은 서로 다르게 |
| `ADMIN_BATCH_TOKEN` | 직접 생성 | **GitHub Actions secrets**, Cloudtype, 로컬 `.env`(개발용) | cron이 `Authorization: Bearer`로 보냄. GitHub·Cloudtype 값은 같아야 함 |
| `ORIGIN_SECRET` | 직접 생성 | **GitHub Actions secrets**, **Vercel(Production·Preview 둘 다)**, Cloudtype | 세 곳의 값이 같아야 한다. 로컬에서는 오리진 검사를 끈다(`app.origin-secret.enabled=false`)라서 `.env`에 불필요 |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail 앱 비밀번호 | 로컬 `.env`, 일회성 Cloudtype 서비스(Task 022), 운영 Cloudtype(Task 092) | 일반 로그인 비밀번호가 아니라 앱 비밀번호 16자리 |
| Cloudtype 배포 토큰, 백엔드 주소 | Cloudtype(Task 020) | GitHub Actions secrets | 이름은 R-12 결정으로 정한다 |
| `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL` | 비밀 아님(주소) | Cloudtype, 로컬 `.env` | |
| `VITE_USE_MOCK` | 비밀 아님(`true`/`false`) | `frontend/.env.local` | 공개 값만 |

### 난수 만드는 법 (PowerShell)
화면에 출력하지 않고 클립보드로 복사한 뒤 비밀번호 관리자에 붙여 넣는다. 아래 명령은 Windows PowerShell 5.1과 PowerShell 7 모두에서 동작한다.
```powershell
# 32바이트(256비트) 난수를 Base64로 만들어 클립보드에 복사. 변수마다 한 번씩 실행해 값을 따로 만든다.
$b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b) | Set-Clipboard
```
- `JWT_SECRET`: 위 명령으로 32바이트(Base64 44자). 길게 하려면 `byte[] 48`로 바꾼다.
- `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`: 같은 명령으로 각각 따로 만든다. HTTP 헤더에 쓰이는 값이라 Base64의 `+`, `/`, `=`가 문제가 되면 `byte[] 32`를 16진수로 바꿔서 쓴다(`($b | ForEach-Object { $_.ToString('x2') }) -join ''`).
- 클립보드 기록 관리 도구를 쓰고 있다면 붙여 넣은 뒤 해당 기록을 지운다.

### 노출됐을 때 (한 줄 절차)
| 값 | 재발급 |
|---|---|
| 아무 키나 커밋·채팅에 노출 | 즉시 해당 서비스에서 폐기·재발급 → 등록 위치 전부 교체. git 이력에서 지우는 것은 이미 늦다(공개 저장소). |
| `ORIGIN_SECRET` | 새 값 생성 → Cloudtype → Vercel(Production·Preview) → GitHub secrets 순으로 교체 → 재배포 |
| `ADMIN_BATCH_TOKEN` | 새 값 생성 → Cloudtype → GitHub secrets 교체 |
| `JWT_SECRET` | 새 값 생성 → Cloudtype 교체 → 재시작(모든 사용자 로그아웃됨) |
| DB·Redis 비밀번호 | 각 서비스 콘솔에서 비밀번호 재설정 → Cloudtype 교체 |
| TMDB·Google·Gmail 앱 비밀번호 | 각 콘솔에서 폐기 후 재발급 → 등록 위치 교체 |
| Cloudtype 배포 토큰 | Cloudtype에서 폐기·재발급 → GitHub secrets 교체 |

---

## Task 002: 외부 서비스 계정 생성과 API 키 발급 — 완료(2026-10-05)
**목적**: 이후 Task(004 조사, 015 등록, 020 배포, 022 SMTP 시험)가 쓰는 계정과 키를 준비한다. 비밀 값은 위 "비밀 값 보관 규칙"을 따른다.

### 할 일
- [ ] **비밀번호 관리자 준비**: 아직 없다면 먼저 설치하고, 서비스별로 항목을 만들어 둘 곳을 정한다.
- [ ] **TMDB API 키(비영리 용도)**
  1. TMDB 계정을 만들고 이메일 인증을 한다.
  2. 계정 설정 → API에서 API 키를 신청한다. 용도는 비영리(개인 학습·무료 서비스)로 선택하고 서비스 이름·URL·설명을 적는다. URL은 도메인 구매 전이면 GitHub 저장소 주소를 쓴다.
  3. 발급된 API 키와 읽기 액세스 토큰을 비밀번호 관리자에 저장한다. → 등록 위치: 위 표 `TMDB_API_KEY`
- [ ] **Google Cloud OAuth 클라이언트**
  1. Google Cloud 콘솔에서 프로젝트를 만든다.
  2. OAuth 동의 화면을 구성한다. 앱 이름은 `OTT내비`, 사용자 유형은 외부(External), 게시 상태는 **테스트**로 두고 **테스트 사용자에 본인 Google 계정을 추가**한다(테스트 상태에서는 등록된 계정만 로그인할 수 있다).
  3. 사용자 인증 정보 → OAuth 클라이언트 ID 만들기 → 유형은 웹 애플리케이션.
  4. **승인된 리디렉션 URI**에 `http://localhost:5173/api/login/oauth2/code/google`을 추가한다(TECH 4절 경로 규칙, 포트 5173은 Vite 기본값이고 `vite.config.ts`에서 바꾸지 않았다). 운영 URI는 Task 092에서 추가한다.
  5. 클라이언트 ID와 보안 비밀을 비밀번호 관리자에 저장한다. 보안 비밀은 생성 직후에 저장해 둔다. → `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- [ ] **Supabase 프로젝트**
  1. 프로젝트를 만들고(지역은 한국에서 가까운 곳, 예: 서울·도쿄) 데이터베이스 비밀번호를 비밀번호 관리자에 저장한다.
  2. **버전 확인**: 로컬 compose가 PostgreSQL 17.6이므로 SQL 편집기에서 `SHOW server_version;`을 실행해 17.6인지 본다. 다르면 알려 주면 로컬 이미지를 맞춘다.
  3. 연결 정보에서 **Session Pooler(5432)** 의 호스트·사용자·포트를 확인한다. JDBC URL은 Task 020에서 Cloudtype 환경 변수로 등록한다. → `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`(Cloudtype에만)
- [ ] **Redis Cloud 무료 DB**
  1. 계정을 만들고 무료 플랜 데이터베이스를 만든다.
  2. **버전은 8.6**을 고른다(선택지: 7.4, 8.2, 8.4, 8.6). 로컬 compose가 8.6이라서 맞춰야 한다.
  3. 공개 엔드포인트(호스트:포트)와 기본 사용자 비밀번호를 비밀번호 관리자에 저장한다. → `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`(Cloudtype에만)
- [ ] **Cloudtype 계정**(무료): 가입 후 GitHub 저장소 연동 권한을 확인한다. 서비스 생성은 Task 020에서 한다.
- [ ] **Vercel 계정**: GitHub 계정으로 가입한다. 프로젝트 연결은 Task 014에서 한다.
- [ ] **GitHub 저장소 권한**: Actions secrets를 등록할 수 있는 권한(저장소 소유자)이 있는지 확인한다. 이미 `gh`가 인증돼 있다.
- [ ] **Gmail 앱 비밀번호(SMTP 시험용)**
  1. 시험에 쓸 Gmail 계정에서 2단계 인증을 켠다.
  2. Google 계정 → 보안 → 앱 비밀번호에서 앱 비밀번호(16자리)를 만든다.
  3. 비밀번호 관리자에 저장한다. → `MAIL_USERNAME`(Gmail 주소), `MAIL_PASSWORD`(앱 비밀번호), `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`
  4. HTTPS 메일 API 키는 Task 022의 SMTP 시험이 실패한 경우에만 발급한다.
- [ ] Gemini 키는 Task 085, Grafana Cloud는 Task 096에서 발급한다(지금 하지 않는다).

### 버전 주의
로컬 compose(`infra/docker-compose.yml`)가 PostgreSQL 17.6, Redis 8.6이므로 운영도 같은 버전으로 만든다.

### 완료 기준(V-H)
서비스별 **발급 여부·발급일·보관 위치(값 제외)** 를 ROADMAP Task 002 `기록:`에 남긴다. 저장소·채팅에 키가 없음을 확인한다(`git status`에 `.env`가 없고, 대화에 붙여 넣은 값이 없는지).

## Task 003: 약관·운영 정책 조사 — 완료(2026-10-05, 결과는 `docs/TASK003_POLICY_PRICING.md`)
**목적**: 화면 표기와 한도 가정이 실제 약관과 맞는지 확인한다. 결과는 서비스 표현(출처 고지)과 Task 022 진행 방식에 영향을 준다.

### 할 일
- [ ] **TMDB 약관**: API 이용 약관(TMDB 사이트의 API Terms of Use)에서 아래를 확인한다.
  - 비영리 용도 조건, 승인된 로고·고지문 문구, 데이터 보관 기간(6개월 가정), JustWatch 출처 표기 조건
- [ ] **7개 서비스 해지 예약 정책(R11-27)**: 각 서비스의 해지 안내 페이지에서 "해지 후 이용 가능 기간", "해지 예약 가능 여부"를 확인한다. 안내 문구에만 영향이 있다.
- [ ] **무료 플랜 한도**: 각 서비스 요금·한도 페이지에서 확인한다.
  - Cloudtype 무료·Hobby 조건
  - Supabase: 500MB, 7일 비활성 시 일시정지
  - Redis Cloud: 30MB, 초당 100 ops
- [ ] **Cloudtype 서비스 개수**: 무료 플랜에서 운영 서비스 외에 서비스를 **하나 더 만들 수 있는지** 확인한다(Task 022의 일회성 서비스 전제, R-15).

### 완료 기준(V-H)
항목별 **출처 URL·확인 날짜·결론**을 ROADMAP Task 003 `기록:`에 남긴다. R11-27 결과를 PRD 10.2에 반영할지 판단한다. 서비스 추가가 불가능하면 Task 022를 어떻게 진행할지 Claude에게 알려 협의한다.

## Task 004: KR 제공처 ID 확정과 요금표 조사 (선행: 002)
**목적**: 백엔드 시드(Task 033)와 관리자 가격 입력(Task 061)에 쓸 값을 만든다.

### 할 일
- [ ] **TMDB KR 제공처 목록 조회**: Task 002의 TMDB 키로 한국 제공처 목록을 받는다. 키는 현재 세션에만 두고 명령 히스토리에 남기지 않는다.
  ```powershell
  # 키를 입력받아 현재 세션에서만 사용(세션 종료 시 사라짐)
  $env:TMDB_API_KEY = Read-Host "TMDB API 키"
  Invoke-RestMethod "https://api.themoviedb.org/3/watch/providers/movie?watch_region=KR&api_key=$env:TMDB_API_KEY" | ConvertTo-Json -Depth 5 > $env:TEMP\tmdb-kr-movie.json
  Invoke-RestMethod "https://api.themoviedb.org/3/watch/providers/tv?watch_region=KR&api_key=$env:TMDB_API_KEY" | ConvertTo-Json -Depth 5 > $env:TEMP\tmdb-kr-tv.json
  Remove-Item Env:TMDB_API_KEY
  ```
  결과 파일은 저장소 밖(임시 폴더)에 둔다.
- [ ] **매핑표 작성**: 7개 서비스 각각의 `provider_id`, TMDB 원본 라벨, tier(STANDARD/AD) 판정(광고형 요금제 라벨이 따로 있으면 AD로 구분)을 표로 만든다.
- [ ] **요금표 조사**: 7개 서비스의 STANDARD 단품 월 가격과 조사일을 공식 요금 페이지에서 확인한다.
- [ ] **매핑되지 않은 KR 제공처 목록**을 따로 적는다(7개 밖의 제공처).
- [ ] 매핑표는 Task 033 시드 입력값으로, 가격표는 Task 061 관리자 입력값으로 넘긴다(표 형태로 Claude에게 전달).

### 완료 기준(V-H)
매핑표·가격표(**출처, 조사일**)와 매핑되지 않은 KR 제공처 목록을 ROADMAP Task 004 `기록:`에 남긴다.

## Task 005: 도메인 `ottnavi.shop` 구매 (구매 시점: Task 092 직전)
**목적**: 운영 주소를 확보한다. DNS 연결은 Task 092에서 한다.
**구매 시점**: 도메인은 구매일부터 1년이라 **실제로 쓰기 직전(Task 092, 기한 2026-11-04 이전)에 구매**한다. 그때까지는 이름만 `ottnavi.shop`으로 정해 두고 `*.vercel.app`·Cloudtype 기본 주소로 개발·첫 배포를 한다(도메인이 필요한 곳은 Task 092의 운영 리디렉션 URI 등록과 DNS 연결뿐이다). 구매를 미루는 동안 같은 이름을 다른 사람이 먼저 등록할 수 있는 위험은 낮다고 보고 받아들인다.

### 할 일
- [ ] 포트폴리오용이라 **1년만 쓰는 것을 기준**으로 한다. 등록업체 2~3곳에서 `ottnavi.shop`의 **첫해 가격**을 비교한다(갱신 가격은 1년 뒤 판단용으로 기록만 한다). 최종 결제 금액(부가세 포함)과 WHOIS 개인정보 보호 포함 여부를 함께 본다.
- [ ] **저예산 범위**인지 계산한다(Cloudtype 구독료는 512MB당 월 6,600원이고 메모리는 실측 후 결정, 여기에 도메인 월 환산을 더한다). 고정 상한은 두지 않고 절약할수록 좋다.
- [ ] 구매하고 **WHOIS 개인정보 보호**(프라이버시 서비스)가 켜져 있는지 확인한다. 소유자 이메일은 **만료·연장 안내가 오는 주소**이므로 평소에 확인하는 메일을 쓴다(스팸 수신용 별도 주소를 쓰면 만료 안내를 놓칠 수 있다).
- [ ] 등록업체 계정에 2단계 인증을 켠다. 도메인 탈취는 서비스 전체 장애로 이어진다.
- [ ] 1년 선결제만 지원하고 자동 갱신이 없다(이전 경험상 만료 전에 업체가 **카카오톡**으로 연장하라고 연락해 온다). 자동 갱신이 있다면 꺼 둔다. 카카오톡 알림을 켜 두고, 업체 안내와 별개로 만료일 **한 달 전 알림**을 캘린더에 남긴다. 연장 안내를 받으면 메시지의 링크가 아니라 **업체 사이트에 직접 접속해서** 확인한다(도메인 만료를 노린 사칭이 있다).
- [ ] 포트폴리오 문서(README 등)에 도메인 주소와 함께 **`*.vercel.app` 대체 주소**를 적어 둔다. 만료되면 `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL`, Google OAuth 운영 리디렉션 URI, `vercel.json`의 주소만 바꾸면 되고 코드 변경은 없다.

### 완료 기준(V-H)
**업체·첫해 가격(갱신 가격은 참고)·구매일·만료일**을 ROADMAP Task 005 `기록:`에 남긴다.

## Task 012: `develop` 브랜치 생성과 보호 규칙 설정 (선행: 코드 Task 006~011) — 완료
- [x] 미커밋 문서와 초기 세팅 커밋을 `origin/main`에 푸시 (2026-10-04)
- [x] `main`에서 `develop` 생성·푸시. 기본 브랜치는 `main` 유지 (2026-10-04)
- [x] `main`·`develop` 보호: 직접 푸시 금지, PR 필수, `backend-ci`·`frontend-ci` 통과 필수, 강제 푸시·삭제 금지(리뷰 승인 필수 없음). CI 경로 필터는 쓰지 않음 (2026-10-04, ruleset `protect-main-develop`)
- [x] Secret scanning·push protection 사용 가능 여부 확인 후 켬 (2026-10-04, 공개 저장소. Settings → Advanced Security에서 Secret Protection·Push protection 모두 켜져 있음을 확인)
- **완료 기준**: Task 010의 `develop` 대상 PR에서 두 워크플로 성공(실행 링크 기록, Task 010 검증 이관분. 첫 푸시는 이미 끝남). 보호 규칙 기록, `develop` 직접 푸시 거부 확인
- **완료 (2026-10-04)**: PR #6에서 두 워크플로 성공, 보호 규칙 설정과 `develop` 직접 푸시 거부(`GH013`) 확인. 상세 기록은 ROADMAP Task 012 `기록:`

## Task 014: Vercel 프로젝트 연결과 Production Branch 설정 (선행: 012, 기한 2026-10-07)
**목적**: 프론트 배포 경로를 열고, `vercel.json`이 설정 오류 없이 배포되는지(R-17) 실측한다.

### 할 일
- [ ] **GitHub 저장소 연결**
  1. Vercel 대시보드 → 새 프로젝트(Add New → Project)에서 GitHub 저장소 `msw-Hub/ottnavi`를 가져온다.
  2. **Root Directory = `frontend`** 로 지정한다(저장소 루트가 아니다).
  3. Framework Preset은 Vite로 자동 인식되는지 확인한다. Build Command는 `npm run build`, Output Directory는 `dist`가 기본값이다.
- [ ] **Production Branch = `main`**: 프로젝트 설정(Settings → Git 또는 Environments)에서 Production Branch를 `main`으로 둔다. `develop`과 PR 브랜치는 미리보기(Preview)만 만든다.
- [ ] **Node 버전을 CI와 같게(Node 24)**: 프로젝트 설정 → General → Node.js Version을 **24.x**로 바꾼다. Node 22(npm 10)는 `npm ci`가 실패해 CI도 24로 맞췄다(ROADMAP Task 010 `기록:`).
- [ ] **환경 변수는 아직 넣지 않는다.** `ORIGIN_SECRET`은 Task 015에서 Production·Preview 둘 다에 넣는다.
- [ ] **R-17 실측**: 미리보기 배포가 `vercel.json` 설정 오류 없이 만들어지는지 확인한다.
  - `develop`은 보호 규칙으로 직접 푸시할 수 없으니, **PR이 열려 있는 `feature/*` 브랜치**의 미리보기를 쓴다. 예를 들어 이 문서 변경 PR(`feature/docs-human-tasks-status`)을 열면 Vercel이 미리보기를 만든다.
  - PR의 Checks 또는 Vercel 봇 코멘트에서 미리보기 URL을 열고 **루트 페이지가 열리는지** 확인한다.
  - 백엔드 주소가 자리표시값(`REPLACE_BACKEND_HOST`)이라 `/api` 요청은 실패하는 것이 정상이다. 프록시 동작과 적용 순서는 Task 021에서 본다.

### 완료 기준
- 설정값(Root Directory, Production Branch, Node 버전, 빌드 명령)과 **PR 미리보기 URL의 루트 페이지가 열림**을 ROADMAP Task 014 `기록:`에 남긴다.
- 혼용 허용 여부는 이미 `routes` 단일 형식이라 시험하지 않고, 배포가 설정 오류 없이 만들어졌는지만 기록한다.
- 실패하면 **오류 메시지 원문**을 Claude에게 알려 Task 009(`vercel.json`)를 수정한다(`feature/f0-vercel-routes` PR).
- 어느 쪽이든 PRD 10.3 R-17을 갱신한다.

## Task 015: 비밀 값 저장 위치 정리와 등록 (선행: 002, 014)
**범위**: GitHub·Vercel·로컬만. Cloudtype은 Task 020. 위 "비밀 값 보관 규칙"의 표가 저장 위치표의 기본이다.

### 할 일
- [ ] **저장 위치표 확정**: 위 표를 확인하고 달라지는 항목이 있으면 고친다(값 제외, 이름만). Cloudtype 항목은 표에만 적고 등록은 Task 020에서 한다.
- [ ] **난수 생성**(위 "난수 만드는 법"): `JWT_SECRET`(256비트 이상), `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`을 각각 따로 만들어 비밀번호 관리자에 먼저 저장한다. 개발용·운영용 `JWT_SECRET`·`ADMIN_BATCH_TOKEN`은 서로 다른 값이다.
- [ ] **GitHub Actions secrets 등록**: 저장소 → Settings → Secrets and variables → Actions → New repository secret
  - `ADMIN_BATCH_TOKEN`
  - `ORIGIN_SECRET`
  - (Cloudtype 백엔드 주소·배포 토큰은 Task 020)
  - 등록 후에는 값을 다시 볼 수 없다. 틀리면 같은 이름으로 덮어쓴다.
- [ ] **Vercel 환경 변수 등록**: 프로젝트 → Settings → Environment Variables
  - `ORIGIN_SECRET`을 **Production과 Preview 환경 둘 다** 체크해서 등록한다(`vercel.json`의 `routes[].transforms`가 읽고, Task 094의 미리보기 확인이 Preview 값을 쓴다).
  - **GitHub secrets의 `ORIGIN_SECRET`과 같은 값**이어야 한다.
  - `VITE_`로 시작하는 변수에 비밀 값을 넣지 않는다.
- [ ] **로컬 `.env` 만들기**: 저장소 루트에서 복사한 뒤 필요한 값을 채운다.
  ```powershell
  Copy-Item .env.example .env
  ```
  - 개발에 필요한 값만 채운다(`JWT_SECRET` 개발용, `ADMIN_BATCH_TOKEN` 개발용, `TMDB_API_KEY`, Google OAuth, 메일). 운영 DB·Redis 값은 넣지 않는다.
  - `git status`에 `.env`가 나타나지 않는지 확인한다(`.gitignore`가 막는다).
- [ ] **노출 시 재발급 절차**: 위 "노출됐을 때" 표가 기준이다. 빠진 값이 있으면 한 줄 추가한다.

### 완료 기준(V-H)
저장 위치표와 **GitHub·Vercel(Production·Preview)·로컬 등록일**을 ROADMAP Task 015 `기록:`에 남기고, **저장소에 비밀 값이 없음**을 확인한다.
```powershell
git status --short                     # .env 가 목록에 없어야 함
git grep -n -i -E "secret|password|token" -- . ':!docs' ':!.env.example'   # 값처럼 보이는 문자열이 없는지 눈으로 확인
```

## Task 020: Cloudtype 서비스 생성과 환경 변수·배포 토큰 등록 (선행: 002, 015, 019, 기한 2026-10-12)
**목적**: 첫 배포(Task 021)의 백엔드 서비스와 배포 연결을 준비한다. Task 019(서비스 목록 API 코드)가 끝난 뒤에 한다.

### 할 일
- [ ] **R-12 결정**: Cloudtype 문서에서 아래를 확인하고 결정한 내용을 Claude에게 알려 준다(Task 021 워크플로 작성의 입력).
  - GitHub Actions로 배포하는 공식 액션(또는 방법)이 있는지
  - 배포 토큰의 **이름과 발급 위치**
  - 비공개·자동 배포 대신 Actions 배포를 쓸지 여부
  - **하비는 커스텀 이미지 배포·이미지 저장소 연결이 프로 전용이라 쓸 수 없다**(Task 003 조사). 그래서 Cloudtype이 저장소에서 직접 빌드(Dockerfile·빌드팩)하고 GitHub Actions는 그 배포를 트리거하는 방식이 전제다
- [ ] **Redis Cloud 무료 DB 생존 확인**: 14일 동안 Redis 명령이 없으면 삭제된다(Task 003 조사). 환경 변수를 등록하기 전에 DB가 살아 있는지 확인하고, 삭제됐으면 다시 만든다
- [ ] **운영 백엔드 서비스 생성**(무료 플랜): 런타임 Java 17, 포트 8080, 활성 프로필 `prod`. Task 022의 일회성 서비스와 **이름으로 구분**한다(예: `ottnavi-backend` / `ottnavi-smtp-trial`).
- [ ] **환경 변수 등록**: Task 015 위치표의 Cloudtype 항목을 등록한다.
  - 지금 필요한 것: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `JWT_SECRET`(운영용), `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`, `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL`
  - 첫 배포 기동에 필요 없는 값(`GOOGLE_*`, `TMDB_API_KEY`, `MAIL_*`)은 Task 092 최종 점검 때 채워도 된다.
  - `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`은 GitHub secrets와 **같은 값**이어야 한다.
- [ ] **GitHub Actions secrets에 Cloudtype 배포 토큰과 백엔드 주소 등록**(이름은 R-12 결정): 저장소 → Settings → Secrets and variables → Actions.
- [ ] 무료 플랜이 꺼져 있으면 대시보드에서 서비스를 기동해야 첫 배포 확인(Task 021)이 가능하다.

### 완료 기준(V-H)
**서비스 이름·생성일, 등록한 변수 이름 목록(값 제외), R-12 결정 내용**을 ROADMAP Task 020 `기록:`에 남기고 PRD 10.3 R-12를 갱신한다.

---

## 완료한 설정 기록 (Task 번호 밖의 작업)
- **GitHub CLI(`gh`)**: 설치와 `gh auth login` 완료(2026-10-04). PR 생성에 쓴다.
- **CodeRabbit**: 저장소에 연결했고 루트 `.coderabbit.yaml`로 `develop` 대상 PR 리뷰·한국어·생성 파일 제외를 설정했다(PR #1에서 동작 확인). Autofix·Autopilot은 쓰지 않는다. 만약 켜져 있다면 코멘트 속 Coding task 화면에서 Autopilot을 끈다.

## 사용자가 직접 실행하는 Git/배포 작업 (Claude는 요청 시에만)
- Task 021: feature → `develop` PR(squash) 후 `develop` → `main` PR(merge commit) 병합 = 첫 배포, 이후 `main` 태그
- Task 022: 일회성 Cloudtype 서비스 배포·시험·삭제, 시험 브랜치 삭제, R11-11 결정
