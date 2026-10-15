# 백엔드 패키지 지도

> 백엔드 폴더가 무엇을 하는 곳인지 한눈에 보는 지도다. 근거 문서가 아니고, 코드와 어긋나면 코드가 기준이다.
> **갱신 규칙:** 패키지(폴더)를 만들거나 옮기거나 지우거나 역할이 바뀌는 PR은 이 문서를 같은 PR에서 고친다.
> 마지막 갱신: 2026-10-16 (Task 056 반영 기준)

## 지도 (`backend/src/main/java/com/ottnavi`)

```
com.ottnavi
├── global/              공통: 어느 기능에서나 쓰는 것
│   ├── common/            CommonResponse(성공 응답 틀), PageResponse(목록 응답 틀), BaseTimeEntity(생성·수정 시각)
│   ├── config/            Redis(캐시 이름·TTL·직렬화), 캐시 오류 무시(CacheErrorHandlingConfig), Feign(외부 호출) 설정, 시계(ClockConfig, KST 기준 Clock 빈)
│   ├── error/             GlobalExceptionHandler, ErrorCode, BusinessException과 하위 예외(FieldValidationException, ResourceNotFoundException)
│   ├── ratelimit/         공개 API 요청 제한: 필터(PublicRateLimitFilter), 일반·단건 수집 버킷 검사(PublicRateLimiter, SingleFetchRateLimiter), Redis 버킷 창구, 설정
│   └── security/          보안 설정(SecurityConfig), 오리진 비밀 헤더 필터, 배치 토큰 인증 필터(BatchTokenAuthenticationFilter), 진입 IP 로그 필터(debug), 401·403 응답 처리, AuthUser(인증 주체)
│       ├── jwt/             Access Token 발급·검증(JwtTokenProvider), Bearer 인증 필터(JwtAuthenticationFilter), 설정(JwtProperties)
│       ├── oauth/           구글 로그인: 인가 요청 쿠키 저장소, 성공·실패 핸들러, Google 토큰 미저장(NoOpOAuth2AuthorizedClientRepository)
│       └── token/           Refresh Token: RefreshTokenStore 인터페이스, Redis 구현(RedisRefreshTokenStore, Lua), RT 쿠키(RefreshTokenCookies), 회전 결과(RefreshRotation)
├── infra/               외부 시스템과 통신
│   └── tmdb/              TmdbClient(호출 창구), 재시도·속도 제한
│       ├── dto/             TMDB가 주는 JSON 틀 (안에서만 쓰고 밖으로 내보내지 않음)
│       └── exception/
├── catalog/             작품·시즌·제공 상태 (핵심 데이터)
│   ├── controller/        TitleController (작품 검색·상세, 공개 API /api/public/titles)
│   ├── dto/
│   │   └── response/        TitleSearchPageResponse, TitleDetailResponse, TitleSeasonResponse …
│   ├── exception/         TitleNotFoundException (404 TITLE_NOT_FOUND)
│   ├── domain/
│   │   ├── entity/          DB 테이블과 1:1 (Title, WatchUnit, Availability, AvailabilityChange …)
│   │   ├── enums/
│   │   ├── repository/      DB 조회·저장 인터페이스
│   │   └── rule/            판단 규칙 계산기 (AvailabilityJudge, RuntimeCalculator, AvailabilityAggregation …)
│   └── service/           흐름 조율: TitleCollectService, TitleDetailCollectService, AvailabilityCollectService, AvailabilityChangeQueryService(실행별 변경 조회), TitleSearchService(검색), TitleDetailQueryService(상세, 캐시), TitleAvailabilityLoader(판정 일괄 조회), WatchUnitQueryService(시청 단위 ID 찾기·요약 일괄 조회, 찜이 사용)
│       └── model/           서비스끼리 주고받는 값 묶음 (CandidateTitle, TitleDetail, UnitDetail, ExecutionChange, TitleDetailView …)
├── provider/            OTT 서비스·TMDB 제공처
│   ├── controller/        OttServiceController (서비스 목록, 공개 API)
│   ├── dto/
│   │   └── response/        OttServiceResponse
│   ├── service/           ProviderCollectService, AdminProviderService(매핑 조회·변경) 등 + model/ (MappedProvider, AdminProviderView …)
│   └── domain/            entity · enums · repository
├── user/                회원·로그인·내 설정
│   ├── controller/        AuthController (POST /api/auth/refresh, /api/auth/logout), UserSettingController (GET·PUT /api/me/settings)
│   ├── dto/
│   │   ├── request/         UpdateMySettingsRequest, UserSubscriptionRequest
│   │   └── response/        AccessTokenResponse, UserSettingsResponse, UserSubscriptionResponse
│   ├── service/           UserLoginService(가입·갱신·역할 조회), AuthTokenService(재발급·로그아웃, Origin 검사), UserSettingService(내 설정 조회·전체 교체, users 행 잠금), UserQueryService(다른 도메인용 사용자 행 잠금·이용 가능 서비스 조회·서비스별 이용 상태 맵 조회) + model/ (GoogleProfile, LoginUser, TokenIssue, SubscriptionState, UserSettingsState)
│   └── domain/            entity(User, UserSetting, UserSubscription) · enums(OAuthProvider, UserRole, WatchPreset, SubscriptionStatus, ServiceSubscriptionStatus) · repository(UserRepository, UserSettingRepository, UserSubscriptionRepository)
├── wishlist/            찜·우선순위·"다 봤음" (Task 056)
│   ├── controller/        WishlistController (GET·POST /api/me/wishlist-items, PATCH·DELETE /api/me/wishlist-items/{id})
│   ├── dto/
│   │   ├── request/         AddWishlistItemRequest, UpdateWishlistItemRequest
│   │   └── response/        WishlistItemListResponse, WishlistItemResponse, AddWishlistItemResultResponse, UpdatedWishlistItemResponse
│   ├── exception/         WishlistLimitExceededException(409), WishlistItemNotFoundException(404)
│   ├── service/           WishlistService(목록·추가·변경·삭제, 상한 20 판정과 사용자 행 잠금 직렬화) + model/ (WishlistView, WishlistItemView, AddWishlistResult, UpdatedWishlistItemView)
│   └── domain/            entity(WishlistItem) · enums(WishlistPriority) · repository(WishlistItemRepository). 사용자·시청 단위는 ID만 들고 catalog·user 서비스를 통해 읽는다
├── product/             구독 상품·월 구독요금
│   ├── controller/        AdminProductController (/api/admin/products, 상품 등록·수정과 요금 이력 추가·수정·삭제·확인함, Task 059), MyServicePriceController (/api/me/service-prices, 내 요금 조회·수정·되돌리기, Task 060)
│   ├── dto/
│   │   ├── request/         CreateAdminProductRequest, UpdateAdminProductRequest, AdminProductPriceRequest, SetMyServicePriceRequest
│   │   └── response/        AdminProductResponse, AdminProductPriceResponse, AdminProductListResponse, ServicePriceResponse, ServicePriceListResponse
│   ├── service/           AdminProductService(관리자 상품·요금 관리), ProductPriceQueryService(기준일 유효 요금 조회, 계산·내 요금이 재사용), MyServicePriceService(내 요금 조회·수정·되돌리기, 사용자 행 잠금 직렬화) + model/ (AdminProductView, ProductPriceView, MyServicePriceView)
│   ├── exception/         ProductPriceConflictException (PRICE_* 409 6종을 코드로 구분), ServicePriceNotFoundException(404)
│   └── domain/            entity(…, UserPriceOverride: 사용자·상품은 ID만 보관) · enums(ProductType, PriceSource) · repository(…, UserPriceOverrideRepository) · rule(ProductPriceRules: 요금 이력 추가·수정·삭제 허용 판정과 기준일 선택)
├── admin/               관리자 API: 수집 실행·복구·재시작·이력 조회(Task 045), 제공처 매핑 조회·변경(Task 046). 보호는 ADMIN JWT 또는 배치 토큰(Task 054)
│   ├── controller/        AdminCollectController (/api/admin/collect/executions), AdminProviderController (/api/admin/providers, 서비스는 provider/service/AdminProviderService 호출)
│   ├── service/           CollectExecutionService(시작·재시작·복구), CollectExecutionQueryService(이력·변경 조회)
│   │   └── model/           CollectViewContext(요청 한 번의 판정 맥락)
│   ├── dto/
│   │   ├── request/         CreateCollectExecutionRequest, UpdateAdminProviderRequest
│   │   └── response/        CollectExecutionResponse, CollectExecutionAcceptedResponse, CollectExecutionChangeResponse, AdminProviderResponse, AdminProviderListResponse
│   └── exception/         409 예외 4종(BatchAlreadyRunning·BatchAlreadyCompleted·BatchRecoveryRequired·BatchNotStopped)
├── collect/             TMDB에서 데이터를 모으는 배치(수집 Job)
│   ├── job/               Job 조립(CollectJobConfig), 실행 파라미터, 설정값(CollectProperties), 비동기 operator 조립(CollectLaunchConfig), 시작·재시작·복구 창구(CollectJobLauncher), 이력 조회(CollectExecutionQuery), 상태·버튼 판정 규칙(CollectExecutionRules), 서버 기동 시각(ServerBootTime), 완료 시 작품 상세 캐시 비우기(TitleCacheEvictListener)
│   └── step/
│       ├── CollectPoolMonitor  DB 연결 사용량을 로그로 남기는 보조 도구(두 Step 묶음이 같이 씀)
│       ├── candidate/       후보 수집 Step: Reader · Writer · Mapper · Tasklet
│       └── detail/          상세·시즌·판정 Step: Processor · Writer · Mapper (Reader는 DetailStepConfig에서 조립)
└── engine/              플랜 계산 엔진 (순수 Java, 프레임워크 없음)
    ├── model/             입력·출력 값 틀
    ├── rule/              평가·비교 규칙
    └── solver/            완전탐색·그리디 풀이
```

아직 폴더가 없는 영역: `plan`, `notification`. 호출 방향은 `controller → service → domain` 한쪽이다(규칙은 `.claude/rules/backend.md`).

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
