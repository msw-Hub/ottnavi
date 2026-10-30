# 백엔드 패키지 지도

> 백엔드 폴더가 무엇을 하는 곳인지 한눈에 보는 지도다. 근거 문서가 아니고, 코드와 어긋나면 코드가 기준이다.
> **갱신 규칙:** 패키지(폴더)를 만들거나 옮기거나 지우거나 역할이 바뀌는 PR은 이 문서를 같은 PR에서 고친다.
> 마지막 갱신: 2026-10-28 (Task 072 + 영어 줄거리 번역 llmdraft 반영 기준)

## 지도 (`backend/src/main/java/com/ottnavi`)

```
com.ottnavi
├── global/              공통: 어느 기능에서나 쓰는 것
│   ├── common/            CommonResponse(성공 응답 틀), PageResponse(목록 응답 틀), BaseTimeEntity(생성·수정 시각)
│   ├── config/            Redis(캐시 이름·TTL·직렬화), 캐시 오류 무시(CacheErrorHandlingConfig), Feign(외부 호출) 설정, 시계(ClockConfig, KST 기준 Clock 빈), 스케줄러(SchedulingConfig: @EnableScheduling, ShedLock 락 제공자 JDBC·DB 시각), 비동기(AsyncConfig: @EnableAsync만, 실행기는 Boot 자동 구성 `spring.task.execution.*`), 관측(Task 096, `observability` 프로필에서만 켜짐: ObservabilityConfig(OTLP 지표 전송 설정)·OtlpMetricsAllowFilter(전송 허용 목록, 개인정보 태그 키 거부), Loki 로그 전송은 `resources/logback-spring.xml` + LokiLogFilter(로그 수준·제외 대상 필터)·LokiRootCauseConverter(스택 대신 근본원인 클래스 이름만))
│   ├── error/             GlobalExceptionHandler, ErrorCode, BusinessException과 하위 예외(FieldValidationException, ResourceNotFoundException)
│   ├── ratelimit/         공개 API 요청 제한: 필터(PublicRateLimitFilter), 일반·단건 수집·데모 로그인 버킷 검사(PublicRateLimiter, SingleFetchRateLimiter, DemoLoginRateLimiter: `rl:demo:{ip}` IP당 15분 5회, Task 109), Redis 버킷 창구, 설정
│   └── security/          보안 설정(SecurityConfig), 오리진 비밀 헤더 필터, 배치 토큰 인증 필터(BatchTokenAuthenticationFilter), 진입 IP 로그 필터(debug), 401·403 응답 처리, AuthUser(인증 주체)
│       ├── jwt/             Access Token 발급·검증(JwtTokenProvider), Bearer 인증 필터(JwtAuthenticationFilter), 설정(JwtProperties)
│       ├── oauth/           구글 로그인: 인가 요청 쿠키 저장소, 성공·실패 핸들러, Google 토큰 미저장(NoOpOAuth2AuthorizedClientRepository)
│       └── token/           Refresh Token: RefreshTokenStore 인터페이스, Redis 구현(RedisRefreshTokenStore, Lua), RT 쿠키(RefreshTokenCookies), 회전 결과(RefreshRotation)
├── infra/               외부 시스템과 통신
│   ├── tmdb/              TmdbClient(호출 창구), 재시도·속도 제한
│   │   ├── dto/             TMDB가 주는 JSON 틀 (안에서만 쓰고 밖으로 내보내지 않음)
│   │   └── exception/
│   ├── mail/              메일 발송 창구(Task 072): MailClient(포트), SmtpMailClient(app.mail.provider=smtp, 타임아웃은 spring.mail.*), LogMailClient(provider=log, local 기본: 제목만 로그), OutgoingMail(보낼 메일 값), AppMailProperties(app.mail.*)·MailConfig
│   │   └── exception/       MailSendException (발송 실패, 재시도 여부는 notification이 정함)
│   └── llm/               Gemini 호출 창구(관리자 줄거리 번역, Spring AI): GeminiClient(번역 호출, 호출 제한 시간 `app.llm.timeout-seconds`, `llm.call` 지표, 키가 없으면 LlmDisabledException, 키 유무 `isEnabled`), LlmPromptService(프롬프트 조립), LlmProperties(app.llm.*)·LlmConfig(키가 있을 때만 Spring AI Gemini 자동 구성을 켠다)
│       └── exception/       LlmException(부모, BusinessException 상속), LlmDisabledException(503), LlmRateLimitedException(429), LlmCallFailedException(502), LlmUpstreamBusyException(502, 구글 서버 혼잡)
├── catalog/             작품·시즌·제공 상태 (핵심 데이터)
│   ├── controller/        TitleController (작품 검색·상세, 공개 API /api/public/titles), ExclusiveTitleController (서비스별 독점 목록, GET /api/public/ott-services/{ottServiceId}/exclusive-titles, Task 078)
│   ├── dto/
│   │   └── response/        TitleSearchPageResponse, TitleDetailResponse, TitleSeasonResponse …
│   ├── exception/         TitleNotFoundException (404 TITLE_NOT_FOUND)
│   ├── domain/
│   │   ├── entity/          DB 테이블과 1:1 (Title, WatchUnit, Availability, AvailabilityChange …)
│   │   ├── enums/
│   │   ├── repository/      DB 조회·저장 인터페이스
│   │   └── rule/            판단 규칙 계산기 (AvailabilityJudge, RuntimeCalculator, AvailabilityAggregation, ExclusiveRules(서비스별 독점 판정) …)
│   └── service/           흐름 조율: TitleCollectService, TitleRefreshTierService(찜 현황에 맞춰 작품 refresh_tier를 벌크로 재맞춤, 수집 시작 Step이 부름, Task 077), TitleDetailCollectService, AvailabilityCollectService, AvailabilityChangeQueryService(실행별 변경 조회), TitleSearchService(검색), TitleCardAssembler(작품 목록 → 서비스별 제공 상태 카드, 검색·탐색·홈이 공용, Task 107), TitleExploreService(장르 목록·장르별·출연자 이름별 작품, Task 107), NewlyAvailableQueryService(새로 볼 수 있게 된 작품: 입고·공개 후보 조회 → NewArrivalPicker로 대표 선택, Task 107), TitleDetailQueryService(상세, 캐시), TitleAvailabilityLoader(판정 일괄 조회), WatchUnitQueryService(시청 단위 ID 찾기·요약 일괄 조회, 찜이 사용), StaleTitleQueryService(마지막 수집이 오래된 작품 수·목록, Task 083), TitlePurgeService(사라진 작품과 하위 행 삭제, Task 083), ExclusiveTitleCalculator(독점 목록 계산)·ExclusiveTitleCacheService(Redis `exclusiveTitlesV2` 읽기·채우기·비우기, 캐시 미스 폴백)·ExclusiveTitleQueryService(페이지 조회, 없는 서비스 404) (Task 078), TitleOverviewService(관리자 줄거리 번역용: 영어 줄거리 후보 조회(찜 수·인기순), 조건부 반영, 관리자 번역 줄거리 비우기(번역 반영 시각 `overview_translated_at`이 있는 것만, 단건·묶음), 번역 통계, 작품 상세 캐시 1개 삭제. llmdraft가 호출)
│       └── model/           서비스끼리 주고받는 값 묶음 (CandidateTitle, TitleDetail, UnitDetail, ExecutionChange, TitleDetailView …)
├── provider/            OTT 서비스·TMDB 제공처
│   ├── controller/        OttServiceController (서비스 목록, 공개 API)
│   ├── dto/
│   │   └── response/        OttServiceResponse
│   ├── exception/         OttServiceNotFoundException (404 OTT_SERVICE_NOT_FOUND, Task 078)
│   ├── service/           ProviderCollectService, AdminProviderService(매핑 조회·변경) 등 + model/ (MappedProvider, AdminProviderView …)
│   └── domain/            entity · enums · repository
├── user/                회원·로그인·내 설정
│   ├── controller/        AuthController (POST /api/auth/refresh, /api/auth/logout, /api/auth/demo-login), UserSettingController (GET·PUT /api/me/settings), UserController (DELETE /api/me 탈퇴)
│   ├── dto/
│   │   ├── request/         UpdateMySettingsRequest, UserSubscriptionRequest, DemoLoginRequest(데모 로그인, toString에서 입력값을 가림)
│   │   └── response/        AccessTokenResponse, UserSettingsResponse, UserSubscriptionResponse
│   ├── service/           UserLoginService(가입·갱신·역할 조회, 데모 계정 행 찾기·만들기 `findOrCreateDemo`), AuthTokenService(재발급·로그아웃, Origin 검사, 데모 로그인 직후 토큰 발급 `issueForLogin`, 데모를 끈 뒤 남은 데모 세션 폐기), DemoLoginService(데모 로그인: 꺼짐 404·시도 한도·아이디·비밀번호 상수 시간 비교, Task 109)·DemoLoginProperties(`app.demo-login.*`, 기동 검증)·DemoLoginConfig(설정 등록)·DemoAccount(데모 행 고정값: subject·이메일·이름), UserSettingService(내 설정 조회·전체 교체, 알림 수신 여부 변경 `updateNotificationEnabled`, users 행 잠금), UserWithdrawService·UserWithdrawWriter(탈퇴: DB 삭제 커밋 뒤 Redis 계열 삭제), UserQueryService(다른 도메인용 사용자 행 잠금·이용 가능 서비스 조회·서비스별 이용 상태 맵 조회·이메일 조회(findEmail, 알림 발송 직전 수신 주소 확인)) + model/ (GoogleProfile, LoginUser, TokenIssue, SubscriptionState, UserSettingsState)
│   ├── exception/         OnboardingRequiredException (409 ONBOARDING_REQUIRED, 알림 설정 저장, Task 073), DemoLoginFailedException (401 DEMO_LOGIN_FAILED, Task 109)
│   └── domain/            entity(User, UserSetting, UserSubscription) · enums(OAuthProvider, UserRole, WatchPreset, SubscriptionStatus, ServiceSubscriptionStatus) · repository(UserRepository, UserSettingRepository, UserSubscriptionRepository)
├── wishlist/            찜·우선순위·"다 봤음" (Task 056)
│   ├── controller/        WishlistController (GET·POST /api/me/wishlist-items, PATCH·DELETE /api/me/wishlist-items/{id})
│   ├── dto/
│   │   ├── request/         AddWishlistItemRequest, UpdateWishlistItemRequest
│   │   └── response/        WishlistItemListResponse, WishlistItemResponse, AddWishlistItemResultResponse, UpdatedWishlistItemResponse
│   ├── exception/         WishlistLimitExceededException(409), WishlistItemNotFoundException(404)
│   ├── service/           WishlistService(목록·추가·변경·삭제, 상한 20 판정과 사용자 행 잠금 직렬화, 공개 홈용 작품별 찜 사용자 수 listMostWishlistedTitles) + model/ (WishlistView, WishlistItemView, AddWishlistResult, UpdatedWishlistItemView, TitleWishlistCount)
│   └── domain/            entity(WishlistItem) · enums(WishlistPriority) · repository(WishlistItemRepository). 사용자·시청 단위는 ID만 들고 catalog·user 서비스를 통해 읽는다
├── product/             구독 상품·월 구독요금
│   ├── controller/        AdminProductController (/api/admin/products, 상품 등록·수정과 요금 이력 추가·수정·삭제·확인함, Task 059), MyServicePriceController (/api/me/service-prices, 내 요금 조회·수정·되돌리기, Task 060)
│   ├── dto/
│   │   ├── request/         CreateAdminProductRequest, UpdateAdminProductRequest, AdminProductPriceRequest, SetMyServicePriceRequest
│   │   └── response/        AdminProductResponse, AdminProductPriceResponse, AdminProductListResponse, ServicePriceResponse, ServicePriceListResponse
│   ├── service/           AdminProductService(관리자 상품·요금 관리), ProductPriceQueryService(기준일 유효 요금 조회, 계산·내 요금이 재사용), MyServicePriceService(내 요금 조회·수정·되돌리기, 사용자 행 잠금 직렬화), ProductSummaryQueryService(상품 이름·포함 서비스 일괄 조회, 판매 종료 상품 포함, 저장된 플랜 조회가 사용) + model/ (AdminProductView, ProductPriceView, MyServicePriceView, ProductSummary)
│   ├── exception/         ProductPriceConflictException (PRICE_* 409 6종을 코드로 구분), ServicePriceNotFoundException(404)
│   └── domain/            entity(…, UserPriceOverride: 사용자·상품은 ID만 보관) · enums(ProductType, PriceSource) · repository(…, UserPriceOverrideRepository) · rule(ProductPriceRules: 요금 이력 추가·수정·삭제 허용 판정과 기준일 선택 · CalcCandidateRules: 서비스당 단품 1개와 번들을 모드에 따라 고르는 계산 후보 규칙)
├── plan/                3개월 플랜 저장·계산 (Task 062 엔티티·리포지토리, Task 063 계산 입력 조립, Task 064 계산 API·DRAFT 저장, Task 065 비교 측정 API, Task 066 초안 저장·조회, Task 069 변경 판정 기반)
│   ├── controller/        PlanController (POST /api/me/plan-drafts 플랜 계산, POST /api/me/plans 초안 저장, GET /api/me/plan-drafts 초안 조회, GET /api/me/plans/current 현재 플랜 조회, GET /api/me/plan-changes 변경 내역 조회), AdminPlanBenchmarkController (POST /api/admin/plan-benchmarks, ADMIN 전용 완전탐색·그리디 비교 측정), AdminPlanRecalcController (POST /api/admin/plans/monthly/executions, 재계산 수동 실행 202·409, ADMIN JWT 또는 배치 토큰)
│   ├── dto/
│   │   ├── request/         CreatePlanRequest
│   │   └── response/        SavedPlanResponse, CurrentPlanResponse, ActivePlanResponse, PlanDraftListResponse, PlanDraftResponse, PlanMonthResponse, PlanMonthProductResponse, PlanAssignmentResponse, UnscheduledItemResponse, PlanBenchmarkResponse
│   ├── exception/         PlanDraftNotFoundException(404), PlanDraftStaleException(409), PlanRecalcAlreadyRunningException(409 BATCH_ALREADY_RUNNING)
│   ├── batch/             매월 재계산 Job(Task 069): PlanRecalcJobConfig(Job·Step 조립), PlanRecalcLaunchConfig(재계산 전용 비동기 operator, defaultCandidate=false), PlanRecalcTasklet(대상 선정 → 플랜마다 재계산, 실패는 로그만 남기고 다음 플랜, 대상 수를 READ_COUNT에 기록), PlanRecalcLauncher(시작·실행 중 판정: 재계산·수집·데이터 정리가 실행 중이면 409, 정리 여부는 retention/job의 RetentionExecutionQuery로 본다), PlanRecalcTrigger(수집 완료 이벤트 → 자동 시작, app.plan-recalc.auto-start), PlanRecalcExecutionQuery(BATCH_* 조회: 실행 중 여부, 수집별 대상 플랜 수), PlanRecalcJobParameters, PlanRecalcProperties
│   ├── service/           PlanQueryService(내 초안·현재 플랜 조회, 읽기 전용), PlanViewLoader(저장된 플랜 → 응답 값 일괄 재구성, 계산 응답과 같은 값·순서), PlanActivateService(초안 저장: users 행 잠금 → 본인 DRAFT 조회 → 오래됨 판정 → 기존 ACTIVE 보관·flush → 활성화 → 나머지 DRAFT 삭제, 쓰기 트랜잭션 하나), PlanCalculateService(진입점: 입력 조립 → 계산 → 저장 → 응답 조립, 트랜잭션 없음), PlanInputService(설정·찜·제공 상태·요금·구독 상태 → 엔진 입력, 읽기 전용), PlanSolveService(입력 해시 키로 세 유형 계산, planCalc 캐시), RecordingPlanSolver(유형별 계산 시간·후보 수 기록), PlanResultWriter(플랜 한 건의 하위 행 저장, DRAFT 저장과 재계산이 공용), PlanChangeSummaryCodec(변경 요약 ↔ plan.change_summary JSON, v 필드), PlanRecalcTargetService(영향 플랜 선정), PlanRecalcService(재계산 진입점, 트랜잭션 없음), PlanRecalcWriter(users 행 잠금 → 이전 ACTIVE 보관·flush → 새 ACTIVE 저장 → 변경 요약 → 알림 행 생성(MONTHLY·PLAN_CHANGE, Task 073) → ARCHIVED 정리, 쓰기 트랜잭션 하나), PlanActivatedEvent(플랜 저장 사건: PlanActivateService가 발행), SubscribeGuideListener(저장 커밋 뒤 `@Async` + AFTER_COMMIT: 가입 안내 행 생성 → 즉시 발송. 알림이 plan을 모르게 하려고 plan에 둔다), PlanNotificationComposer(저장된 플랜 → 알림 재료 record: 가입·해지 예약 판정), PlanSnapshotLoader(저장된 플랜 → 변경 판정용 스냅샷), PlanChangeEvidenceAssembler(이유 판정 근거 모으기), PlanChangeHistoryService(변경 내역 조회 최근 6건), PlanPurgeService(데이터 정리용: 사라진 작품이 든 DRAFT·플랜 삭제, 영향 ACTIVE 조회, 오래된 끊긴 calc_run 삭제, Task 083), PlanDraftWriter(users 행 잠금 → 기존 DRAFT 삭제 → DRAFT 3건 저장), PlanDraftMapper(결과 → 달별 상품·비용·조각 번호 규칙), PlanDraftViewAssembler(저장 직후 ID와 결과로 응답 값 조립), AllSubscribeCostCalculator(전체 구독 비용, 순수 계산), EngineConfig(엔진 빈 등록) + model/ (PlanInputBundle, ProductPriceInfo, PlanCalcResult, PlanCalcResults, SavedDraft, SavedPlanView, PlanDraftView, ActivePlanView, PlanRecalcOutcome, PlanChangeHistoryEntryView) + benchmark/ (BenchmarkCase·BenchmarkCaseLoader: `engine-benchmark/index.txt` 목록의 정답 세트 JSON 로드, 테스트 소스에서 main으로 옮겨 테스트와 운영 측정이 같은 세트를 쓴다 · PlanBenchmarkService: 케이스×알고리즘 워밍업 1회 + 3회 중앙값 측정, MedianTimer · PlanBenchmarkRunService: 측정 → 저장 진입점, 트랜잭션 없음 · PlanBenchmarkWriter: `calc_run` 행 저장 · 값 묶음 BenchmarkCaseResult, SolverMeasurement, PlanBenchmarkRun, SavedBenchmarkCase)
│   └── domain/            entity(Plan, PlanMonth, PlanMonthProduct, PlanItem, PlanAssignment, CalcRun: 사용자·상품·시청 단위·서비스는 ID만 보관, 하위 행 삭제는 DB FK CASCADE) · enums(PlanStatus, PlanItemOutcome, PlanChangeReasonType, PlanTitleChangeType. 플랜 유형·알고리즘·우선순위·이유 코드는 engine.model, 가격 출처는 product PriceSource 재사용) · repository(PlanRepository: DRAFT 벌크 삭제·ACTIVE 잠금 조회 …) · rule(Task 069 변경 판정, 프레임워크 의존 없는 순수 Java: PlanChangeDetector 이전·새 플랜 비교 → 달 변화·작품 변화·이유 4종·요약, PlanChangeAlertRule 이번 달 확정 달에 배정된 작품이 그 서비스에서 빠졌는지 판정(변경 알림 대상, Task 073), PlanChangeMessages 문장·조사, 입력 값 PlanSnapshot·PlanChangeEvidence, 결과 값 PlanChangeSummary. 입출력 값을 service/model이 아닌 rule에 둔 것은 domain → service 의존을 피하려는 것)
├── notification/        알림 outbox와 메일 발송 기반(Task 072)과 설정·이력 API(Task 073). 알림 행 생성은 NotificationCreateService 한 곳이 관문이다. 알림은 plan을 모르고 plan이 알림을 부른다(재료 record는 이쪽 service/model에 있다). 사용자 이메일·이름·알림 수신 여부는 user 서비스(UserQueryService)로 읽는다
│   ├── controller/        NotificationController (GET·PUT /api/me/notification-setting, GET /api/me/notifications)
│   ├── dto/
│   │   ├── request/         UpdateMyNotificationSettingRequest
│   │   └── response/        NotificationSettingResponse, NotificationHistoryItemResponse
│   ├── scheduler/         NotificationSendScheduler (@Scheduled 1분 + ShedLock, app.notification.scheduler-enabled가 true일 때만 등록)
│   ├── service/           NotificationSettingService (알림 설정 조회·저장: user 서비스 위임, 트랜잭션 없음), NotificationQueryService (본인 이력 페이지·관리자 최근 7일 요약, 읽기 전용), NotificationCreateService (생성 관문: 알림 설정 확인 → payload JSON → `INSERT … ON CONFLICT (dedupe_key) DO NOTHING`. `create`는 호출한 트랜잭션에 합류, `createInNewTransaction`은 REQUIRES_NEW), NotificationClaimService (1단계: 짧은 트랜잭션으로 발송할 행 점유, SKIP LOCKED. `claimOne`은 즉시 발송용 한 건 점유, REQUIRES_NEW), NotificationDispatcher (트랜잭션 없음: 행·수신 주소 재확인 → 템플릿 → 발송. `dispatchNow`는 한 건 즉시 발송), NotificationResultRecorder (3단계: 결과 기록, 재시도 간격·FAILED 전이, REQUIRES_NEW), NotificationMailRenderer (유형별 Thymeleaf 템플릿·제목, templates/mail/), NotificationMetrics (notification.send 지표), NotificationProperties·NotificationConfig (app.notification.*) + model/ (NotificationSettingView, NotificationHistoryItem, NotificationSummaryView, ClaimedNotification, RenderedMail, 메일 재료 payload record SubscribeGuidePayload·MonthlyPayload·PlanChangePayload와 공용 NotificationProductItem·NotificationTitleItem)
│   └── domain/            entity(NotificationOutbox: 사용자는 ID만 보관, 삭제는 DB FK CASCADE) · enums(NotificationType, NotificationStatus) · repository(NotificationOutboxRepository: findDueForUpdate, findOneDueForUpdate, insertIfAbsent(네이티브 ON CONFLICT), findIdByDedupeKey, findByUserIdOrderByCreatedAtDescIdDesc, countByStatusAndCreatedAtGreaterThanEqual, findLatestSentAtSince)
├── admin/               관리자 API: 수집 실행·복구·재시작·이력 조회(Task 045), 제공처 매핑 조회·변경(Task 046). 보호는 ADMIN JWT 또는 배치 토큰(Task 054)
│   ├── controller/        AdminCollectController (/api/admin/collect/executions), AdminProviderController (/api/admin/providers, 서비스는 provider/service/AdminProviderService 호출), AdminNotificationController (GET /api/admin/notifications/summary, 서비스는 notification/service/NotificationQueryService 호출, Task 073), AdminSystemMetricsController (GET /api/admin/system-metrics, 운영 메모리 구성 조회, ADMIN 전용: SecurityConfig에서 ADMIN·DEMO 조회 규칙보다 앞에 둠)
│   ├── service/           CollectExecutionService(시작·재시작·복구, 재계산이나 데이터 정리가 실행 중이면 409: retention/job의 RetentionExecutionQuery 사용), CollectExecutionQueryService(이력·변경 조회), SystemMetricsService (JVM은 JMX, 프로세스는 /proc/self/status, 컨테이너는 cgroup 파일에서 읽는 읽기 전용 측정. 파일 경로는 생성자로 바꿀 수 있음), SystemMetricsParser (/proc·cgroup 글자를 바이트로 바꾸는 순수 함수), SystemMemoryMetrics (같은 측정값을 `ottnavi.process.rss`·`ottnavi.container.memory.used` 게이지로 노출하는 MeterBinder, 읽지 못하면 NaN)
│   │   └── model/           CollectViewContext(요청 한 번의 판정 맥락), SystemMetricsView(시스템 지표 값 묶음)
│   ├── dto/
│   │   ├── request/         CreateCollectExecutionRequest, UpdateAdminProviderRequest
│   │   └── response/        CollectExecutionResponse, CollectExecutionAcceptedResponse, CollectExecutionChangeResponse, AdminProviderResponse, AdminProviderListResponse, AdminNotificationSummaryResponse, SystemMetricsResponse
│   └── exception/         409 예외 4종(BatchAlreadyRunning·BatchAlreadyCompleted·BatchRecoveryRequired·BatchNotStopped)
├── collect/             TMDB에서 데이터를 모으는 배치(수집 Job)
│   ├── job/               Job 조립(CollectJobConfig), 실행 파라미터, 설정값(CollectProperties), 비동기 operator 조립(CollectLaunchConfig), 시작·재시작·복구 창구(CollectJobLauncher), 이력 조회(CollectExecutionQuery), 상태·버튼 판정 규칙(CollectExecutionRules), 서버 기동 시각(ServerBootTime), tier로 흐름 분기(CollectTierDecider: DAILY면 제공처 목록·후보 발견을 건너뜀, Task 077), 완료 시 작품 상세 캐시 비우기(TitleCacheEvictListener), 종료 시 독점 목록 캐시 갱신(ExclusiveTitlesRefreshListener: COMPLETED면 새로 계산해 채우고 아니면 비움, Task 078), COMPLETED일 때만 완료 이벤트 발행(CollectCompletionListener → CollectCompletedEvent, plan/batch의 재계산 시작이 받는다, Task 069)
│   ├── service/           CollectDataVersionService(마지막 COMPLETED 수집 종료 시각 = 데이터 기준 시각, 플랜 계산 입력이 사용)
│   └── step/
│       ├── CollectPoolMonitor  DB 연결 사용량을 로그로 남기는 보조 도구(두 Step 묶음이 같이 씀)
│       ├── refresh/         갱신 주기 재맞춤 Step: Tasklet(RefreshTierTasklet) + Step 조립(Task 077, scope 없는 모든 실행의 첫 Step)
│       ├── candidate/       후보 수집 Step: Reader · Writer · Mapper · Tasklet
│       └── detail/          상세·시즌·판정 Step: Processor · Writer · Mapper (Reader는 DetailStepConfig에서 조립, 읽을 작품 범위는 TierReaderParameterListener가 tier로 정함)
├── retention/           데이터 정리: 6개월 갱신 점검·TMDB에서 사라진 작품 삭제, 오래된 BATCH_* 이력·끊긴 calc_run 삭제 (Task 083). 각 도메인 서비스를 호출하는 조율 계층
│   ├── controller/        AdminRetentionController (POST /api/admin/retention/executions, 202·409, ADMIN JWT 또는 배치 토큰)
│   ├── dto/response/      RetentionExecutionAcceptedResponse
│   ├── exception/         RetentionAlreadyRunningException (409 BATCH_ALREADY_RUNNING)
│   ├── job/               RetentionJobConfig(Job·Step 3개, 앞 Step이 실패해도 다음으로), RetentionLaunchConfig(정리 전용 비동기 operator), RetentionLauncher(정리·재계산·수집 실행 중이면 409), RetentionExecutionQuery(BATCH_* 조회: 정리 실행 중 여부. admin·plan/batch도 쓴다), RetentionJobParameters, RetentionProperties·RetentionConfig(app.retention.*)
│   ├── service/           RetentionService(Step이 부르는 세 일: 작품 점검, 배치 이력 삭제, calc_run 삭제), TitleRetentionService(작품 한 건: 확인 → 갱신 또는 찜·플랜 정리 후 삭제), TitleExistenceProbe(작품 단위 404만 사라짐으로 판정), TitleRefresher(수집 상세 Processor 재사용), BatchHistoryPurger(BATCH_* 하위 테이블부터 삭제, 마지막 COMPLETED 수집 보존) + model/ (TitleExistence, TitleRetentionResult, TitleRetentionSummary)
│   └── step/              TitleRetentionTasklet, BatchHistoryPurgeTasklet, CalcRunPurgeTasklet
├── home/                홈·랭킹·탐색의 공통 목록 (Task 107, 개인화 없음): 서비스별 인기 순위 수집 Job과 공개 조회 API. 찜·작품 데이터는 wishlist·catalog 서비스를 호출해 읽는다
│   ├── controller/        HomeController (GET /api/public/popularity-rankings·wishlisted-titles·newly-available-titles·genres·genres/{genreId}/titles·people/titles, 공개·요청 제한), AdminPopularityController (POST /api/admin/popularity/executions, 202·409, ADMIN JWT 또는 배치 토큰)
│   ├── dto/response/      PopularityRankingResponse·PopularityRankingItemResponse, WishlistedTitleListResponse·WishlistedTitleResponse, NewlyAvailableTitleListResponse·NewlyAvailableTitleResponse, GenreResponse, TitleExplorePageResponse, PopularityExecutionAcceptedResponse
│   ├── exception/         PopularityAlreadyRunningException (409 BATCH_ALREADY_RUNNING)
│   ├── job/               PopularityJobConfig(Job·Step 2개: 수집 → 오래된 스냅샷 삭제), PopularityLaunchConfig(인기 수집 전용 비동기 operator), PopularityLauncher(인기 수집·정리·재계산·수집 실행 중이면 409), PopularityExecutionQuery(BATCH_* 조회: 인기 수집 실행 중 여부. admin·plan/batch·retention도 쓴다), PopularityJobParameters, PopularityProperties·PopularityConfig(app.popularity.*, app.home.* 등록)
│   ├── service/           PopularityCollectService(서비스×매체별 TMDB discover → 스냅샷 저장, 트랜잭션 없음), PopularitySnapshotService(날짜·서비스·매체 단위 교체·삭제), PopularityItemMapper(TMDB 응답 → 값 묶음), PopularityRankingService(TODAY·WEEK 순위 조회), HomeQueryService(찜 많은 작품·새로 볼 수 있게 된 작품·탐색을 각 도메인 서비스에서 받아 돌려줌), HomeProperties(app.home.*: 최소 찜 인원·입고 일수·공개 일수) + model/ (PopularityItem, PopularityCollectSummary, PopularityRankingView, WishlistedTitleListView)
│   ├── step/              PopularityCollectTasklet, PopularityPurgeTasklet
│   └── domain/            entity(PopularitySnapshot) · enums(PopularityPeriod) · repository(PopularitySnapshotRepository) · rule(PopularityRankingRules: 날짜별 인기도 → 평균 순위. 없던 날 0 · DailyPopularity, RankedPopularity)
├── llmdraft/            영어 줄거리 한국어 번역 관리자 기능(관리자가 수동으로 시작, 자동 번역 없음, 데모는 조회만): 번역 초안 생성·일괄·검수·반영·되돌리기. 초안은 DB 표 없이 Redis에 7일 보관한다. 작품 줄거리는 catalog 서비스(TitleOverviewService)로 읽고 바꾼다
│   ├── config/            LlmDraftProperties(app.llm.limits·draft·bulk·guard), LlmDraftConfig(설정 등록, 자동 검사 규칙 DraftTextGuard 빈)
│   ├── controller/        AdminLlmController (/api/admin/llm/**: 후보 목록·초안 생성·일괄 시작·초안 목록·반영·삭제·영어로 되돌리기 7개. GET은 ADMIN·DEMO, 쓰기는 ADMIN만)
│   ├── dto/
│   │   ├── request/         CreateDraftRequest, CreateBulkOverviewDraftsRequest(titleIds 또는 count), ApplyDraftsRequest(draftIds 또는 allPassed)
│   │   └── response/        OverviewCandidatePageResponse·OverviewCandidateResponse·LlmSummaryResponse, OverviewDraftResponse·OverviewDraftPageResponse, BulkDraftRunResponse·OverviewLatestBulkRunResponse(목록 안의 일괄 상태, nullable 표시가 섞이지 않게 따로 둠), ApplyOverviewDraftsResponse·ApplyOverviewDraftItemResponse, OverviewRevertResponse
│   ├── exception/         LlmDraftException (LLM_* 오류 코드를 생성자로 받음)
│   ├── repository/        Redis 저장소(DB 아님): DraftRepository(초안 llm:draft:*·작품 연결·목록 색인), LlmQuotaStore(분당·하루 호출 카운터 Lua, 태평양 날짜 기준, 작품 쿨다운), QuotaDecision, BulkRunStatusRepository(최근 일괄 진행 상황)
│   ├── service/           DraftService(초안 한 건: 작품 확인 → 키·쿨다운·초안 총량·상한 확인 → Gemini 호출 → 자동 검사. `translate`는 저장 없이 초안 기록만 돌려주고 `createDraft`(단건, 재시도 없음)가 그 뒤 저장한다, 트랜잭션 없음), DraftBulkService(일괄: 전용 스레드 하나, 호출 간 대기, 중단 사유 판정, 실패·주의 자동 재시도 후 최선 결과 한 번 저장), DraftQueryService(초안 목록·표본), DraftEditService(관리자가 번역문을 고침: 자동 검사 재판정, isEdited 표시, TTL 갱신. 고친 초안은 일괄 번역이 덮어쓰지 않음), DraftApplyService(반영·삭제·단건 및 대량 되돌리기(관리자 번역만 대상, 500건씩 묶음, isPreview이면 대상 수만 셈), 수집 등 실행 중이면 거절), OverviewCandidateService(후보 목록 + 요약), ConflictingJobChecker(수집·재계산·정리·인기 수집 실행 중 확인) + model/ (DraftRecord, DraftGrade(성공·주의·실패 등급), BulkRunStatus·BulkRunState·BulkStopReason, QuotaUsage, ApplyItemResult·ApplyOutcome·ApplySkipReason, DraftPageView, CandidatePageView·CandidateItemView, DraftStatusFilter)
│   └── domain/rule/       DraftTextGuard(번역 초안 자동 검사: 실패 규칙(한글 비율·길이 비율·최대 글자 수·URL·마크다운·머리말·숫자 보존)과 경고 규칙(문장 수·한다체·깨진 띄어쓰기·길이 비율 한계 근접), 순수 Java), GuardLimits(기준 수치), GuardResult, GuardFailure(걸린 이유), GuardWarning(의심 신호, 실패 없이 경고만 있으면 "주의")
└── engine/              플랜 계산 엔진 (순수 Java, 프레임워크 없음)
    ├── model/             입력·출력 값 틀
    ├── rule/              평가·비교 규칙
    └── solver/            완전탐색·그리디 풀이
```

호출 방향은 `controller → service → domain` 한쪽이다(규칙은 `.claude/rules/backend.md`).

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
