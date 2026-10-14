# 백엔드 패키지 지도

> 백엔드 폴더가 무엇을 하는 곳인지 한눈에 보는 지도다. 근거 문서가 아니고, 코드와 어긋나면 코드가 기준이다.
> **갱신 규칙:** 패키지(폴더)를 만들거나 옮기거나 지우거나 역할이 바뀌는 PR은 이 문서를 같은 PR에서 고친다.
> 마지막 갱신: 2026-10-15 (Task 046 반영 기준)

## 지도 (`backend/src/main/java/com/ottnavi`)

```
com.ottnavi
├── global/              공통: 어느 기능에서나 쓰는 것
│   ├── common/            CommonResponse(성공 응답 틀), PageResponse(목록 응답 틀), BaseTimeEntity(생성·수정 시각)
│   ├── config/            Redis, Feign(외부 호출) 설정
│   ├── error/             GlobalExceptionHandler, ErrorCode, BusinessException과 하위 예외(FieldValidationException, ResourceNotFoundException)
│   └── security/          보안 설정, 오리진 비밀 헤더 필터, 관리자 배치 토큰 필터(AdminBatchTokenFilter), 진입 IP 로그 필터, 401·403 응답 처리
├── infra/               외부 시스템과 통신
│   └── tmdb/              TmdbClient(호출 창구), 재시도·속도 제한
│       ├── dto/             TMDB가 주는 JSON 틀 (안에서만 쓰고 밖으로 내보내지 않음)
│       └── exception/
├── catalog/             작품·시즌·제공 상태 (핵심 데이터)
│   ├── domain/
│   │   ├── entity/          DB 테이블과 1:1 (Title, WatchUnit, Availability, AvailabilityChange …)
│   │   ├── enums/
│   │   ├── repository/      DB 조회·저장 인터페이스
│   │   └── rule/            판단 규칙 계산기 (AvailabilityJudge, RuntimeCalculator …)
│   └── service/           흐름 조율: TitleCollectService, TitleDetailCollectService, AvailabilityCollectService, AvailabilityChangeQueryService(실행별 변경 조회)
│       └── model/           서비스끼리 주고받는 값 묶음 (CandidateTitle, TitleDetail, UnitDetail, ExecutionChange …)
├── provider/            OTT 서비스·TMDB 제공처
│   ├── controller/        OttServiceController (서비스 목록, 공개 API)
│   ├── dto/
│   │   └── response/        OttServiceResponse
│   ├── service/           ProviderCollectService, AdminProviderService(매핑 조회·변경) 등 + model/ (MappedProvider, AdminProviderView …)
│   └── domain/            entity · enums · repository
├── product/             구독 상품·가격 (엔티티·리포지토리만 있음)
├── admin/               관리자 API: 수집 실행·복구·재시작·이력 조회(Task 045), 제공처 매핑 조회·변경(Task 046). 보호는 배치 토큰 필터(제공처 매핑은 Task 054 전까지 미보호)
│   ├── controller/        AdminCollectController (/api/admin/collect/executions), AdminProviderController (/api/admin/providers, 서비스는 provider/service/AdminProviderService 호출)
│   ├── service/           CollectExecutionService(시작·재시작·복구), CollectExecutionQueryService(이력·변경 조회)
│   │   └── model/           CollectViewContext(요청 한 번의 판정 맥락)
│   ├── dto/
│   │   ├── request/         CreateCollectExecutionRequest, UpdateAdminProviderRequest
│   │   └── response/        CollectExecutionResponse, CollectExecutionAcceptedResponse, CollectExecutionChangeResponse, AdminProviderResponse, AdminProviderListResponse
│   └── exception/         409 예외 4종(BatchAlreadyRunning·BatchAlreadyCompleted·BatchRecoveryRequired·BatchNotStopped)
├── collect/             TMDB에서 데이터를 모으는 배치(수집 Job)
│   ├── job/               Job 조립(CollectJobConfig), 실행 파라미터, 설정값(CollectProperties), 비동기 operator 조립(CollectLaunchConfig), 시작·재시작·복구 창구(CollectJobLauncher), 이력 조회(CollectExecutionQuery), 상태·버튼 판정 규칙(CollectExecutionRules), 서버 기동 시각(ServerBootTime)
│   └── step/
│       ├── CollectPoolMonitor  DB 연결 사용량을 로그로 남기는 보조 도구(두 Step 묶음이 같이 씀)
│       ├── candidate/       후보 수집 Step: Reader · Writer · Mapper · Tasklet
│       └── detail/          상세·시즌·판정 Step: Processor · Writer · Mapper (Reader는 DetailStepConfig에서 조립)
└── engine/              플랜 계산 엔진 (순수 Java, 프레임워크 없음)
    ├── model/             입력·출력 값 틀
    ├── rule/              평가·비교 규칙
    └── solver/            완전탐색·그리디 풀이
```

아직 폴더가 없는 영역: `user`, `wishlist`, `plan`, `notification`. 호출 방향은 `controller → service → domain` 한쪽이다(규칙은 `.claude/rules/backend.md`).

## 보조 설명

**`dto`와 `service/model`의 차이**

| | 누가 주고받나 | 예시 |
|---|---|---|
| `dto` | 프론트(JSON)와 주고받음 | `OttServiceResponse`, `CollectExecutionResponse` |
| `service/model` | 서버 안에서 서비스끼리 주고받음 | `CandidateTitle` |

**`entity`와 `rule`의 차이:** `Availability`(엔티티)는 "이 시즌은 넷플릭스에서 AVAILABLE"이라는 결과를 담는 그릇이고, `AvailabilityJudge`(rule)는 그 결과를 어떻게 결정하는지 계산한다. rule은 DB·TMDB를 부르지 않아 DB 없이 테스트한다.

**값이 흐르는 길 (수집)**

```
TMDB ─▶ infra/tmdb (TmdbClient)        외부 호출
          ─▶ collect/step (Mapper)     TMDB 응답을 우리 값으로 변환
              ─▶ catalog/domain/rule   판정·러닝타임 계산
                  ─▶ catalog/service   저장 조율(트랜잭션)
                      ─▶ domain/repository ─▶ DB
```

**파일 이름으로 종류 구분하기**

| 이름 | 하는 일 |
|---|---|
| `XxxConfig` | 설정(빈 조립) |
| `XxxService` | 흐름 조율, 트랜잭션 |
| `XxxRepository` | DB 조회·저장 |
| `XxxMapper` | 한 틀을 다른 틀로 변환 |
| `XxxReader` / `XxxProcessor` / `XxxWriter` | 배치에서 읽기 / 가공 / 저장 |
| `XxxJudge`, `XxxCalculator`, `XxxDetector` | 판단·계산 규칙(`domain/rule`) |

**이전 프로젝트(Hansabari)와 이름 대응:** `client` → `infra/tmdb`, `scheduler` → `collect`, `entity`·`repository`·`enums` → `domain/` 아래로 한 겹 더 들어감, 서비스 메서드 안의 `if`문 → `domain/rule`.
