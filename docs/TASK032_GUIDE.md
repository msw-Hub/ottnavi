# Task 032 작업 안내서 (ERD 보정)

> 2026-10-12 작성. **구현 전 안내서다.** 이 Task는 코드도 마이그레이션 파일도 만들지 않는다. 산출물은 `docs/ERD.md`(v0.3 → v0.4)와 ROADMAP `기록:`이다. ROADMAP은 고치는 사람을 사용자로 정했지만, **2026-10-13에 사용자가 "ERD.md 수정은 Claude가 하고 사용자는 10절만 보며 진행한다"고 지시해 Claude가 8절대로 `ERD.md`를 이미 고쳤다.** 사용자는 결과를 검토하고 확정한다(10절). 이 안내서가 하는 일은 확정된 계약(Task 031)과 현재 ERD를 대조해 **무엇을 어떻게 바꿀지, 왜, 무엇이 영향받는지, 무엇을 아직 모르는지**를 정리하는 것이다.
> 기준 문서: `docs/ROADMAP.md` Task 031·032·033·034·035와 4.2(Flyway 번호 배정), `docs/ERD.md` v0.3 전체, `docs/api/openapi.yaml`(확정 계약, operation 31개), `docs/api/error-codes.md`, `docs/TECH.md` 1~3절·6절·T-1·T-9, `docs/PRD.md` FR-08~FR-11·FR-14·FR-19·FR-20·11절 34~41번, `.claude/rules/backend.md`, `backend/src/main/resources/db/migration/V1__create_ott_service.sql`, `backend/src/main/java/com/ottnavi/provider/domain/OttService.java`, `global/common/BaseTimeEntity.java`, `engine/model/Assignment.java`.
> 작성 형식은 `docs/TASK041_GUIDE.md`를 따랐다(한눈에 보기 → 왜 → 사전 조사 → 단계별 → 함정 → 불일치 → 확인 못 한 것).
>
> 근거 수준 표시 (이 문서 전체에서 같은 뜻으로 쓴다).
>
> | 표시 | 뜻 |
> |---|---|
> | `계약` | `docs/api/openapi.yaml` 본문을 직접 읽고 대조함 |
> | `문서` | 이 저장소의 PRD·ERD·TECH·ROADMAP에 적혀 있음 |
> | `소스 확인` | 이 저장소가 쓰는 버전의 라이브러리 소스 jar(로컬 Gradle 캐시)를 읽어 확인함. **실행해 보지는 않았다** |
> | `실행 확인` | 임시 PostgreSQL 17.6 컨테이너(저장소의 Testcontainers·compose와 같은 이미지)에서 이 문서의 SQL을 **실제로 실행**해 결과를 봄(부록 A). 개발용 compose DB는 건드리지 않았다 |
> | `c7` | context7으로 공식 문서를 확인함(Flyway, Spring Boot 4.1.0) |
> | 확인 못 함 | 위 방법으로 확인하지 못함. 추측으로 채우지 않았고 14절에 모았다 |

## 1. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 목적 | 확정된 API 계약과 `ERD.md`가 어긋나는 곳을 찾아 ERD를 고친다. 이 결과가 Task 033(V2~V4)·034(V5~V7) 마이그레이션의 입력이 된다 |
| 태그·선행 | [H] · 선행 030(완료), 031(계약 확정은 끝났고 PR 2의 프론트 생성물 교체가 남음, 이 Task는 계약 파일만 있으면 진행 가능) |
| 브랜치 | 안내서와 ERD 수정을 한 PR로 올리려고 문서 워크트리의 `feature/docs-task032-guide` 브랜치에서 함께 작업했다(문서만 고치는 작업도 `feature/`, git.md) |
| 이 Task가 하는 것 | ① 계약 31개 operation의 요청·응답 필드를 ERD 컬럼과 하나씩 대조(5.1) ② 12개 변경 항목 판정(4절) ③ 사용자 결정 9건(D1~D9, 모두 추천대로 확정, 6절) ④ `ERD.md` 갱신(8절, **적용 완료**) ⑤ ROADMAP `기록:`(15절, 병합 뒤) |
| 이 Task가 하지 않는 것 | Flyway 파일 작성(033·034), 엔티티(035 이후), 계약 수정(계약은 확정됐다), 프론트 수정. **9절의 SQL은 스케치이고 `backend/`에 넣지 않는다** |
| 결과 요약 | 12항목 중 **열·인덱스·상태 값이 바뀌는 것 6건**(1·2·3·5·8·10), **CHECK 제약만 더해지는 것 2건**(7·12), **바뀌지 않는 것 4건**(4·6·9·11, "변경 없음"을 이유와 함께 ERD에 남긴다). 새 테이블은 없다(서비스 테이블 24개 그대로). 이미 병합된 V1(`ott_service`)을 고칠 일은 없다 |
| 계약 쪽에서 더 고칠 것 | 없다. 다만 계약 YAML의 일부 description에 "ERD에 …를 추가해야 한다(Task 032)"라는 문장이 있다(13절 U9). 반영 뒤 낡은 문장이 되므로 Task 033 PR에서 정리할지 정하라 |
| 프론트에서 해야 할 변경 | 없다(ERD는 내부 구조다). 참고로 시각 값이 서버에서 `Z`(UTC)로 나올 수 있는 점은 4.2와 5.3에 적었다 |

| 완료 기준 (ROADMAP Task 032, V-H) | 확인 방법 |
|---|---|
| 반영 항목과 ERD 버전(또는 "변경 없음")을 기록했다 | 15절 `기록:` 틀을 채워 ROADMAP Task 032 아래에 붙인다 |
| R11-18·R11-19가 ERD에 주는 영향을 정리했다 | 4.6 끝: 둘 다 **변경 없음** |

## 2. 쉬운 용어 풀이

| 용어 | 뜻 |
|---|---|
| 마이그레이션 | DB 구조를 바꾸는 SQL 파일. Flyway가 `V2__이름.sql`처럼 번호 순서대로 한 번씩만 실행하고 실행 기록과 파일 지문(체크섬)을 남긴다. **이미 실행된(병합된) 파일을 고치면 지문이 달라져 기동이 실패한다**(`c7`: Flyway 검증은 실행 당시 지문과 파일 지문이 다르면 `CHECKSUM_MISMATCH`로 막는다) |
| 파생(계산) 값 | 저장하지 않고 조회할 때 다른 값으로 계산하는 값. 예: 요금의 종료일은 "다음 줄 시작일의 전날"이라 저장하지 않는다 |
| CHECK 제약 | "이 열 값은 이 규칙을 지켜야 한다"를 DB가 대신 검사하는 장치. 코드가 실수해도 DB가 막아 준다 |
| TIMESTAMPTZ | 시각을 "시간대 정보와 함께" 저장하는 PostgreSQL 타입. 내부적으로는 UTC 기준 순간 하나를 저장하고, 보여 줄 때 시간대를 붙인다. `DATE`는 "달력의 하루"(시각 없음) |
| 스냅샷 | 계산 당시 값을 복사해 둔 것. 나중에 원본이 바뀌어도 과거 결과는 그대로 |
| 부분 유니크 인덱스 | 조건에 맞는 행끼리만 중복을 막는 인덱스. 예: ACTIVE 플랜은 사용자당 1개 |
| 잠금(락) | 같은 행을 두 요청이 동시에 고치지 못하게 한쪽이 기다리게 하는 것. 찜 20개 상한이 동시 요청으로 뚫리지 않게 쓴다 |

## 3. 사전 조사 결과

### 3.1 대조 방법

계약의 모든 스키마 필드를 ERD 컬럼과 하나씩 맞춰 봤다(5.1절 표). 필드가 (a) 컬럼과 1:1이면 변경 없음, (b) 컬럼은 있으나 타입·이름·규칙이 다르면 보정 대상, (c) 컬럼이 없으면 "파생(계산)할 수 있는가, 아니면 컬럼을 추가해야 하는가"를 판정했다. ROADMAP이 정리해 둔 12항목(Task 031 진행 기록)은 이 방식으로 모두 재확인했고 12항목 밖에서 추가로 나온 것은 5.4에 따로 모았다.

### 3.2 Spring Batch 6.0.5 소스 확인 (`소스 확인`, 항목 11의 근거)

로컬 Gradle 캐시의 `spring-batch-core-6.0.5` jar와 sources jar를 읽었다(저장소는 Boot 4.1.1 BOM으로 이 버전을 쓴다).

| 사실 | 근거 |
|---|---|
| `JobOperator.recover(JobExecution)`는 6.0에 새로 생겼다. javadoc: "급작스런 종료로 `STARTED`에 멈춘 실행을 `FAILED`로 바꿔 재시작 가능하게 한다. 실행 컨텍스트에 `recovered=true` 표시를 남긴다" | `launch/JobOperator.java` |
| 구현(`SimpleJobOperator.recover`)은 실행 중 상태인 Step을 `FAILED`로 바꾸고 **`setEndTime(LocalDateTime.now())`로 종료 시각을 채운다**. Job도 `FAILED` + **`setEndTime(LocalDateTime.now())`** + 컨텍스트에 `batch.recovered=true`를 넣고 저장한다 | `launch/support/SimpleJobOperator.java` 417~455줄 |
| `recover`는 상태가 `COMPLETED`·`ABANDONED`·`UNKNOWN`이면 경고만 남기고 그대로 돌려준다. 그 밖(`STARTED`뿐 아니라 `FAILED`, 아직 돌고 있는 `STARTED` 포함)은 **전부 FAILED로 만든다**. 즉 "정말 멈춘 실행인지"를 `recover` 자신은 확인하지 않는다 | 같은 파일 |
| `JobExecution`의 시각은 `LocalDateTime`(시간대 없음) | `job/JobExecution.java` |
| 메타데이터 테이블(`schema-postgresql.sql`)의 시각 열은 `TIMESTAMP`(시간대 없음), `BATCH_JOB_EXECUTION.END_TIME`은 NULL 허용 | `spring-batch-core-6.0.5.jar`의 `org/springframework/batch/core/schema-postgresql.sql` |

### 3.3 임시 DB 실행 확인 (`실행 확인`)

저장소의 실제 `V1__create_ott_service.sql` 위에 9절의 SQL을 올려 실행했다. 결과(부록 A의 스크립트와 같다)는 다음과 같다. 전부 기대대로였고, 실패해야 하는 INSERT는 모두 의도한 제약 이름으로 거부됐다.

| 확인 | 결과 |
|---|---|
| V1 + 뼈대 부모 테이블 + 9절 SQL이 오류 없이 실행된다 | 통과 |
| `tmdb_provider` 시드 8행이 코드로 서비스 ID를 찾아 들어간다(Netflix 광고형 1796은 `AD`) | 통과 |
| MAPPED인데 서비스 없음 / IGNORED인데 서비스 있음 / 없는 상태 값 | 각각 `ck_tmdb_provider_mapped_service` ×2, `ck_tmdb_provider_mapping_state`로 거부 |
| `user_subscription`: NOT_SUBSCRIBED 저장, SUBSCRIBED인데 요금제 이름 없음, FREE인데 결제일 있음, 서비스 중복 | 모두 거부(상태 값·교차 규칙·유니크) |
| `product_price`: 같은 시작일, 음수 금액 | 거부. 결제일 기준 가격 조회(2026-10-01 → 12000, 2026-10-15 → 13500), 종료일 계산(LEAD, 마지막 줄은 NULL) 정상 |
| `plan`: 같은 유형 DRAFT 2개, ACTIVE 2개, DRAFT에 `activated_at`, ACTIVE에 `activated_at` 없음 | 모두 거부. ACTIVE를 ARCHIVED로 바꾼 뒤 DRAFT를 ACTIVE로 올리면 통과(`activated_at`을 같은 UPDATE에서 채워야 함) |
| `plan_assignment`: 0분, 같은 달 같은 항목 중복 | 거부. 조각 번호 계산(`ROW_NUMBER`·`COUNT` 윈도우)이 기대대로 나옴 |
| 찜 개수(`watched_at IS NULL`만 셈) | 3행 중 2 |
| `title.media_type` 소문자 `movie` | 거부 |
| 실행별 변경 조회 쿼리 | 제목순 정렬 정상, `job_execution_id` 인덱스를 탄다(`Bitmap Index Scan`) |
| `users` 삭제 | `user_subscription`·`plan`·`plan_month`·`wishlist_item` 모두 0행(CASCADE) |

### 3.4 Flyway 번호 배정 (`문서`, ROADMAP 4.2)

V1 `ott_service`(병합됨) · V2 작품 A · V3 상품 B · V4 `BATCH_*` · V5 사용자 C · V6 플랜 D · V7 알림 E·`shedlock`. **V2 이후는 아직 병합 전이므로 이번 보정은 새 번호를 만들지 않고 각 파일 본문에 처음부터 넣는다**(ROADMAP Task 033: "Task 032에서 A·B 영역 변경이 나오면 아직 병합 전인 V2·V3에 넣고"). V1을 바꿀 일은 12항목 어디에도 없다.

## 4. 12항목 대조

### 4.0 요약표

| # | 항목 | 계약 근거 | ERD 변경 | 들어갈 Flyway | 사용자 결정 |
|---|---|---|---|---|---|
| 1 | `product_price.effective_to` 제거 | `AdminProductPrice`에 종료일 없음, `listAdminProducts` 설명 | **컬럼 삭제** | V3 | 없음 |
| 2 | `product_price.last_verified_at` | `AdminProductPrice.lastVerifiedAt`·`ServicePrice.lastVerifiedAt` (date-time) | **컬럼 추가** `TIMESTAMPTZ NOT NULL` | V3 | 없음 |
| 3 | `tmdb_provider` 매핑 상태 | `ProviderMappingState`, `AdminProvider.mappingState` | **컬럼 추가** `mapping_state` + CHECK + 시드 | V2 | 없음 |
| 4 | `active` ↔ `isActive` | `AdminProduct.isActive` | 변경 없음(이름 대응만) | V3 | 없음 |
| 5 | `UNKNOWN` 상태, `plan_name` | `ServiceSubscriptionStatus`, `UserSubscription.planName` | **상태 값 추가 + 컬럼 추가** | V5 | D4, D5 |
| 6 | `isOnboardingDone` | `UserSettings.isOnboardingDone` | 변경 없음(행 존재로 계산) | V5 | 없음 |
| 7 | `watchMin` ↔ `minutes` | `PlanAssignment.watchMin`·`segment*` | 변경 없음(조각은 계산). `CHECK (minutes > 0)` 추가 권장 | V6 | D3 |
| 8 | `activated_at`, `data_as_of` 타입 | `SavedPlan.activatedAt`, `PlanDraft.dataAsOf` | **컬럼 추가** `activated_at`, **타입 명시** `data_as_of TIMESTAMPTZ` | V6 | D1, D2 |
| 9 | 찜 개수 집계 | `WishlistItemList.activeItemCount`, `WISHLIST_LIMIT_EXCEEDED` | 변경 없음(집계 규칙을 ERD에 명시) | V5 | 없음 |
| 10 | 변경 작품 조회 인덱스 | `listCollectExecutionChanges`, `CollectExecution.changedCount` | **인덱스 추가** `availability_change(job_execution_id)`, 열 NULL 허용 | V2 | D8 |
| 11 | 수집 `STOPPED` | `CollectExecutionStatus.STOPPED` | 변경 없음(저장하지 않는 파생 상태) | — | 없음 |
| 12 | `media_type` 변환 | `MediaType`(소문자) | 변경 없음(+ 값 CHECK). 변환은 코드 | V2 | D7 |

R11-18(환산 입력 저장 안 함)·R11-19(드라마 찜은 시즌 단위)는 ERD에 영향이 **없다**(4.6 끝).

### 4.1 `product_price.effective_to` 제거

| 칸 | 내용 |
|---|---|
| 현재 ERD | 연결도(225행)에 `date effective_to`, 표(236행)에 `product_price │ id, product_id FK, monthly_price, effective_from, effective_to │ UNIQUE(product_id, effective_from). 적용 시작일 기준으로 가격을 고르고, 기간이 겹치지 않게 서비스에서 검증 (FR-19)` |
| 바뀔 내용 | `effective_to` 삭제. 설명을 "종료일은 저장하지 않는다. 다음 줄 시작일의 전날까지 적용되고 마지막 줄은 계속 적용된다"로 바꾼다. "겹치지 않게 서비스에서 검증"은 "줄은 맨 뒤에 시작일이 더 늦은 줄로만 추가한다(`PRICE_START_DATE_NOT_AFTER_LAST`·`PRICE_START_DATE_DUPLICATE`)"로 바뀐다 |
| 이유 | `계약`: `AdminProductPrice`에는 종료일 필드가 없고 `listAdminProducts` 설명이 "적용 종료일은 저장하지 않는다. 화면이 '다음 줄 시작일의 전날'로 계산"이라고 쓴다. `문서`: ROADMAP Task 029 기록(2026-10-09 결정 "종료일을 저장하지 않고 다음 행 시작일 전날로 파생") |
| 영향받는 쿼리·코드 | ① **가격 조회**(PRD 11절 40번: 결제일에 적용 중인 줄 = 시작일이 결제일 이하인 것 중 가장 늦은 행). 아래 SQL이 기준이다(`실행 확인`). ② 종료일이 필요한 서버 쿼리는 없다(계약 응답에 필드가 없으므로 LEAD 계산도 서버가 하지 않는다). ③ `effective_to`가 없으면 기간이 **겹치거나 비는 일이 구조상 없다**. 그래서 FR-19 "가격 기간은 겹치지 않는다"는 별도 검증 없이 지켜지고, 서비스가 검증할 것은 "시작일이 마지막 줄보다 늦다" 하나다 ④ 수정·삭제를 마지막 줄만 허용(`PRICE_NOT_LATEST`)하는 규칙이 이 구조를 지킨다. 중간 줄을 고칠 수 있으면 시작일 순서가 뒤집힐 수 있다 |
| 확인 못 한 것 | 알림(2단계)이 "요금 인상 예정일"을 보여 줄 필요가 있는지는 계약·ROADMAP에서 찾지 못했다. 필요해지면 그때 LEAD로 계산하면 되고 컬럼을 되살릴 이유는 없다 |

```sql
-- 가격 조회: 기준 날짜(오늘 또는 사용자 결제일, 한국 날짜)에 적용 중인 줄 하나
-- UNIQUE (product_id, effective_from) 인덱스를 뒤에서부터 한 칸만 읽는다
SELECT monthly_price, effective_from, last_verified_at
FROM product_price
WHERE product_id = :productId AND effective_from <= :baseDate
ORDER BY effective_from DESC
LIMIT 1;
```

**`:baseDate`는 DB의 `CURRENT_DATE`로 만들지 않는다.** DB 서버의 시간대(Supabase는 보통 UTC)에 따라 한국 새벽 9시 이전에 "어제"가 된다. 자바의 `Clock`(한국 시간대)에서 `LocalDate`를 만들어 파라미터로 넘긴다(12절 함정 7).

### 4.2 `product_price.last_verified_at` 추가

| 칸 | 내용 |
|---|---|
| 현재 ERD | 없음 |
| 바뀔 내용 | `last_verified_at TIMESTAMPTZ NOT NULL`을 추가한다. **DEFAULT를 두지 않는다**(도메인 메서드가 항상 채운다. JPA 삽입은 값이 null이면 NULL을 그대로 보내 DEFAULT가 쓰이지 않으므로 DEFAULT는 실수를 가릴 뿐이다) |
| 이유 | `계약`: `AdminProductPrice.lastVerifiedAt`, `ServicePrice.lastVerifiedAt` 둘 다 `date-time`. 설명: "서버가 확인·수정·추가·상품 등록 시각을 저장한다. 화면은 한국 시간 기준 날짜만 표기, 90일 판정도 한국 날짜 기준". `문서`: PRD 11절 41번, ROADMAP Task 031 진행 기록("`lastVerifiedAt`은 일시로 저장·전송") |
| 영향받는 쿼리·코드 | 값을 바꾸는 순간: 상품 등록(첫 줄), 줄 추가, 줄 수정, [요금 확인함]. `ServicePrice`는 4.1의 가격 조회 쿼리 결과에서 그대로 꺼낸다. 엔티티 필드는 `Instant` + `@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)`(`BaseTimeEntity`와 같은 방식, TECH 6절). **`updated_at`과 합치지 않는다**: `updated_at`은 `@UpdateTimestamp`가 어떤 변경에도 자동으로 바꾸지만, 확인 시각은 "관리자가 요금을 확인했다"는 업무 사건이다 |
| 90일 판정 | 서버는 계산하지 않는다. 프론트가 한국 날짜 기준으로 판정한다(PRD 41번, 계약 설명). 서버는 값만 준다 |
| 확인 못 한 것 | ① "금액을 고치면 확인일도 오늘로"(계약)는 확정이지만 **시작일만 고친 경우**도 확인일을 갱신하는지는 계약에 없다(Task 061에서 정한다). ② `Instant`를 JSON으로 내보내면 `Z`(UTC) 표기가 나오고 계약 예시는 `+09:00`이다. 둘 다 유효한 `date-time`이지만 서버 직렬화 결과는 확인하지 못했다. 프론트가 한국 날짜로 바꾸는 코드가 `Z`도 받는지는 프론트 담당이 확인할 일이다 |

### 4.3 `tmdb_provider` 매핑 상태

| 칸 | 내용 |
|---|---|
| 현재 ERD | 표(120~129행): `ott_service_id` "우리 판정. **관심 없는 제공처는 NULL**", `tier` "STANDARD / AD. 우리 판정". 즉 "아직 안 본 제공처"와 "관련 없다고 정리한 제공처"가 둘 다 `ott_service_id = NULL`이라 구분되지 않는다 |
| 바뀔 내용 | `mapping_state VARCHAR(20) NOT NULL DEFAULT 'UNMAPPED'`(UNMAPPED / MAPPED / IGNORED)를 추가한다. 규칙: MAPPED ⇔ `ott_service_id`가 있음(CHECK), UNMAPPED·IGNORED는 서비스가 없고 `tier`는 STANDARD. `ott_service_id` 설명을 "MAPPED일 때만 값이 있다"로 바꾼다. 시드 8행은 모두 MAPPED(Task 004 매핑표, Netflix 광고형 1796은 `AD`) |
| 이유 | `계약`: `ProviderMappingState`, `AdminProvider.mappingState`(필수), `UpdateAdminProviderRequest`("MAPPED면 ottServiceId 필수, UNMAPPED·IGNORED면 null, 등급은 STANDARD로 저장"). `문서`: TASK041 8절 1번(7곳 외 제공처를 버리지 않고 "미매핑"으로 저장, 관리자가 "관련 없음"으로 정리) |
| 영향받는 쿼리·코드 | ① **판정**(Task 044): 제공처 → 서비스 변환은 `mapping_state = 'MAPPED'`인 행만 쓴다 ② **수집**(Task 043): 새 제공처는 없을 때만 `INSERT`(UNMAPPED, `first_seen_at` = 지금). **이미 있는 행의 상태·서비스·tier를 덮어쓰지 않는다**(예: `INSERT ... ON CONFLICT (provider_id) DO NOTHING` 또는 존재 확인 후 삽입). 덮어쓰면 관리자가 IGNORED로 정리한 제공처가 매일 UNMAPPED로 돌아온다 ③ **관리자 API**(Task 046): PATCH가 세 값을 한꺼번에 저장하고, 서비스 규칙(MAPPED 필수·나머지 null·tier 정규화)은 서비스 계층이 검사하며 DB CHECK가 안전망이다 ④ 정렬("미매핑 먼저")은 화면 몫이다(`listAdminProviders` 설명) |
| 확인 못 한 것 | 쿠팡플레이처럼 TMDB에서 영화 목록에만 빠진 제공처(Task 004 기록)를 수집이 어떻게 다루는지는 Task 043·044의 몫이다 |

### 4.4 `subscription_product.active` ↔ API `isActive`

| 칸 | 내용 |
|---|---|
| 현재 ERD | `subscription_product │ id, name, product_type, active` ("판매 종료 상품은 active=false (삭제하지 않음)") |
| 바뀔 내용 | **스키마 변경 없음.** 컬럼은 `active`로 둔다(PRD FR-19도 `active = false`라고 쓴다). 설명 끝에 "API에서는 `isActive`"를 덧붙인다 |
| 이유 | `계약`: `AdminProduct.isActive`, `UpdateAdminProductRequest.isActive`. 컬럼 이름을 계약에 맞춰 바꾸면 PRD·ERD 설명 전체를 바꿔야 하고 얻는 것이 없다. `backend.md`: "boolean은 `isXxx`로 짓되, DB 컬럼명이 정해진 경우는 컬럼명을 따른다" |
| 영향받는 쿼리·코드 | 엔티티 필드는 `boolean active`(Lombok `@Getter`가 `isActive()`를 만든다). **응답 record의 필드만 `isActive`**로 두고 `from()`에서 옮긴다. 요청의 `isActive`는 PATCH라서 "안 보냄"과 "false"를 구분해야 하므로 `Boolean`(래퍼)이다. Spring Data 파생 쿼리는 엔티티 속성 이름(`active`) 기준이다(`findByActiveTrue`). **회원 요금 API는 `active = true`인 상품만 다룬다**(계약 `listMyServicePrices`·`setMyServicePrice`·`resetMyServicePrice`): 목록은 판매 중 상품만 주고, 판매 종료 상품에 대한 `PUT`·`DELETE`는 404 `SERVICE_PRICE_NOT_FOUND`다. 그래서 상품을 판매 종료하면 사용자가 저장해 둔 금액은 남지만 조회·수정 대상에서 빠진다 같은 방식으로 이름이 다른 불리언 전체가 5.1절 표에 있다 |
| 확인 못 한 것 | **Jackson 3이 record의 `isActive` 컴포넌트를 JSON 키 `isActive`로 내보내는지**(`isXxx()` 접근자를 "getter"로 보고 키를 `active`로 바꾸는 동작이 있는지)는 확인하지 못했다. springdoc이 만드는 스키마와 `OpenApiContractTest`(계약 대조)가 키 이름 불일치는 잡아 주지만, 실제 JSON 키는 응답 본문을 직접 보는 단위 테스트 한 건으로 확인하라(Task 061) |

### 4.5 `user_subscription`: `UNKNOWN` 상태와 `plan_name`

| 칸 | 내용 |
|---|---|
| 현재 ERD | 연결도: `status "SUBSCRIBED / FREE"`, 열은 `id, user_id, ott_service_id, status, product_id FK, billing_day(smallint)`. 표(293행): "미구독은 행을 두지 않는다. billing_day 기본값 1" |
| 바뀔 내용 | ① `status` 허용 값을 **SUBSCRIBED / FREE / UNKNOWN**으로. `NOT_SUBSCRIBED`는 저장하지 않는다(행이 없다) ② `plan_name VARCHAR(30)` 추가(표시용, 계산에 안 쓰임) ③ 구독 중이 아니면 `plan_name`·`billing_day`는 NULL(CHECK) ④ `billing_day`의 "기본값 1"은 **DB 기본값이 아니라 저장 요청이 비었을 때 서비스가 1을 채운다**는 뜻으로 문구를 고친다(D5) |
| 이유 | `계약`: `ServiceSubscriptionStatus`(SUBSCRIBED·FREE·NOT_SUBSCRIBED·UNKNOWN, "UNKNOWN도 저장한다… ERD `user_subscription` 상태 값에 `UNKNOWN`을 추가해야 한다"), `UserSubscription.planName`("maxLength 30, SUBSCRIBED일 때만 필수, 그 밖에는 null… ERD에 `plan_name`(30자) 칸을 추가해야 한다"), `billingDay`("SUBSCRIBED일 때만 값이 있고 그 밖에는 null"). 만약 `billing_day`에 DB 기본값 1을 두면 FREE·UNKNOWN 행도 1이 되어 "그 밖에는 null"이라는 계약과 어긋난다 |
| 영향받는 쿼리·코드 | ① 설정 저장(PUT)은 **전체 교체**다: 그 사용자의 행을 지우고(또는 비교해 갱신하고) NOT_SUBSCRIBED가 아닌 서비스만 넣는다. 보내지 않은 서비스는 미구독(계약) ② 조회(GET)는 서비스 7개를 모두 채운다: 행이 없는 서비스는 서버가 `NOT_SUBSCRIBED`로 채운다(계약) ③ 열거형은 상태 4개짜리 하나를 쓰되 `NOT_SUBSCRIBED`는 저장하지 않는다(CHECK가 막는다). 둘로 나누는 것보다 단순하다 ④ **계산 입력**(Task 063)은 UNKNOWN을 NOT_SUBSCRIBED처럼 다룬다(계약: "볼 수 있는 서비스로 세지 않음") ⑤ `billing_day` 31은 30일인 달이 있다. 그 달의 결제일 기준 가격 조회(PRD 40번)를 어떻게 할지는 문서에 없다(12절 함정 8) ⑥ `product_id`는 D4 |
| 확인 못 한 것 | PRD FR-08과 "사용 흐름"은 아직 3상태(미구독/구독 중/무료 이용)다. UNKNOWN 반영은 13절 U1 |

### 4.6 `user_setting`과 `isOnboardingDone`

| 칸 | 내용 |
|---|---|
| 현재 ERD | `user_setting │ user_id PK·FK, monthly_budget, monthly_watch_minutes, watch_preset, notification_enabled │ 1:1. 예산·시청 시간 필수(FR-10)` |
| 바뀔 내용 | **스키마 변경 없음.** 설명에 한 줄 추가: "행이 있으면 온보딩(첫 설정 저장)을 마친 사용자다. 행은 `PUT /me/settings`가 처음 만들고 로그인(가입) 때 만들지 않는다". 예산·시청 시간·프리셋 열은 `NOT NULL`로 확정한다 |
| 이유 | `계약`: `UserSettings.isOnboardingDone`("첫 설정 저장을 마쳤는지"), 온보딩 전에는 `subscriptions: []`와 나머지 `null`, 처음 저장하면 `true`. **`user_subscription` 행 수로 판정하면 안 된다**: 모든 서비스를 미구독으로 저장한 사용자는 구독 행이 0개라 "온보딩 전"과 구별되지 않는다. `user_setting`은 예산·시청 시간이 필수라 저장하는 순간 행이 생기므로 존재 여부가 곧 온보딩 여부다 |
| 영향받는 쿼리·코드 | `existsById(userId)` 하나. 계산 선행 조건(PRD FR-10: 예산·시청 시간 없으면 400)도 같은 행의 존재로 판정한다. 플랜 계산은 이 행을 `PESSIMISTIC_WRITE`로 잠그므로(TECH 2절) **행이 없는 사용자는 잠글 대상이 없다**. 그래서 계산 전에 "행 없음 → 400"을 먼저 처리해야 한다(Task 064). `notification_enabled` 기본값은 D6 |
| R11-18·R11-19 | **R11-18 A안**(환산한 분만 저장, 주 N회·회당 M시간은 저장하지 않음): `user_setting`에 열을 추가하지 않는다. **R11-19**(드라마 찜은 시즌 단위): `watch_unit`이 시즌 단위이고 `wishlist_item`이 `watch_unit_id`를 가리키므로 이미 반영돼 있다. 둘 다 ERD 변경 없음 |
| 확인 못 한 것 | `monthly_watch_minutes`의 하한(120)·상한(44640)은 계약의 입력 검증이다. DB CHECK로 못 박을지는 D3에 맡긴다(권장은 `>= 0` 같은 변하지 않는 하한만) |

### 4.7 `plan_assignment`: `watchMin`과 조각 필드

| 칸 | 내용 |
|---|---|
| 현재 ERD | `plan_assignment │ id, plan_item_id FK, plan_month_id FK, ott_service_id FK, minutes │ UNIQUE(plan_item_id, plan_month_id). 긴 시즌은 연속된 달에 걸쳐 여러 행으로 나뉨` (연결도 346~351행에는 `ott_service_id`가 빠져 있다, 13절 U5) |
| 바뀔 내용 | 스키마 변경 없음. 설명에 "API `watchMin` = `minutes`. 조각 번호(`segmentIndex`·`segmentCount`·`isContinuation`)는 저장하지 않고 조회 때 계산한다"를 적는다. `CHECK (minutes > 0)`을 권장한다(D3) |
| 이유 | `계약`: `PlanAssignment`의 `watchMin`·`segmentIndex`(≥1)·`segmentCount`(≥1)·`isContinuation`("segmentIndex > 1"). `소스 확인`: 엔진 `Assignment(monthIndex, productId, minutes)`는 `minutes ≤ 0`을 예외로 막는다(`engine/model/Assignment.java`). 한 항목은 달마다 최대 한 행(`UNIQUE(plan_item_id, plan_month_id)`)이므로 **한 달 = 한 조각**이다 |
| 영향받는 쿼리·코드 | 조각 계산은 `실행 확인`한 윈도우 함수 쿼리를 쓴다(아래). `ott_service_id`는 엔진이 상품(`productId`)만 주므로 `plan` 서비스가 MVP(단품)에서 상품 → 서비스로 바꿔 채운다(`Assignment` 주석: "plan이 ott_service_id로 변환") |
| 확인 못 한 것 | 번들(2단계)에서 한 상품이 서비스 여러 개를 포함할 때 `ott_service_id`가 무엇이 되는지는 2단계 설계다 |

```sql
-- 한 플랜의 배정 + 조각 번호(저장하지 않고 계산)
SELECT a.plan_item_id, m.month_index, a.minutes AS watch_min, a.ott_service_id,
       ROW_NUMBER() OVER (PARTITION BY a.plan_item_id ORDER BY m.month_index) AS segment_index,
       COUNT(*)     OVER (PARTITION BY a.plan_item_id) AS segment_count
FROM plan_assignment a
JOIN plan_month m ON m.id = a.plan_month_id
WHERE m.plan_id = :planId
ORDER BY m.month_index, a.plan_item_id;
-- isContinuation = (segment_index > 1)
```

### 4.8 `plan`: `activated_at`과 `data_as_of` 타입

| 칸 | 내용 |
|---|---|
| 현재 ERD | `plan` 표(363행)에 `data_as_of`가 있고 **타입이 적혀 있지 않다**. `activated_at`은 없다. 연결도의 `start_month`는 `date` |
| 바뀔 내용 | ① `data_as_of`를 **`TIMESTAMPTZ NOT NULL`**로 명시(D2) ② **`activated_at TIMESTAMPTZ`**(NULL 허용) 추가: ACTIVE로 저장된 시각. 초안(DRAFT)은 NULL, ACTIVE·ARCHIVED는 반드시 값이 있다(CHECK) (D1) |
| 이유 | `계약`: `SavedPlan.activatedAt`("저장(활성화) 시각", date-time), `PlanDraft.dataAsOf`(date). **저장 시 409 판정(TECH 2절·PRD 138행): "`data_as_of`가 최신 수집 완료 시각보다 이르면 막는다"**. 수집 완료 시각은 시각(timestamp)이다. `data_as_of`를 날짜로 저장하면 같은 날 두 번째 수집이 끝나도 "같은 날"이라 오래됐음을 알 수 없다. ROADMAP Task 066·067의 409 재현 시나리오(계산 뒤 local 프로필에서 `targetDate`를 바꿔 같은 날 재수집하고 저장)가 바로 이 경우다. `activated_at`: `updated_at`을 대신 쓰면 ARCHIVED로 바뀌거나 `change_summary`를 쓰는 순간 값이 바뀌어 "저장한 시각"이 사라진다 |
| 영향받는 쿼리·코드 | ① 계산(Task 063·064): `data_as_of` = 수집 Job 마지막 `COMPLETED`의 종료 시각. 이 값은 `BATCH_JOB_EXECUTION.END_TIME`(**시간대 없는 `TIMESTAMP`**, 3.2절)에서 오므로 `LocalDateTime → Instant` 변환에 JVM 기본 시간대를 일관되게 써야 한다(5.3, 12절 함정 5) ② 응답 `dataAsOf`(date) = `data_as_of`를 **한국 날짜**로 바꾼 값 ③ 활성화: `status`와 `activated_at`을 **같은 갱신**에서 바꾼다(CHECK는 문장 끝에서 검사하므로 한 번의 UPDATE면 통과, `실행 확인`). 매월 재계산이 DRAFT 없이 만드는 ACTIVE도 `activated_at`을 채운다 ④ 저장 응답 `activatedAt`은 방금 채운 값 |
| 확인 못 한 것 | `change_summary`의 타입(TEXT인지 JSONB인지)이 ERD에 없다. 스케치는 TEXT로 뒀고 Task 034/070에서 확정한다 |

### 4.9 찜 개수 집계

| 칸 | 내용 |
|---|---|
| 현재 ERD | `wishlist_item` 설명(295행)에 이미 "`watched_at`이 없는 행만 센다", "DB 제약(CHECK·트리거)을 두지 않는다", "사용자별 직렬화(사용자 행 잠금 등)는 Task 056에서 정한다"가 있다 |
| 바뀔 내용 | **스키마 변경 없음.** 집계 쿼리를 명시하고, `watched_at`을 `TIMESTAMPTZ`로 적는다(연결도 285행은 `timestamp`라 오해의 소지가 있고, 계약 `watchedAt`은 date-time이다) |
| 이유 | `계약`: `WishlistItemList.activeItemCount`("상한에 세는 현재 찜 개수, '다 봤음' 제외"), `maxActiveItemCount`(20), `WISHLIST_LIMIT_EXCEEDED`(409, 추가와 "다 봤음 되돌리기" 두 곳) |
| 영향받는 쿼리·코드 | 개수: `SELECT count(*) FROM wishlist_item WHERE user_id = :u AND watched_at IS NULL`(`실행 확인`). `UNIQUE(user_id, watch_unit_id)`의 선두가 `user_id`라 인덱스를 추가하지 않는다. **잠금이 필요한 두 동작**은 새 찜 추가와 `isWatched: false`(되돌리기)다. 둘 다 "개수 확인 → 저장"이 한 단위로 묶여야 동시 요청으로 21개가 되지 않는다. 잠글 대상은 **`users` 행**을 권장한다: 온보딩 전(`user_setting` 없음)에도 찜을 담을 수 있기 때문이다. 다른 도메인 소유 테이블이므로 `wishlist` 서비스가 `user` 서비스의 "사용자 잠금" 메서드를 호출한다(`backend.md`: 다른 도메인 리포지토리를 직접 쓰지 않음). 중복 추가는 상한과 무관하게 기존 항목을 돌려주므로(200) "이미 있는지" 확인이 개수 확인보다 **먼저**다 |
| 확인 못 한 것 | 잠금 SQL이 `FOR UPDATE`인지 `FOR NO KEY UPDATE`인지는 TECH 5절과 같이 실제 SQL 로그로 확인해야 한다. `FOR UPDATE`는 다른 트랜잭션의 외래 키 확인(`KEY SHARE`)과도 충돌해 기다리게 만들 수 있다 |

### 4.10 변경 작품 조회와 `availability_change` 인덱스

| 칸 | 내용 |
|---|---|
| 현재 ERD | `availability_change │ watch_unit_id, ott_service_id FK, before_status, after_status, job_execution_id "Spring Batch 실행 ID (FK 없이 참조값만)", changed_at`. 인덱스 언급 없음 |
| 바뀔 내용 | ① 인덱스 `availability_change(job_execution_id)` 추가 ② `job_execution_id`를 **NULL 허용**으로 둔다(D8) ③ ERD "참조 컬럼 인덱스" 절에 이 인덱스를 적는다 |
| 이유 | `계약`: `listCollectExecutionChanges`("이 실행에서 제공 상태가 바뀐 건을 제목 순, 같으면 시즌 번호·서비스 ID 순", 페이지네이션, "첫 전체 수집은 한 실행에 수천~수만 건"), `CollectExecution.changedCount`("`listCollectExecutionChanges`의 `totalElements`와 같다"). 둘 다 `job_execution_id`로 거른다. `availability_change`는 수집이 계속 쌓는 가장 빨리 커지는 이력 테이블이다 |
| 영향받는 쿼리·코드 | 아래 쿼리(`실행 확인`)가 기준이다. ① **정렬 키는 인덱스로 해결되지 않는다**(제목은 `title` 테이블 값). 인덱스가 하는 일은 "이 실행의 변경만 골라내기"이고, 골라낸 수천~수만 건을 메모리에서 정렬한다. 이 규모에서는 문제없다고 판단하지만 실제 소요는 측정하지 못했다 ② **페이지가 안정적이려면 정렬 키가 전순서여야 한다**: 계약의 "제목 → 시즌 번호 → 서비스 ID" 뒤에 `t.id`(동명 작품)와 `c.id`를 붙인다. 계약 정렬을 해치지 않는다 ③ 영화는 `season_number`가 NULL이다. PostgreSQL은 오름차순에서 NULL을 **마지막**에 둔다. 같은 제목의 영화와 드라마가 섞일 때만 영향이 있다 ④ `changedCount`는 같은 `WHERE`의 `count(*)`다 |
| 확인 못 한 것 | **제목 정렬 순서는 DB의 collation(문자 정렬 규칙)에 따른다.** Supabase와 Testcontainers(alpine 이미지)의 collation이 같은지 확인하지 못했다. 한글 가나다 순은 보통 맞지만 영문·한글이 섞일 때 순서가 환경마다 다를 수 있다. 계약 테스트에서 한글 제목만으로 정렬을 검증하면 환경 차이를 피할 수 있다 |

```sql
-- listCollectExecutionChanges (페이지 하나)
SELECT t.media_type, t.tmdb_id, t.title, wu.season_number,
       c.ott_service_id, c.before_status AS previous_status, c.after_status AS current_status
FROM availability_change c
JOIN watch_unit wu ON wu.id = c.watch_unit_id
JOIN title t       ON t.id  = wu.title_id
WHERE c.job_execution_id = :executionId
ORDER BY t.title, t.id, wu.season_number, c.ott_service_id, c.id   -- 앞 세 개가 계약 정렬, 뒤 둘은 페이지 안정용
LIMIT :size OFFSET :page * :size;

-- changedCount, totalElements
SELECT count(*) FROM availability_change WHERE job_execution_id = :executionId;
```

### 4.11 수집 `STOPPED`는 서버가 판정한다

| 칸 | 내용 |
|---|---|
| 현재 ERD | 수집 이력은 `BATCH_*`가 가진다(E 영역 "라이브러리 관리"). `STOPPED`를 담는 열은 없다 |
| 바뀔 내용 | **스키마 변경 없음. `STOPPED`는 저장하지 않는 파생 상태**임을 ERD E 영역에 한 줄로 적는다 |
| 이유 | `계약`: `CollectExecutionStatus` 설명 "STOPPED 중단됨(서버가 멈춰 기록만 실행 중으로 남은 실행을 서버가 판정). Spring Batch `BatchStatus`를 그대로 내보내지 않는다. 이 STOPPED는 Batch의 STOPPED(정상 정지)와 뜻이 다르며, 메타데이터는 STARTED인데 지금 JVM에서 돌고 있지 않은 실행이다" |
| 영향받는 쿼리·코드 (Task 045) | ① 판정: `BATCH_JOB_EXECUTION.STATUS = 'STARTED'`이면서 **지금 이 JVM이 실제로 돌리고 있지 않은** 실행. "돌리고 있는지"는 DB로는 알 수 없고 앱이 자기가 시작한 실행 ID를 메모리에 들고 있어야 한다(단일 인스턴스 전제, TECH 3절). 판정 방식은 Task 045에서 정한다 ② **`JobOperator.recover`는 STARTED가 정말 멈춘 것인지 확인하지 않는다**(3.2절). 살아서 돌고 있는 실행에 호출하면 그대로 `FAILED`가 된다. 그래서 API가 먼저 "STOPPED로 판정된 실행인가"를 확인하고 아니면 `BATCH_NOT_STOPPED`(409)로 막아야 한다 ③ **`recover`가 `END_TIME`을 채운다**(복구하는 순간의 시각, 3.2절 `소스 확인`). 그런데 계약은 `recover` 응답의 `endedAt`을 `null`로 정했다("언제 멈췄는지 알 수 없어"). 즉 **계약대로 하려면 서버가 "복구된 실행"을 따로 알아보고 `endedAt`을 null로 내려야 한다**. 알아보는 방법은 실행 컨텍스트의 `batch.recovered = true` 표시(`recover`가 남긴다)를 읽는 것이다. `BATCH_JOB_EXECUTION`의 `END_TIME`을 지우는 방법은 권하지 않는다(라이브러리 테이블을 직접 고치는 일이다). 실패 사유(`failureReason`)도 `recover`가 `EXIT_MESSAGE`를 채우지 않으므로 앱이 "중단된 실행을 관리자가 실패로 복구함"(계약 예시)을 따로 만들어야 한다 |
| 확정 사항 | **recover 응답의 `endedAt`은 계약(null)이 기준이다.** 구현 때 실행 컨텍스트의 `batch.recovered`를 읽어 복구된 실행이면 `endedAt`을 null로 내린다(2026-10-12 사용자 확정). 계약을 바꾸지 않는다 |
| 확인 못 한 것 | ① 위 ③은 소스를 읽어 얻은 결론이고 **실행해 보지 않았다**. Task 045의 복구 테스트가 실제 `END_TIME`·컨텍스트 값을 확인해야 한다 ② `batch.recovered`를 읽는 정확한 API(`JobRepository`로 컨텍스트를 읽는 방법)는 확인하지 못했다 ③ 마지막 `COMPLETED`의 판정(`data_as_of`용)과 STOPPED 판정이 같은 쿼리를 공유할 수 있는지는 045·063에서 정한다 |

### 4.12 `title.media_type`의 `MOVIE/TV` ↔ API `movie/tv`

| 칸 | 내용 |
|---|---|
| 현재 ERD | `title │ media_type │ VARCHAR(10) │ UK │ MOVIE / TV`, `UNIQUE(media_type, tmdb_id)` |
| 바뀔 내용 | 스키마 변경 없음. **`CHECK (media_type IN ('MOVIE', 'TV'))`를 추가**하고, 설명에 "DB는 대문자, API 경계에서 소문자로 바꾼다"를 적는다 |
| 이유 | `계약`: `MediaType` enum은 `movie`·`tv`(소문자)이고 "DB(`title.media_type`)는 MOVIE/TV 대문자로 저장하고 API 경계에서 바꾼다(백엔드 구현: Jackson 직렬화 값과 경로 변수 변환기 둘 다 소문자로 맞춘다)". 사용처가 많다: 경로 `{mediaType}`, `TitleSearchItem`·`TitleDetail`·`WishlistItem`·`PlanAssignment`·`UnscheduledItem`·`CollectExecutionChange`·`AddWishlistItemRequest` |
| 변환 위치 제안 | **도메인 enum 한 곳에 변환을 둔다**(아래 코드). 응답·요청 DTO마다 변환 코드를 두면 일곱 곳이 어긋날 수 있다. 위치는 `com.ottnavi.catalog.domain.MediaType`을 권장한다(D7). ① **JSON**: enum에 `@JsonValue`(내보낼 때)·`@JsonCreator`(받을 때). 어노테이션 패키지는 Jackson 3에서도 `com.fasterxml.jackson.annotation`이다(TECH 6절) ② **경로·쿼리 값**: Spring은 `String → enum`을 `name()`(대문자)으로만 찾으므로 `movie`는 변환에 실패한다. `catalog/api`에 `WebMvcConfigurer`를 두고 `addFormatters`에서 변환기를 등록한다(`c7`: Boot 문서는 `WebMvcConfigurer#addFormatters`로 MVC 변환기를 등록하라고 안내한다) ③ **DB**: `@Enumerated(EnumType.STRING)`은 `name()`(`MOVIE`)을 저장한다. `@JsonValue`는 JPA에 영향이 없다 ④ 이미 있는 `infra/tmdb/TmdbMediaType`(TMDB 경로용 `movie`/`tv` 문자열을 가진 별도 enum)과 **합치지 않는다**(외부 연동 DTO 격리, `backend.md`) |
| 확인 못 한 것 | ① springdoc이 `@JsonValue` enum을 소문자 값으로 스키마에 쓰는지: `OpenApiContractTest`가 차이를 잡아 줄 것이다(실행해 보지 않음) ② 잘못된 값(`/titles/book/1`)이 400 `VALIDATION_FAILED`로 나오는지는 Task 051에서 확인한다 ③ Spring 7의 `FormatterRegistry.addConverter(Class, Class, Converter)` 시그니처는 소스를 열어 보지 않았다(컴파일할 때 확인) |

```java
package com.ottnavi.catalog.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 작품 구분. DB에는 MOVIE/TV(대문자)로 저장하고 API에는 movie/tv(소문자)로 주고받는다(계약 MediaType) */
public enum MediaType {

	MOVIE("movie"),
	TV("tv");

	private final String apiValue; // 계약의 MediaType 값

	MediaType(String apiValue) {
		this.apiValue = apiValue;
	}

	/** JSON으로 내보낼 때 쓰는 값 */
	@JsonValue
	public String getApiValue() {
		return apiValue;
	}

	/** API 값(movie/tv)을 enum으로 바꾼다. 모르는 값은 IllegalArgumentException */
	@JsonCreator
	public static MediaType fromApiValue(String value) {
		return Arrays.stream(values())
				.filter(type -> type.apiValue.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 작품 구분이다: " + value));
	}
}
```

```java
package com.ottnavi.catalog.api;

import com.ottnavi.catalog.domain.MediaType;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 경로·쿼리의 mediaType(movie/tv)을 enum으로 바꾸는 변환기를 MVC에 등록한다 */
@Configuration
public class MediaTypeWebConfig implements WebMvcConfigurer {

	@Override
	public void addFormatters(FormatterRegistry registry) {
		// 람다를 addConverter(람다) 한 인자로 넘기면 제네릭이 지워져 변환 대상을 못 찾는다. 클래스를 명시하는 형태를 쓴다
		registry.addConverter(String.class, MediaType.class, MediaType::fromApiValue);
	}
}
```

## 5. 계약 필드 전수 대조에서 나온 추가 발견

### 5.1 API 필드 ↔ 컬럼 대응표 (1:1이 아닌 것만)

1:1인 필드(예: `ott_service.name`)는 뺐다. 이 표는 ERD에 "API 필드와 컬럼 대응" 절로 옮겨 두기를 권한다(8절 ⑩). 구현자가 응답 DTO를 만들 때 가장 자주 찾는 정보다.

| API 필드 | 출처 | 비고 |
|---|---|---|
| `AdminProduct.ottServiceId` | `product_component.ott_service_id` (단품은 1행) | 상품 등록은 `subscription_product` + `product_component`(STANDARD) + `product_price`(첫 줄)를 **한 트랜잭션**으로 만든다 |
| `AdminProduct.isActive` | `subscription_product.active` | 4.4 |
| `AdminProductPrice.*` / `ServicePrice.adminPrice·effectiveFrom·lastVerifiedAt` | `product_price`의 기준 날짜 적용 줄 | 4.1 쿼리 |
| `ServicePrice.userPrice` | `user_price_override.monthly_price` | 없으면 null. `UNIQUE(user_id, product_id)` |
| `ServicePrice.appliedPrice·priceSource` | `userPrice`가 있으면 그 값(`USER_OVERRIDE`), 없으면 `adminPrice`(`ADMIN`) | 이 응답에는 두 값만 온다 |
| `ServicePrice.subscriptionStatus` | `user_subscription.status`, 행이 없으면 `NOT_SUBSCRIBED` | |
| `UserSettings.subscriptions` | `user_subscription` + 빠진 서비스를 `NOT_SUBSCRIBED`로 채움 | 7개 항상 |
| `UserSettings.isOnboardingDone` | `user_setting` 행 존재 | 4.6 |
| `WishlistItem.requiredMin·isRuntimeEstimated` | `watch_unit.total_runtime_min`·`runtime_estimated` | |
| `WishlistItem.seasonNumber·seasonName` | `watch_unit.season_number`·`name` | 영화는 null |
| `WishlistItem.isWatchableNow` | 계산: 내 `SUBSCRIBED`·`FREE` 서비스 중 `availability.status = 'AVAILABLE'`가 있는가 | |
| `WishlistItem.availabilities` (7개) | `availability` 행들 | 행이 없는 서비스를 무엇으로 채울지는 계약에 없다(14절) |
| `WishlistItemList.activeItemCount·maxActiveItemCount` | 4.9 집계 / 설정 키 20 | |
| `PlanDraft.scheduledCount·totalCount` | `plan_item`의 `outcome='SCHEDULED'` 수 / 전체 수 | **ERD `plan`에 컬럼이 없다.** 저장하지 않고 센다(아래 5.4 E) |
| `PlanDraft.savedAmount` | `all_subscribe_cost − total_cost` | 저장하지 않음 |
| `PlanDraft.startMonth`, `PlanMonth.month` | `plan.start_month`, `plan_month.month` (둘 다 DATE, 그 달의 1일) | 응답은 `YYYY-MM` 문자열 |
| `PlanMonth.isConfirmed` | `plan_month.confirmed` | |
| `PlanMonthProduct.productName·ottServiceIds` | `subscription_product.name`, `product_component` | |
| `PlanAssignment.priority·mediaType·tmdbId·title·seasonNumber` | `plan_item.priority`와 `plan_item → watch_unit → title` | |
| `PlanAssignment.watchMin·segment*·isContinuation` | 4.7 | |
| `PlanDraft.dataAsOf` / `SavedPlan.activatedAt` | 4.8 | |
| `TitleSearchItem.releaseYear` | `EXTRACT(YEAR FROM title.release_date)` | 모르면 null |
| `TitleSeason.airYear·name·episodeCount` | `watch_unit.air_date`의 연도·`name`·`episode_count` | |
| `TitleDetail.genres` | `title_genre` → `genre.name` | |
| `TitleDetail.cast` | `title_cast`(cast_order 순, 10명) `name`·`character_name` | `character`는 nullable이므로 `character_name`도 NULL 허용 |
| `*.availabilities` (드라마 작품 단위) | 시즌 중 하나라도 AVAILABLE이면 AVAILABLE | 나머지 경우(UNKNOWN·NOT_AVAILABLE 섞임)의 규칙은 계약에 없다 |
| `TitleSearchPage.dataAsOf`, `TitleDetail.dataAsOf`, `WishlistItemList.dataAsOf` | **ERD에 출처가 없다** | 13절 U10, D9 |
| `CollectExecution.executionId·status·startedAt·endedAt·failureReason` | `BATCH_JOB_EXECUTION` (`JOB_EXECUTION_ID`, `STATUS`, `START_TIME`, `END_TIME`, `EXIT_MESSAGE`) + 4.11 | 시각이 시간대 없는 `TIMESTAMP` |
| `CollectExecution.targetDate·tier` | `BATCH_JOB_EXECUTION_PARAMS` (`PARAMETER_NAME`) | 식별 파라미터 |
| `CollectExecution.processedCount` | `BATCH_STEP_EXECUTION`의 어떤 카운트(`READ_COUNT`·`WRITE_COUNT`)를 합칠지 | Task 045에서 정한다. 계약 설명은 "처리한 작품 수"뿐 |
| `CollectExecution.changedCount` | 4.10 `count(*)` | |
| `AdminProvider.*` | `tmdb_provider` | `ottServiceId` null 허용 |

### 5.2 컬럼 타입과 계약 타입 (엔티티 쓸 때 걸리는 것)

| 컬럼 | ERD 타입 | 계약 타입 | 조치 |
|---|---|---|---|
| `title.tmdb_id`, `tmdb_provider.provider_id`, `genre.id`, `title_cast.person_tmdb_id` | `INT` | `int64` (`tmdbId`·`providerId`) | TMDB ID는 현재 32비트에 들어간다. 컬럼은 `INT`를 유지하고, 엔티티는 `Integer`, 요청·응답 record는 `Long`으로 바꿔 쓴다. **엔티티를 `Long`으로 쓰면 `ddl-auto=validate`가 `integer`와 `bigint` 불일치로 기동을 막는다**(함정 3) |
| `user_subscription.billing_day`, `plan_month.month_index` | `INT`(검토 후 `SMALLINT`에서 바꿈, 6.2절 15번) | `int32` | 엔티티를 `Integer`로 쓴다. (참고: `SMALLINT`로 두면 같은 이유로 validate가 막는다.) `Short`를 쓰거나 컬럼을 `INT`로 키운다(함정 3) |
| 모든 시각 | `TIMESTAMPTZ` | `date-time` | 5.3 |

### 5.3 시각·날짜 규칙 (한 번에 정해 둔다)

| 종류 | DB 타입 | 자바 타입 | 만드는 곳 | 비고 |
|---|---|---|---|---|
| 업무 날짜(적용 시작일, 대상 날짜) | `DATE` | `LocalDate` | 한국 시간대 `Clock` | DB의 `CURRENT_DATE`를 쓰지 않는다(4.1) |
| 사건 시각(확인·저장·"다 봤음"·최초 발견·변경) | `TIMESTAMPTZ` | `Instant` + `@JdbcTypeCode(TIMESTAMP_WITH_TIMEZONE)` | `Clock`(테스트에서 고정 가능) | `OttService`/`BaseTimeEntity`와 같은 방식 |
| 응답의 날짜(`dataAsOf`) | `TIMESTAMPTZ` | `Instant → 한국 날짜 LocalDate` | 응답 변환 | |
| Spring Batch 시각 | `TIMESTAMP`(시간대 없음) | `LocalDateTime` | 라이브러리 | `Instant`로 바꿀 때 `ZoneId.systemDefault()`를 쓴다. **한국 시간대를 하드코딩하지 않는다**: 라이브러리가 JVM 기본 시간대로 만든 값이기 때문이다. JVM 기본 시간대가 로컬(KST)과 Cloudtype(확인 못 함)에서 같은지는 별도 확인이 필요하다 |

### 5.4 그 밖에 알게 된 것

- **A. 서비스당 판매 중인 단품이 하나뿐이라는 보장이 DB에 없다.** `ServicePriceList`는 "서비스 ID 순, 최대 7개", 즉 서비스당 한 줄을 전제한다. 그런데 "서비스당 판매 중(`active`)인 SINGLE 상품은 하나"를 막는 제약이 ERD에 없다(두 테이블에 걸쳐 있어 부분 유니크 인덱스로 만들 수 없다). 관리자 API(Task 061)가 상품 등록·판매 재개 때 검사해야 한다. 어긴 데이터가 들어가면 같은 서비스가 두 줄로 나와 계약이 깨진다.
- **B. 가격 시드는 Flyway에 없다.** ROADMAP은 가격표를 Task 061의 "관리자 입력값"으로 넘겼다(Task 004 기록). 그러면 로컬 DB에는 계산에 필요한 상품·가격이 처음에 없다. 로컬 개발·통합 테스트용 시드를 어떻게 둘지는 문서에 없다(확인 못 함, Task 033·061에서 정한다).
- **C. 작품 제목 검색 인덱스가 ERD에 없다.** `searchTitles`는 "DB 제목 검색"(Task 050)인데 `title.title`·`original_title`에 대한 인덱스 언급이 없다. 후보가 수천 건 규모면 순차 조회로 충분할 가능성이 크다고 보지만 측정하지 못했다. 결정은 Task 050에서 `EXPLAIN`으로 한다. (`LIKE '%키워드%'`를 인덱스로 빠르게 하려면 `pg_trgm` 확장이 필요하고, Supabase에서 확장을 마이그레이션으로 켤 수 있는지는 확인하지 못했다.)
- **D. 에러 코드와 DB 제약의 대응.** `PRICE_START_DATE_DUPLICATE`(409)는 `uk_product_price_product_from` 위반, `WISHLIST_LIMIT_EXCEEDED`는 DB 제약이 아님(앱 규칙), 활성 플랜 유일성(`ux_plan_active`)과 DRAFT 유형 유일성(`ux_plan_draft_type`)은 동시 요청의 안전망이다. 서비스가 먼저 확인해도 동시 요청은 DB 제약이 최후에 막으므로 `DataIntegrityViolationException`을 해당 오류 코드로 바꾸는 처리를 둔다.
- **E. `scheduledCount`·`totalCount`·`savedAmount`는 컬럼으로 두지 않는다.** `plan_item`을 세면 나오고 `savedAmount`는 두 값의 차이다. 저장하면 `plan_item`과 어긋날 수 있다. 응답이 DRAFT 목록(최대 3개)이라 매번 세어도 비용이 작다. 성능 문제가 보이면 이 판단은 뒤집을 수 있다(그때 컬럼을 추가한다). 지금은 "저장하지 않는다"를 기본으로 적었다.
- **F. `ERD.md` 연결도와 표가 서로 다른 곳**이 있다(13절 U5). 이번에 같이 맞추면 이후 헷갈림이 줄어든다.

## 6. 사용자 결정 (D1~D9 모두 확정)

> **확정(2026-10-12, 사용자가 추천대로 결정).** 아래 표의 선택지와 이유는 근거로 남겼고, "확정" 칸이 결론이다. 이 안내서의 4·5·8·9절과 체크리스트는 모두 이 결론대로 쓰여 있다. D9(작품 검색·상세·찜 목록의 `dataAsOf` 출처)는 13절 끝에 있다.

| # | 결정할 것 | 선택지(근거) | 확정 | 영향 |
|---|---|---|---|---|
| D1 | 저장 시각을 어디에 두나 | (a) `plan.activated_at` 추가 (b) `updated_at`을 저장 시각으로 사용 | **확정**: **(a)**. (b)는 ARCHIVED 전환·`change_summary` 기록 때 값이 바뀌어 "저장한 시각"이 사라진다 | `plan` 열 1개, CHECK 1개 |
| D2 | `plan.data_as_of` 타입 | (a) `TIMESTAMPTZ` (b) `DATE` | **확정**: **(a)**. 409 판정이 수집 완료 **시각**과 비교하기 때문(4.8). 응답은 한국 날짜로 변환 | 변환 코드 한 곳(5.3) |
| D3 | enum·범위 CHECK를 어디까지 넣나 | (a) 열끼리의 규칙만(MAPPED⇔서비스, 구독 중⇔요금제·결제일, `activated_at`) (b) (a) + 이번에 새로 정하거나 바뀌는 열의 값 목록 (c) (a) + 모든 enum 열 값 목록 + `minutes > 0`·금액 `>= 0` 같은 변하지 않는 하한 | **확정**: **(c)**. 새 enum 값을 자바에만 추가하고 마이그레이션을 빠뜨리면 첫 INSERT에서 바로 터져서 알아챈다. `V1`(`ott_service.data_quality`)에는 CHECK가 없지만 병합된 파일은 고치지 않는다. 입력 상·하한(`120`, `44640`, `1000000`)은 계약 검증에 맡기고 DB에 넣지 않는다(바뀔 때 마이그레이션이 필요해서) | V2~V7 모든 enum 열 |
| D4 | `user_subscription.product_id`를 MVP에서 채우나 | (a) NULL로 두고 계산 입력 단계(Task 063)가 서비스 → 판매 중 단품으로 찾음 (b) 저장할 때 서버가 판매 중 단품을 찾아 채움 | **확정**: **(a)**. 계약이 `productId`를 받지 않고, (b)는 상품을 판매 종료하고 새로 등록할 때 사용자 행이 옛 상품을 가리키게 된다. 번들(2단계)에서 채운다. 단 ROADMAP Task 055는 `productId`를 받는다고 적혀 있어 어긋난다(13절 U2) | 열은 그대로 NULL 허용 |
| D5 | `billing_day`·`plan_name`의 기본값 | (a) DB 기본값 없음. 구독 중이면 서비스가 결제일이 비었을 때 1을 채우고, 구독 중이 아니면 둘 다 NULL (b) DB `DEFAULT 1` | **확정**: **(a)**. (b)는 FREE·UNKNOWN 행도 1이 되어 계약의 "그 밖에는 null"과 어긋난다 | ERD 문구 수정 |
| D6 | `user_setting.notification_enabled` 기본값(2단계 알림) | (a) 기본 켬 (b) 기본 끔 (c) 2단계에 정하고 그 전에는 끔 | **확정**: **(c)**: 스케치는 `DEFAULT FALSE`. 메일을 동의 없이 보내지 않는 쪽이 안전하다. 다만 PRD FR-16은 "끄면 알림이 생성되지 않는다"고만 써서 기본값이 문서에 없다(13절 U6) | 컬럼 기본값 |
| D7 | `MediaType` enum 위치 | (a) `catalog.domain.MediaType`, 다른 도메인 DTO가 이를 가져다 씀 (b) `global/common`에 둠 (c) API용 enum을 따로 두고 도메인 enum과 변환 | **확정**: **(a)**. 사용처가 많아 변환 코드를 한 곳에 둬야 한다. (b)는 도메인 개념을 global에 올린다. (c)는 변환 코드가 늘어난다 | 4.12 코드 |
| D8 | `availability_change.job_execution_id`를 NULL 허용하나 | (a) NULL 허용 (b) NOT NULL | **확정**: **(a)**. 사용자가 검색해서 DB에 없던 작품을 단건 수집할 때(Task 050, 배치가 아님) 제공 상태가 처음 생긴다. 이 변경을 이력에 남길지가 문서에 없다(남긴다면 배치 ID가 없다). (b)로 하려면 "단건 수집은 이력을 남기지 않는다"를 먼저 정해야 한다 | 열 NULL 허용 + 4.10 인덱스는 같음 |

### 6.1 결정이 아니라 다음 Task에 넘길 내용

| 받는 Task | 내용 |
|---|---|
| 033 | 이 안내서 9절 SQL을 V2(tmdb_provider·availability_change·title CHECK)·V3(subscription_product·product_price)에 반영. V4는 Spring Batch 라이브러리의 `schema-postgresql.sql`(jar 안, 시각 열이 `TIMESTAMP`)을 그대로 복사. `tmdb_provider` 시드 |
| 034 | V5(user_setting·user_subscription·wishlist_item)·V6(plan·plan_month·plan_assignment)에 반영. 9절 SQL의 CHECK를 Testcontainers 테스트로 고정(11절) |
| 043 | 새 제공처만 INSERT(상태 덮어쓰기 금지, 4.3) |
| 044 | 판정은 `MAPPED` 제공처만 사용 |
| 045 | STOPPED 판정, `recover` 전 사전 확인, 복구한 실행의 `endedAt` null 처리와 `failureReason` 생성(4.11) |
| 055 | `PUT /me/settings` 전체 교체, `NOT_SUBSCRIBED` 미저장, 결제일 기본 1(D5) |
| 056 | 찜 개수 집계·`users` 행 잠금(4.9) |
| 061 | `last_verified_at` 갱신 규칙(시작일만 고친 경우), 서비스당 활성 단품 1개 검사(5.4 A), 가격 시드(5.4 B) |
| 063 | 기준 날짜별 가격 조회(4.1), `data_as_of` 변환(5.3), UNKNOWN을 미구독으로 취급 |
| 064 | `activated_at`을 상태 변경과 같은 갱신에서 채움(4.8) |

### 6.2 db-reviewer 검토 후 추가 확정 (2026-10-13, 사용자가 모두 추천대로 결정)

`db-reviewer`(DB 설계 검토 에이전트, 읽기 전용)가 ERD v0.4를 검토해 심각 0건, 권장 13건, 참고 8건을 냈다. 아래를 `ERD.md`·TECH·ROADMAP·계약에 반영했다.

| # | 내용 | 확정 | 반영 위치 |
|---|---|---|---|
| 1 | 매월 재계산 순서: 이전 ACTIVE를 ARCHIVED로 바꾸고 flush한 뒤 새 ACTIVE INSERT | 문서 오류 수정 | ERD plan 소개, TECH 2절, ROADMAP Task 069 |
| 2 | 저장(`activatePlan`)·매월 재계산도 `user_setting`을 `PESSIMISTIC_WRITE`로 잠근다 | 잠금 추가 | TECH 2절 |
| 3 | 같은 트랜잭션에서 delete 뒤 save로 같은 유니크 키를 넣으면 INSERT가 먼저 실행되어 위반 → 벌크 삭제를 먼저 하거나 비교 갱신 | 문서에 명시 | ERD 설계 결정, TECH 6절 |
| 4 | 열끼리의 CHECK 추가: `watch_unit`(SEASON ⇔ 시즌 번호), `availability`(UNKNOWN ⇔ `unknown_reason`), `plan_item`(UNSCHEDULED ⇔ `reason`) | 확정 | ERD, 9.2절 |
| 5 | 금액·분 열 `>= 0`, `must_completed <= must_total` | 확정 | ERD, 9.2절 |
| 6 | `plan_month_product`에 `UNIQUE(plan_month_id, product_id)`, 단독 인덱스 삭제 | 확정 | ERD, 9.2절 |
| 7 | `plan_month.confirmed`는 `CHECK (confirmed = (month_index = 0))`로 묶음 | CHECK | ERD, 9.2절 |
| 10 | 상품 `name`은 계약에 `maxLength: 100`(DB `VARCHAR(100)`과 맞춤, 101자는 400) | 계약에 상한 | `openapi.yaml` 3곳 |
| 12 | "`id BIGINT` PK" 예외에 `user_setting`·`product_component`·`title_genre` 추가 | 확정 | ERD 머리말, `backend.md` |
| 13 | 연결도를 표에 맞춤(`title.fetched_at` timestamptz, `user_setting`은 선택 관계) | 확정 | ERD 연결도 |
| 15 | `SMALLINT`(`billing_day`, `month_index`)를 `INT`로 | INT | ERD 연결도, 5.2절, 9.2절 |
| 16 | `calc_run` 보존 기준(계산마다 3행, 탈퇴 뒤에도 남음)을 Task 080 결정 목록에 | 추가 | ROADMAP Task 080 |
| 17 | `tmdbId`가 INT 범위를 넘으면 500 → 계약에 `maximum: 2147483647` | 계약에 상한 | `openapi.yaml` 2곳 |

**참고로 남긴 것(지금 고치지 않고 Task 033~034 또는 다음 ERD 손질 때 처리):** 8번 "서비스당 판매 중 SINGLE 상품은 1개" 규칙을 앱 규칙으로 ERD에 명시, 9번 `NUMERIC`↔`Double` validate 호환(`popularity`, `vote_average`), 11번 같은 새 작품의 동시 검색에서 `uk_title_media_tmdb` 위반 500, 14·18·19·20·21번 연결도 정리·`mapping_state`/`tier` CHECK·첫 설정 저장 경합·`completed_month_index` 설명·13절 처리 칸 문구.

**검토가 실행으로 확인하지 못한 것:** NUMERIC↔Double·SMALLINT↔Integer가 실제로 validate에 막히는지, 2번 교착의 재현, 3번 flush 순서 문제, 추가한 CHECK가 정상 데이터를 막지 않는지, mermaid 렌더링. 033·034의 마이그레이션 테스트와 구현에서 확인한다.

## 7. 고도화 후보 인덱스 검토 (PRD 9절 "고도화 후보")

PRD 318행의 고도화 후보(인물 검색, 장르 검색, 메인 공통 추천 목록)는 **FR이 아니다**. 이 Task에서는 인덱스를 만들지 않고, 나중에 필요해질 때 **새 마이그레이션 버전**으로 추가하면 된다는 근거를 정리한다(테이블 구조는 이미 후보 기능을 지원하므로 스키마 변경이 필요 없다). 근거가 없는 효과를 단정하지 않았고, 쓰기 비용·저장 용량(Supabase 무료 500MB, TECH 6절)이라는 비용은 지금 넣지 않는 이유가 된다.

| 후보 기능 | 테이블 | 필요해질 인덱스(후보) | 지금 넣을까 | 이유 |
|---|---|---|---|---|
| 인물 검색(`title_cast` 이름으로 찾기) | `title_cast` | `(name)` 또는 부분 일치용 `pg_trgm` GIN, 동명이인 구분용 `(person_tmdb_id)` | **아니오** | FR이 아니고, 검색 방식(정확/부분 일치)이 정해지지 않았다. 부분 일치는 확장(`pg_trgm`)이 필요한데 Supabase에서 마이그레이션으로 켤 수 있는지 확인하지 못했다(5.4 C와 같은 질문). `UNIQUE(title_id, cast_order)`가 작품 → 출연진 방향은 이미 덮는다 |
| 장르 검색 | `title_genre` | `(genre_id, title_id)` | **아니오(필요해질 때 추가)** | 기본키 `(title_id, genre_id)`가 작품 → 장르 방향만 덮는다. 장르 → 작품 방향은 반대 순서 인덱스가 필요하지만 작은 정수 두 개짜리 연결 테이블이라 나중에 추가해도 비용이 작다 |
| 서비스별 "새로 들어온 작품" | `availability_change` | `(ott_service_id, changed_at DESC)`를 `after_status = 'AVAILABLE'` 조건 부분 인덱스로 | **아니오** | 첫 적재분(`before_status IS NULL`)을 어떻게 뺄지 등 정의가 미정이다. 이번에 넣는 `(job_execution_id)`와는 용도가 다르다. TECH T-9(오래된 DRAFT 판정 개선)가 `(watch_unit_id, changed_at)`를 쓰게 될 수도 있어 두 용도를 합쳐 한 번에 설계하는 편이 낫다 |
| "찜 많은 작품" 집계 | `wishlist_item` | `(watch_unit_id) WHERE watched_at IS NULL` | **아니오** | 집계는 캐시에 두는 쪽이 자연스럽고(숫자만 노출, PRD 321행) 사용자 수가 적은 MVP에서 순차 조회로 충분할 가능성이 크다(측정하지 못함). 참고: 위 인덱스는 2단계 O4의 `watch_unit` 6개월 삭제 때도 필요해진다. PostgreSQL은 외래 키를 **가진 쪽**에 인덱스를 자동으로 만들지 않아서, 부모(`watch_unit`) 행을 지울 때마다 자식 테이블 전체를 훑는다(ERD "참조 컬럼 인덱스" 절이 같은 이유로 일부 FK에 인덱스를 둔다). `wishlist_item.watch_unit_id`·`plan_item.watch_unit_id`·`availability_change.watch_unit_id`·`availability(ott_service_id)`가 그 후보다 |
| 메인 "인기작" | `title` | `(popularity DESC)` | **아니오** | 후보가 수천 건 규모라 정렬 비용이 작다고 보지만 측정하지 못했다 |

## 8. `ERD.md` 갱신 항목 (v0.3 → v0.4)

**이 절의 ①~⑬은 2026-10-13에 `docs/ERD.md`에 모두 적용했다**(v0.4). 아래는 무엇을 어떻게 바꿨는지의 기록이고, 검토할 때 대조표로 쓴다. 줄 번호는 적용 전 `docs/ERD.md`(v0.3) 기준이다. 긴 행(`plan`, `wishlist_item`)은 통째가 아니라 **바꿀 문장**만 적었다. 짧은 행·표는 통째 교체문이다. 문구는 사용자가 다듬어도 된다.

| # | 위치 | 바꿀 내용 |
|---|---|---|
| ① | 3행(머리말) | `초안 v0.3 · 2026-10-03` → `초안 v0.4 · {반영한 날짜}` |
| ② | 56~61행(연결도 A, `tmdb_provider`) | 아래 블록 ② |
| ③ | 87~93행(연결도 A, `availability_change`) | 아래 블록 ③ |
| ④ | 120~129행(표 `tmdb_provider`) | 아래 블록 ④ 통째 교체 |
| ⑤ | 136행(`title.media_type` 행) | 아래 블록 ⑤ |
| ⑥ | 180~188행(표 `availability_change`) | 아래 블록 ⑥ 통째 교체 |
| ⑦ | 220~226행(연결도 B, `product_price`), 234·236행(표 B) | 아래 블록 ⑦ |
| ⑧ | 260~286행(연결도 C), 292·293·295행(표 C) | 아래 블록 ⑧ |
| ⑨ | 315~351행(연결도 D), 363·367행(표 D) | 아래 블록 ⑨ |
| ⑩ | "설계 결정" 절(389행~) 끝 | 새 소절 "API 필드와 컬럼 대응(파생 값)": 5.1절 표와 5.3절 시각 규칙을 옮긴다(선택, 권장) |
| ⑪ | "참조 컬럼 인덱스"(411~414행) | `availability_change(job_execution_id)` 한 줄 추가 |
| ⑫ | E 영역 `BATCH_*` 행(377행) | 끝에 "수집 실행의 `STOPPED`(중단됨)는 이 테이블에 저장하지 않는 파생 상태다. `STARTED`인데 지금 이 서버가 돌리고 있지 않은 실행을 서버가 판정한다(API `CollectExecutionStatus`)" |
| ⑬ | 5행(머리말 둘째 문단) | 서비스 테이블 24개 그대로(변경 없음 확인) |

**블록 ② (연결도 A, `tmdb_provider`)**

```
  tmdb_provider {
    int provider_id PK "TMDB 값"
    varchar tmdb_name "원본 라벨"
    varchar mapping_state "UNMAPPED / MAPPED / IGNORED"
    bigint ott_service_id FK "MAPPED일 때만"
    varchar tier "STANDARD / AD"
  }
```

**블록 ③ (연결도 A, `availability_change`)**

```
  availability_change {
    bigint id PK
    bigint watch_unit_id FK
    bigint ott_service_id FK
    varchar before_status
    varchar after_status
    bigint job_execution_id "NULL 허용"
    timestamptz changed_at
  }
```

**블록 ④ (표 `tmdb_provider` 통째 교체)**

```markdown
### tmdb_provider — TMDB 제공처와 서비스 매핑

| 컬럼 | 타입 | 키 | 설명 |
|---|---|---|---|
| provider_id | INT | PK | TMDB 값 그대로 (8, 1796 등). 외부 값이 안정적인 식별자라 대리키를 두지 않는다 |
| tmdb_name | VARCHAR(100) | | TMDB 원본 라벨. 관리자 확인용이며 판정에 쓰지 않는다 |
| logo_path | VARCHAR(200) | | |
| mapping_state | VARCHAR(20) | | UNMAPPED(미매핑, 처음 발견된 상태) / MAPPED(서비스에 연결됨) / IGNORED(관련 없음). 기본값 UNMAPPED. 판정에는 MAPPED만 쓴다. 수집은 이미 있는 제공처의 상태를 덮어쓰지 않는다 |
| ott_service_id | BIGINT | FK | 우리 판정. MAPPED일 때만 값이 있고 UNMAPPED·IGNORED는 NULL. `CHECK ((mapping_state = 'MAPPED') = (ott_service_id IS NOT NULL))` |
| tier | VARCHAR(20) | | STANDARD / AD. 우리 판정. UNMAPPED·IGNORED는 STANDARD로 저장 |
| first_seen_at | TIMESTAMPTZ | | 처음 발견된 시각. 미매핑 제공처를 관리자가 확인하는 데 쓴다 |
```

**블록 ⑤ (`title.media_type` 행)**

```markdown
| media_type | VARCHAR(10) | UK | MOVIE / TV (`CHECK`). DB는 대문자로 저장하고 API는 소문자 `movie`/`tv`다. 변환은 도메인 enum(`MediaType`)이 맡는다 |
```

**블록 ⑥ (표 `availability_change` 통째 교체)**

```markdown
### availability_change — 제공 상태 변경 이력

| 컬럼 | 타입 | 키 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| watch_unit_id, ott_service_id | BIGINT | FK | |
| before_status, after_status | VARCHAR(20) | | 신규는 before가 NULL |
| job_execution_id | BIGINT | | Spring Batch 실행 ID (FK 없이 참조값만). 배치 밖(단건 수집)에서 생긴 변경이면 NULL. **인덱스 있음**: 실행별 "바뀐 작품" 조회와 실행 이력의 변경 건수 집계가 이 값으로 거른다 |
| changed_at | TIMESTAMPTZ | | 재계산·변경 알림의 입력 |
```

**블록 ⑦ (B 영역)**

연결도 B의 `product_price`:

```
  product_price {
    bigint id PK
    bigint product_id FK
    int monthly_price
    date effective_from
    timestamptz last_verified_at
  }
```

표 B의 두 행:

```markdown
| subscription_product | id, name, product_type, active | 관리자가 등록. 판매 종료 상품은 active=false (삭제하지 않음). API에서는 `isActive` |
| product_price | id, product_id FK, monthly_price, effective_from, last_verified_at | `UNIQUE(product_id, effective_from)`. **종료일은 저장하지 않는다**: 다음 줄 시작일의 전날까지 적용되고 마지막 줄은 계속 적용된다. 줄은 맨 뒤에 시작일이 더 늦은 줄로만 추가하고 수정·삭제는 마지막 줄만 허용하므로 기간이 겹치거나 비는 일이 구조상 없다(FR-19). 가격 조회는 기준 날짜(오늘 또는 사용자 결제일, 한국 날짜) 이하의 시작일 중 가장 늦은 줄. last_verified_at(TIMESTAMPTZ)은 관리자가 마지막으로 확인한 일시이며 상품 등록·줄 추가·수정·[요금 확인함]이 현재 시각으로 바꾼다(PRD 11절 41번). 90일 판정은 화면이 한국 날짜 기준으로 한다 |
```

**블록 ⑧ (C 영역)**

연결도 C의 `user_subscription`·`wishlist_item`:

```
  user_subscription {
    bigint id PK
    bigint user_id FK
    bigint ott_service_id FK
    varchar status "SUBSCRIBED / FREE / UNKNOWN"
    bigint product_id FK "MVP는 NULL"
    varchar plan_name "구독 중일 때만"
    int billing_day "구독 중일 때만"
  }
```

```
  wishlist_item {
    bigint id PK
    bigint user_id FK
    bigint watch_unit_id FK
    varchar priority "MUST / WANT / MAYBE"
    timestamptz watched_at
  }
```

표 C의 `user_setting`·`user_subscription` 행(통째 교체):

```markdown
| user_setting | user_id PK·FK, monthly_budget, monthly_watch_minutes, watch_preset, notification_enabled | 1:1. 예산·시청 시간 필수(FR-10). watch_preset = LIGHT / NORMAL / HEAVY / CUSTOM. **행이 있으면 온보딩(첫 설정 저장)을 마친 사용자**이고 행은 `PUT /me/settings`가 처음 만든다(가입 때 만들지 않는다). 구독 행 수로는 온보딩 여부를 알 수 없다(모두 미구독이면 0행). 환산 입력(주 N회·회당 M시간)은 저장하지 않는다(R11-18 A안) |
| user_subscription | id, user_id FK, ott_service_id FK, status, product_id FK NULL, plan_name, billing_day | `UNIQUE(user_id, ott_service_id)`. status = SUBSCRIBED(구독 중) / FREE(무료 이용) / UNKNOWN(잘 모르겠음, 저장하며 계산에서는 미구독과 같이 취급). **미구독(NOT_SUBSCRIBED)은 행을 두지 않는다**(API가 행이 없는 서비스를 NOT_SUBSCRIBED로 채워 준다). plan_name(VARCHAR(30))은 표시용 요금제 이름, billing_day(1~31)는 결제일이며 둘 다 SUBSCRIBED일 때만 값이 있고 나머지 상태에서는 NULL(`CHECK`). 결제일이 비어 들어오면 서비스가 1을 채운다(DB 기본값이 아니다). MVP는 product_id를 NULL로 두고 계산 입력 단계가 서비스의 판매 중 단품을 찾는다. 번들 이용 시 구성 서비스 행들이 같은 product_id를 가리킨다(2단계) |
```

`wishlist_item` 행(295행)에서 바꿀 문장 두 곳:

- `watched_at NULL` → `watched_at(TIMESTAMPTZ) NULL`
- "…애플리케이션 규칙이라 DB 제약(CHECK·트리거)을 두지 않는다(…)" 뒤에 한 문장 추가: "개수는 `SELECT count(*) FROM wishlist_item WHERE user_id = ? AND watched_at IS NULL`로 센다(`UNIQUE(user_id, watch_unit_id)`의 선두가 user_id라 별도 인덱스가 없다). 검사·저장이 한 단위로 묶여야 하는 동작은 새 찜 추가와 '다 봤음' 되돌리기 둘이다."

**블록 ⑨ (D 영역)**

연결도 D의 `plan`·`plan_month`·`plan_assignment`:

```
  plan {
    bigint id PK
    bigint user_id FK
    bigint previous_plan_id FK
    varchar status "DRAFT / ACTIVE / ARCHIVED"
    varchar plan_type "RECOMMENDED / SAVER / SIMPLE"
    date start_month
    varchar algorithm "EXACT / GREEDY"
    int total_cost
    timestamptz data_as_of
    timestamptz activated_at "DRAFT는 NULL"
  }
  plan_month {
    bigint id PK
    bigint plan_id FK
    int month_index
    date month "그 달의 1일"
    boolean confirmed
    int cost
  }
  plan_assignment {
    bigint id PK
    bigint plan_item_id FK
    bigint plan_month_id FK
    bigint ott_service_id FK
    int minutes
  }
```

표 D `plan` 행(363행)에서 바꿀 곳 세 가지:

1. 열 목록 `…, data_as_of, change_summary` → `…, data_as_of, activated_at, change_summary`
2. "저장 시 DRAFT의 data_as_of가 최신 수집보다 오래됐으면 서비스에서 막는다." 뒤에 추가: "data_as_of(TIMESTAMPTZ)는 계산에 쓴 수집 Job의 마지막 완료 시각이고 API 응답은 한국 날짜로 바꿔 준다. activated_at(TIMESTAMPTZ, NULL 허용)은 ACTIVE로 저장된 시각이며 DRAFT만 NULL이고 ACTIVE·ARCHIVED는 반드시 값이 있다(`CHECK ((status = 'DRAFT') = (activated_at IS NULL))`). 상태와 activated_at은 같은 갱신에서 바꾼다. 매월 재계산이 DRAFT 없이 만드는 ACTIVE도 값을 채운다. scheduledCount·totalCount·savedAmount는 저장하지 않고 plan_item과 두 비용 열에서 계산한다."
3. start_month 설명: "첫 달의 1일(DATE), API에서는 YYYY-MM"

표 D `plan_month`·`plan_assignment` 행:

```markdown
| plan_month | id, plan_id FK, month_index, month, confirmed, cost | `UNIQUE(plan_id, month_index)`. month_index 0은 확정(이번 달), 1·2는 예상. month는 그 달의 1일(DATE)이며 API에서는 YYYY-MM. confirmed는 API의 isConfirmed |
| plan_assignment | id, plan_item_id FK, plan_month_id FK, ott_service_id FK, minutes | `UNIQUE(plan_item_id, plan_month_id)`, `CHECK (minutes > 0)`. 긴 시즌은 연속된 달에 걸쳐 여러 행으로 나뉘고 한 달 = 한 조각이다. API의 watchMin이 minutes이고, segmentIndex·segmentCount·isContinuation은 저장하지 않고 `ROW_NUMBER`·`COUNT` 윈도우로 계산한다. ott_service_id는 엔진이 상품만 주므로 plan 서비스가 변환해 채운다 |
```

## 9. Flyway 버전 배치와 확정 SQL 스케치

### 9.1 어느 버전에 무엇을 넣나

| 버전 | Task | 이번 보정으로 들어갈 것 |
|---|---|---|
| V1 | 019(병합됨) | **없음.** 고치지 않는다 |
| V2 | 033 | `tmdb_provider`(+`mapping_state`·CHECK·시드 8행), `title`(`ck_title_media_type`), `availability_change`(+`job_execution_id` 인덱스·NULL 허용). 나머지 테이블(`watch_unit`, `availability`, `genre`, `title_genre`, `title_cast`)은 이번 보정이 건드리지 않는다 |
| V3 | 033 | `subscription_product`(`active`), `product_price`(`effective_to` 없음, `last_verified_at`) |
| V4 | 033 | Spring Batch `schema-postgresql.sql` 복사(보정 없음) |
| V5 | 034 | `user_setting`, `user_subscription`(`UNKNOWN`·`plan_name`·CHECK), `wishlist_item`(`watched_at` TIMESTAMPTZ) |
| V6 | 034 | `plan`(`activated_at`·`data_as_of`), `plan_month`(`month`), `plan_assignment`(`minutes > 0`) |
| V7 | 034 | 보정 없음(`notification_outbox`, `shedlock`) |

병합된 마이그레이션 파일은 고치지 않고 새 버전을 추가한다는 규칙은 V2 이후가 병합된 **뒤**부터 적용된다. 지금은 모두 미병합이라 위 내용을 각 파일에 처음부터 넣는다.

### 9.2 SQL 스케치 (실행 확인 완료, 파일로 만들지 않는다)

규칙: 들여쓰기는 V1처럼 **탭**, 주석은 한국어. 아래는 **보정이 닿는 테이블의 전체 정의**이고 `NOT NULL` 범위·길이는 ERD 표에 값이 없는 곳을 합리적으로 정한 것이라 033·034에서 다시 본다. 변경과 무관한 열의 `NOT NULL` 여부는 이 안내서의 근거가 아니다. 실행해 본 것은 이 SQL 그대로다(부록 A).

**V2: tmdb_provider, title의 CHECK, availability_change**

```sql
-- (V2 작품 영역) tmdb_provider
CREATE TABLE tmdb_provider (
	provider_id    INT          PRIMARY KEY,                       -- TMDB 값 그대로(8, 1796 등). 대리키를 두지 않는다
	tmdb_name      VARCHAR(100) NOT NULL,                          -- TMDB 원본 라벨. 관리자 확인용이며 판정에 쓰지 않는다
	logo_path      VARCHAR(200),                                   -- TMDB 이미지 상대 경로
	mapping_state  VARCHAR(20)  NOT NULL DEFAULT 'UNMAPPED',       -- UNMAPPED 미매핑 / MAPPED 서비스에 연결됨 / IGNORED 관련 없음
	ott_service_id BIGINT       REFERENCES ott_service (id),       -- MAPPED일 때만 값이 있다
	tier           VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',       -- STANDARD / AD. UNMAPPED·IGNORED는 STANDARD로 둔다
	first_seen_at  TIMESTAMPTZ  NOT NULL,                          -- 처음 발견된 시각. 수집 코드가 채운다
	created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
	updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
	CONSTRAINT ck_tmdb_provider_mapping_state CHECK (mapping_state IN ('UNMAPPED', 'MAPPED', 'IGNORED')),
	CONSTRAINT ck_tmdb_provider_tier CHECK (tier IN ('STANDARD', 'AD')),
	-- MAPPED이면 서비스가 있고, 아니면 서비스가 없다(UNMAPPED와 IGNORED는 서비스 NULL 여부로 구분할 수 없어 상태 열을 둔 것이므로 둘이 어긋나면 안 된다)
	CONSTRAINT ck_tmdb_provider_mapped_service CHECK ((mapping_state = 'MAPPED') = (ott_service_id IS NOT NULL))
);

-- Task 004 매핑표 시드. 서비스 ID는 코드로 찾는다(V1 주석: 새 DB에서만 1~7이 보장된다)
INSERT INTO tmdb_provider (provider_id, tmdb_name, mapping_state, ott_service_id, tier, first_seen_at) VALUES
	(8,    'Netflix',                   'MAPPED', (SELECT id FROM ott_service WHERE code = 'NETFLIX'),       'STANDARD', now()),
	(1796, 'Netflix Standard with Ads', 'MAPPED', (SELECT id FROM ott_service WHERE code = 'NETFLIX'),       'AD',       now()),
	(337,  'Disney Plus',               'MAPPED', (SELECT id FROM ott_service WHERE code = 'DISNEY_PLUS'),   'STANDARD', now()),
	(1883, 'TVING',                     'MAPPED', (SELECT id FROM ott_service WHERE code = 'TVING'),         'STANDARD', now()),
	(97,   'Watcha',                    'MAPPED', (SELECT id FROM ott_service WHERE code = 'WATCHA'),        'STANDARD', now()),
	(356,  'wavve',                     'MAPPED', (SELECT id FROM ott_service WHERE code = 'WAVVE'),         'STANDARD', now()),
	(1881, 'Coupang Play',              'MAPPED', (SELECT id FROM ott_service WHERE code = 'COUPANG_PLAY'),  'STANDARD', now()),
	(350,  'Apple TV',                  'MAPPED', (SELECT id FROM ott_service WHERE code = 'APPLE_TV_PLUS'), 'STANDARD', now());

-- (V2) title 중 이번 보정에 해당하는 줄: 영화·드라마 구분 값은 DB에 대문자로 저장한다(API의 movie/tv 변환은 코드에서 한다)
-- 실제 V2에서는 이 두 줄을 CREATE TABLE title 안의 CONSTRAINT로 넣는다. 아래는 실행 확인을 위해 ALTER로 쓴 형태다
ALTER TABLE title ADD CONSTRAINT ck_title_media_type CHECK (media_type IN ('MOVIE', 'TV'));
ALTER TABLE title ADD CONSTRAINT uk_title_media_tmdb UNIQUE (media_type, tmdb_id);

-- (V2) availability_change
CREATE TABLE availability_change (
	id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	watch_unit_id    BIGINT      NOT NULL REFERENCES watch_unit (id),
	ott_service_id   BIGINT      NOT NULL REFERENCES ott_service (id),
	before_status    VARCHAR(20),                                  -- 처음 확인된 건은 NULL
	after_status     VARCHAR(20) NOT NULL,
	job_execution_id BIGINT,                                       -- Spring Batch 실행 ID. FK 없이 값만 둔다. 배치 밖(단건 수집)에서 생긴 변경이면 NULL
	changed_at       TIMESTAMPTZ NOT NULL,
	created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT ck_availability_change_before CHECK (before_status IS NULL OR before_status IN ('AVAILABLE', 'NOT_AVAILABLE', 'UNKNOWN')),
	CONSTRAINT ck_availability_change_after CHECK (after_status IN ('AVAILABLE', 'NOT_AVAILABLE', 'UNKNOWN'))
);
-- 실행별 "바뀐 작품" 조회(listCollectExecutionChanges)와 실행 이력의 changedCount 집계가 모두 job_execution_id로 거른다
CREATE INDEX idx_availability_change_job_execution ON availability_change (job_execution_id);
```

**V3: subscription_product, product_price**

```sql
-- (V3 상품 영역) subscription_product
CREATE TABLE subscription_product (
	id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	name         VARCHAR(100) NOT NULL,                            -- 예: 넷플릭스 스탠다드
	product_type VARCHAR(20)  NOT NULL,                            -- SINGLE / BUNDLE(2단계)
	active       BOOLEAN      NOT NULL DEFAULT TRUE,               -- false면 판매 종료. 삭제하지 않는다. API에서는 isActive
	created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
	updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
	CONSTRAINT ck_subscription_product_type CHECK (product_type IN ('SINGLE', 'BUNDLE'))
);

-- (V3) product_price: 종료일(effective_to)은 없다. 다음 줄 시작일의 전날까지 적용되고 마지막 줄은 계속 적용된다
CREATE TABLE product_price (
	id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	product_id       BIGINT      NOT NULL REFERENCES subscription_product (id),
	monthly_price    INT         NOT NULL,                         -- 월 구독요금(원)
	effective_from   DATE        NOT NULL,                         -- 적용 시작일(한국 날짜)
	last_verified_at TIMESTAMPTZ NOT NULL,                         -- 관리자가 마지막으로 확인한 일시. 도메인 메서드가 채우므로 DEFAULT를 두지 않는다
	created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT uk_product_price_product_from UNIQUE (product_id, effective_from),
	CONSTRAINT ck_product_price_monthly_price CHECK (monthly_price >= 0)
);
```

**V5: user_setting, user_subscription, wishlist_item**

```sql
-- (V5 사용자 영역) user_setting: 행이 있으면 온보딩을 마친 사용자다
CREATE TABLE user_setting (
	user_id               BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
	monthly_budget        INT         NOT NULL,                    -- 월 예산(원). 0이면 이미 쓰는 서비스만으로 계산
	monthly_watch_minutes INT         NOT NULL,                    -- 월 시청 시간(분)
	watch_preset          VARCHAR(10) NOT NULL,                    -- LIGHT / NORMAL / HEAVY / CUSTOM
	notification_enabled  BOOLEAN     NOT NULL DEFAULT FALSE,      -- 2단계 알림 수신 여부. 기본 꺼짐으로 확정(안내서 6절 D6), 최종 기본값은 2단계 알림 설계에서 확정
	created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT ck_user_setting_budget CHECK (monthly_budget >= 0),
	CONSTRAINT ck_user_setting_minutes CHECK (monthly_watch_minutes >= 0),
	CONSTRAINT ck_user_setting_preset CHECK (watch_preset IN ('LIGHT', 'NORMAL', 'HEAVY', 'CUSTOM'))
);

-- (V5) user_subscription: 미구독(NOT_SUBSCRIBED)은 행을 두지 않는다. UNKNOWN(잘 모르겠음)은 저장한다
CREATE TABLE user_subscription (
	id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	ott_service_id BIGINT      NOT NULL REFERENCES ott_service (id),
	status         VARCHAR(20) NOT NULL,                           -- SUBSCRIBED / FREE / UNKNOWN
	product_id     BIGINT      REFERENCES subscription_product (id), -- MVP는 NULL로 둔다(안내서 6절 D4)
	plan_name      VARCHAR(30),                                    -- 요금제 이름(표시용). SUBSCRIBED일 때만 값이 있다
	billing_day    INT,                                            -- 결제일 1~31. (엔티티 Integer와 맞추려고 INT, 6.2절 15번) SUBSCRIBED일 때만 값이 있다(저장 요청이 비면 서비스가 1로 채운다)
	created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT uk_user_subscription_user_service UNIQUE (user_id, ott_service_id),
	CONSTRAINT ck_user_subscription_status CHECK (status IN ('SUBSCRIBED', 'FREE', 'UNKNOWN')),
	CONSTRAINT ck_user_subscription_billing_day CHECK (billing_day BETWEEN 1 AND 31),
	-- 요금제 이름·결제일은 구독 중일 때만 있다(계약 UserSubscription의 규칙을 DB도 지킨다)
	CONSTRAINT ck_user_subscription_subscribed_fields CHECK (
		(status = 'SUBSCRIBED' AND plan_name IS NOT NULL AND billing_day IS NOT NULL)
		OR (status <> 'SUBSCRIBED' AND plan_name IS NULL AND billing_day IS NULL))
);

-- (V5) wishlist_item
CREATE TABLE wishlist_item (
	id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	watch_unit_id BIGINT      NOT NULL REFERENCES watch_unit (id),
	priority      VARCHAR(10) NOT NULL,                            -- MUST / WANT / MAYBE
	watched_at    TIMESTAMPTZ,                                     -- "다 봤음" 시각. NULL이면 안 봤음(찜 20개 상한은 이 값이 NULL인 행만 센다)
	created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT uk_wishlist_item_user_unit UNIQUE (user_id, watch_unit_id),
	CONSTRAINT ck_wishlist_item_priority CHECK (priority IN ('MUST', 'WANT', 'MAYBE'))
);
```

**V6: plan, plan_month, plan_assignment** (V6의 나머지 `plan_item`·`plan_month_product`·`calc_run`은 이번 보정과 무관하다)

```sql
-- (V6 플랜 영역) plan
CREATE TABLE plan (
	id                    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	user_id               BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	previous_plan_id      BIGINT      REFERENCES plan (id) ON DELETE SET NULL,
	status                VARCHAR(10) NOT NULL,                    -- DRAFT / ACTIVE / ARCHIVED
	plan_type             VARCHAR(15) NOT NULL,                    -- RECOMMENDED / SAVER / SIMPLE
	start_month           DATE        NOT NULL,                    -- 첫 달의 1일. API에서는 YYYY-MM
	monthly_budget        INT         NOT NULL,                    -- 계산 당시 예산(스냅샷)
	monthly_watch_minutes INT         NOT NULL,                    -- 계산 당시 시청 시간(스냅샷)
	algorithm             VARCHAR(10) NOT NULL,                    -- EXACT / GREEDY
	must_total            INT         NOT NULL,
	must_completed        INT         NOT NULL,
	score                 INT         NOT NULL,
	total_cost            INT         NOT NULL,
	all_subscribe_cost    INT         NOT NULL,                    -- 전체 구독 비용. 세 유형이 같은 값
	data_as_of            TIMESTAMPTZ NOT NULL,                    -- 계산에 쓴 데이터 기준 시각(수집 Job 마지막 완료 시각). API 응답은 한국 날짜로 바꿔 준다
	activated_at          TIMESTAMPTZ,                             -- ACTIVE로 저장(활성화)된 시각. DRAFT는 NULL
	change_summary        TEXT,
	created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT ck_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
	CONSTRAINT ck_plan_type CHECK (plan_type IN ('RECOMMENDED', 'SAVER', 'SIMPLE')),
	CONSTRAINT ck_plan_algorithm CHECK (algorithm IN ('EXACT', 'GREEDY')),
	CONSTRAINT ck_plan_start_month CHECK (EXTRACT(DAY FROM start_month) = 1),
	-- 초안(DRAFT)만 저장 시각이 없다. ACTIVE와 ARCHIVED는 한 번은 활성화됐던 플랜이다
	CONSTRAINT ck_plan_activated_at CHECK ((status = 'DRAFT') = (activated_at IS NULL)),
	-- 금액·분·개수의 하한과 "꼭 볼 것 중 시청 완료 수"의 상한(6.2절 5번)
	CONSTRAINT ck_plan_amounts CHECK (monthly_budget >= 0 AND monthly_watch_minutes >= 0 AND total_cost >= 0
		AND all_subscribe_cost >= 0 AND must_total >= 0 AND must_completed BETWEEN 0 AND must_total)
);
CREATE UNIQUE INDEX ux_plan_active ON plan (user_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_plan_draft_type ON plan (user_id, plan_type) WHERE status = 'DRAFT';
CREATE INDEX idx_plan_user ON plan (user_id);
CREATE INDEX idx_plan_previous ON plan (previous_plan_id);

-- (V6) plan_month
CREATE TABLE plan_month (
	id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	plan_id     BIGINT      NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
	month_index INT         NOT NULL,                              -- 0 이번 달(확정), 1·2 예상 (엔티티 Integer와 맞추려고 INT, 6.2절 15번)
	month       DATE        NOT NULL,                              -- 그 달의 1일. API에서는 YYYY-MM
	confirmed   BOOLEAN     NOT NULL,                              -- API에서는 isConfirmed
	cost        INT         NOT NULL,
	created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT uk_plan_month_plan_index UNIQUE (plan_id, month_index),
	CONSTRAINT ck_plan_month_index CHECK (month_index BETWEEN 0 AND 2),
	CONSTRAINT ck_plan_month_month CHECK (EXTRACT(DAY FROM month) = 1),
	CONSTRAINT ck_plan_month_cost CHECK (cost >= 0),
	-- confirmed는 month_index 0에서 파생되는 값이라 어긋난 값이 못 들어가게 묶는다(6.2절 7번)
	CONSTRAINT ck_plan_month_confirmed CHECK (confirmed = (month_index = 0))
);

-- (V6) plan_assignment: 시청 시간(minutes)이 API의 watchMin이다. 조각 번호(segmentIndex 등)는 저장하지 않고 조회할 때 계산한다
CREATE TABLE plan_assignment (
	id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	plan_item_id   BIGINT      NOT NULL REFERENCES plan_item (id) ON DELETE CASCADE,
	plan_month_id  BIGINT      NOT NULL REFERENCES plan_month (id) ON DELETE CASCADE,
	ott_service_id BIGINT      NOT NULL REFERENCES ott_service (id), -- 이 달에 이 분량을 보는 서비스
	minutes        INT         NOT NULL,
	created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT uk_plan_assignment_item_month UNIQUE (plan_item_id, plan_month_id),
	CONSTRAINT ck_plan_assignment_minutes CHECK (minutes > 0)      -- 엔진의 Assignment가 1분 이상만 허용한다
);
CREATE INDEX idx_plan_assignment_month ON plan_assignment (plan_month_id);
```

**검토 후 추가한 제약(6.2절 4·5·6번).** 위 스케치에 없는 테이블의 제약이다. 각 `CREATE TABLE`의 해당 위치에 넣는다. 이 제약들은 임시 DB에서 **아직 실행해 보지 않았다**(Task 033·034의 마이그레이션 테스트에서 정상 데이터가 막히지 않는지, 잘못된 데이터가 거부되는지 확인한다).

```sql
-- (V2) watch_unit: 영화는 시즌 번호가 없고 시즌은 있다
CONSTRAINT ck_watch_unit_season CHECK ((unit_type = 'SEASON') = (season_number IS NOT NULL))
-- (V2) availability: UNKNOWN일 때만 unknown_reason이 있다(6.2절 4번 확정. 수집이 항상 지키는지는 Task 043에서 테스트로 확인)
CONSTRAINT ck_availability_unknown_reason CHECK ((status = 'UNKNOWN') = (unknown_reason IS NOT NULL))
-- (V6) plan_item: 못 넣은 작품(UNSCHEDULED)만 이유가 있다
CONSTRAINT ck_plan_item_reason CHECK ((outcome = 'UNSCHEDULED') = (reason IS NOT NULL))
-- (V6) plan_month_product: 같은 달에 같은 상품은 한 줄, 금액 하한. 이 UNIQUE가 plan_month_id 인덱스를 겸하므로 단독 인덱스는 만들지 않는다
CONSTRAINT uk_plan_month_product_month_product UNIQUE (plan_month_id, product_id),
CONSTRAINT ck_plan_month_product_price CHECK (applied_price >= 0)
```

스케치에서 일부러 한 선택(바꿔도 되는 것): `created_at`/`updated_at`은 V1처럼 `NOT NULL DEFAULT now()`(Hibernate가 JVM 시각으로 채우지만 DB 기본값은 SQL로 직접 넣는 시드·테스트를 위한 안전망). 이름 규칙은 V1의 `uk_<테이블>_<열>`을 따르고 CHECK는 `ck_`, 인덱스는 `idx_`, 부분 유니크는 `ux_`를 붙였다. 컬럼 길이(`name VARCHAR(100)` 등)는 ERD에 없어 제안한 값이다.

## 10. 진행 순서 (사용자는 이 절만 보면 된다)

**Claude가 한 일(2026-10-13, 완료)과 사용자가 할 일을 나눴다.** 코드도 마이그레이션 파일도 만들지 않는 문서 작업이다.

### 10.1 Claude가 이미 끝낸 것

| 한 일 | 결과 |
|---|---|
| `docs/ERD.md` v0.3 → v0.4 (8절 ①~⑬ 적용) | 연결도 5개의 엔티티 짝이 맞고(열기·닫기 수 같음), `effective_to`·`v0.3`이 ERD에 남아 있지 않다 |
| 문서끼리의 불일치를 계약 기준으로 수정 (13절) | PRD FR-08·FR-16·SCR-07, TECH 2·3절, ROADMAP 045·046·055·056·059·060, `openapi.yaml` description 4곳 |
| D1~D9 확정 반영 | 6절 |

`ERD.md`가 새로 갖게 된 것: `mapping_state`(제공처 매핑 상태), `last_verified_at`(요금 확인 일시), `plan_name`·`UNKNOWN`(구독 정보), `activated_at`·`data_as_of`(플랜), `availability_change.job_execution_id` 인덱스, `CHECK` 제약 설명, "API 필드와 컬럼 대응" 소절.

### 10.2 사용자가 할 일

| 순서 | 할 일 | 확인할 것 | 막히면 |
|---|---|---|---|
| ① | 문서 워크트리에서 변경을 읽는다: `git diff docs/ERD.md` (또는 VS Code의 변경 보기). 함께 바뀐 문서는 `git diff docs/PRD.md docs/TECH.md docs/ROADMAP.md docs/api/openapi.yaml`로 ERD와 상태·필드가 맞는지 본다(계약 파일의 구문 오류는 CI의 계약 대조 테스트가 잡는다) | 8절 표(①~⑬)와 같은 곳이 바뀌었는지. 특히 **④ tmdb_provider 표, ⑦ product_price 표, ⑧ user_subscription 표, ⑨ plan 표** | 이해 안 되는 줄은 8절의 해당 블록과 4.x절(이유)을 본다 |
| ② | 연결도(mermaid)가 오류 없이 그려지는지 눈으로 본다 (VS Code 마크다운 미리보기 또는 GitHub) | 연결도 A~D의 표시가 깨지지 않는다 | 깨진 연결도 번호를 알려 준다 |
| ③ | (선택) 9.2절 SQL이 정말 도는지 확인하고 싶으면 11.1절을 따른다 | 기대한 곳에서만 실패한다 | 건너뛰어도 된다. 안내서 작성 때 이미 실행했다 |
| ④ | 바꾸고 싶은 문구·결정이 있으면 알려 준다 | 6절 D1~D9는 추천대로 확정이다 | — |
| ⑤ | PR을 요청한다 (`gh pr create --base develop`) | `backend-ci`·`frontend-ci` 통과 (문서 PR도 필수 검사) | CI 실패 내용을 알려 준다 |
| ⑥ | **병합한 뒤** ROADMAP Task 032 `기록:`과 쉬림프 완료 처리 | 15절 틀 | Claude가 채운다 |

### 10.3 이 PR이 하는 일의 범위
이 문서 PR은 안내서(`TASK032_GUIDE.md`), `ERD.md`, 그리고 위 문서 수정을 함께 담는다. Task 033 이후 마이그레이션은 별도 Task다.

## 11. 검증 방법

### 11.1 SQL 스케치를 임시 DB에서 실행해 보기 (이 안내서 작성자가 한 방법)

개발용 compose DB(`ottnavi-postgres-1`)는 쓰지 않는다(Flyway 기록과 데이터가 있다). 임시 컨테이너를 따로 띄우고 지운다. PowerShell 5에서 한글 파일을 파이프로 넘기면 깨질 수 있어 `docker cp`로 파일을 넘긴다.

```powershell
$work = Join-Path $env:TEMP 't032'; New-Item -ItemType Directory -Force $work | Out-Null
# 9.2절 SQL을 파일 4개(10_v2.sql, 20_v3.sql, 30_v5.sql, 40_v6.sql)로, 부록 A의 뼈대·점검 스크립트를 00_stubs.sql, 50_checks.sql로 $work에 저장한다(UTF-8)

docker run -d --rm --name t032-pg -e POSTGRES_PASSWORD=x -e POSTGRES_DB=t postgres:17.6-alpine
Start-Sleep -Seconds 5; docker exec t032-pg pg_isready -U postgres -d t

docker cp backend\src\main\resources\db\migration\V1__create_ott_service.sql t032-pg:/tmp/v1.sql
docker exec t032-pg psql -U postgres -d t -v ON_ERROR_STOP=1 -q -f /tmp/v1.sql          # 실제 V1 위에 올린다
foreach ($f in '00_stubs','10_v2','20_v3','30_v5','40_v6') {
    docker cp "$work\$f.sql" "t032-pg:/tmp/$f.sql"
    docker exec t032-pg psql -U postgres -d t -v ON_ERROR_STOP=1 -q -f "/tmp/$f.sql"   # 각각 오류 없이 끝나야 한다
}
docker cp "$work\50_checks.sql" t032-pg:/tmp/50.sql
docker exec t032-pg psql -U postgres -d t -q -f /tmp/50.sql                             # 출력의 "기대" 문구와 결과를 대조한다

docker rm -f t032-pg                                                                    # 반드시 지운다
```

`50_checks.sql`은 일부러 실패하는 문장이 있어 `ON_ERROR_STOP`을 쓰지 않는다. 출력에서 "기대: 실패" 아래에만 `ERROR:`가 나오는지 본다.

### 11.2 Task 033·034의 Testcontainers 테스트 스케치 (아직 실행하지 않음)

V2~V6가 만들어진 **뒤에** 쓰는 테스트다(Task 033·034 범위). 이 안내서는 어떤 케이스를 고정해야 하는지만 보인다. 기존 통합 테스트와 같은 스타일(`IntegrationTestSupport` 상속, 탭, 한글 메서드명, AssertJ)이고 H2는 쓰지 않는다. 컴파일해 보지 않은 스케치다.

```java
package com.ottnavi.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 032 보정(V2·V3)이 DB 제약으로 지켜지는지 고정한다. 시드는 지우지 않고 각 테스트가 만든 행만 지운다 */
class CatalogSchemaConstraintTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 제공처_시드_8행은_모두_MAPPED이고_서비스에_연결된다() {
		Integer mapped = jdbc.queryForObject(
				"SELECT count(*) FROM tmdb_provider WHERE mapping_state = 'MAPPED' AND ott_service_id IS NOT NULL", Integer.class);
		assertThat(mapped).isEqualTo(8);
	}

	@Test
	void MAPPED인데_서비스가_없으면_거부된다() {
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO tmdb_provider (provider_id, tmdb_name, mapping_state, first_seen_at) VALUES (999001, 'x', 'MAPPED', now())"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void IGNORED_제공처는_서비스_없이_저장된다() {
		jdbc.update("INSERT INTO tmdb_provider (provider_id, tmdb_name, mapping_state, first_seen_at) VALUES (999002, 'x', 'IGNORED', now())");
		try {
			String state = jdbc.queryForObject("SELECT mapping_state FROM tmdb_provider WHERE provider_id = 999002", String.class);
			assertThat(state).isEqualTo("IGNORED");
		} finally {
			jdbc.update("DELETE FROM tmdb_provider WHERE provider_id = 999002");
		}
	}

	@Test
	void 작품_구분은_대문자_MOVIE_TV만_저장된다() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO title (media_type, tmdb_id, title, refresh_tier, fetched_at) VALUES ('movie', 1, 'x', 'WEEKLY', now())"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 요금_테이블에는_종료일_열이_없고_마지막_확인_일시가_있다() {
		List<String> columns = jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'product_price'", String.class);
		assertThat(columns).contains("last_verified_at").doesNotContain("effective_to");
	}

	@Test
	void 같은_상품에_같은_적용_시작일은_거부된다() {
		Long productId = jdbc.queryForObject(
				"INSERT INTO subscription_product (name, product_type) VALUES ('테스트 상품', 'SINGLE') RETURNING id", Long.class);
		try {
			jdbc.update("INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at) VALUES (?, 1000, DATE '2026-10-01', now())", productId);
			assertThatThrownBy(() -> jdbc.update(
					"INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at) VALUES (?, 2000, DATE '2026-10-01', now())", productId))
					.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbc.update("DELETE FROM product_price WHERE product_id = ?", productId);
			jdbc.update("DELETE FROM subscription_product WHERE id = ?", productId);
		}
	}

	@Test
	void 변경_이력에는_실행_ID_인덱스가_있다() {
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM pg_indexes WHERE tablename = 'availability_change' AND indexname = 'idx_availability_change_job_execution'", Integer.class);
		assertThat(count).isEqualTo(1);
	}
}
```

(`title`의 `NOT NULL` 열 이름은 Task 033이 정한 대로 맞춘다. 위 INSERT는 스케치다.)

Task 034에는 `UserPlanSchemaConstraintTest`로 다음 케이스를 고정한다. 부록 A의 점검 스크립트가 이미 같은 일을 SQL로 해 본 것이니 그대로 옮기면 된다.

| 케이스 | 기대 |
|---|---|
| `NOT_SUBSCRIBED` 상태 저장 | 거부 |
| `SUBSCRIBED`인데 요금제 이름 또는 결제일 없음 / `FREE`·`UNKNOWN`인데 결제일 있음 | 거부 |
| 같은 사용자·서비스 구독 행 두 개 | 거부 |
| 같은 사용자의 같은 유형 DRAFT 두 개 / 다른 유형 DRAFT 세 개 | 거부 / 허용 |
| ACTIVE 두 개 | 거부 |
| DRAFT인데 `activated_at` 있음 / ACTIVE인데 `activated_at` 없음 | 각각 거부 |
| 기존 ACTIVE를 ARCHIVED로 바꾼 뒤 DRAFT를 ACTIVE(+`activated_at`)로 올림 | 성공 |
| `plan_assignment.minutes = 0` | 거부 |
| `users` 삭제 | 구독·플랜·찜 0행, `calc_run.plan_id`는 NULL(ROADMAP Task 034 기존 항목) |

### 11.3 계약 대조 체크리스트 (사람이 하는 마지막 점검)

ERD.md 수정을 마친 뒤, 계약의 각 응답 스키마 필드가 ERD의 컬럼이거나 5.1절의 "계산" 항목에 있는지 훑는다. 빠진 필드가 하나라도 있으면 5.1에 없는 파생이라는 뜻이므로 사용자에게 올린다. 이 안내서 작성 때는 31개 operation의 요청·응답 스키마를 모두 확인했고, 5.1 표에 없는 필드는 컬럼과 1:1이거나 인증·오류 스키마(`AccessToken`, `ProblemDetail`)였다.

## 12. 흔한 함정

| # | 함정 | 대응 |
|---|---|---|
| 1 | **병합된 `V1`을 고치고 싶어진다.** 이번 보정에서는 V1을 건드릴 항목이 없다. 앞으로 `ott_service`를 바꿀 일이 생기면 새 버전을 추가한다 | Flyway는 실행 당시 파일 지문과 지금 파일 지문이 다르면 기동을 막는다(`c7`) |
| 2 | **V2~V7이 병합된 뒤에 이 안내서의 SQL을 고치면 안 된다.** 병합 전인 지금만 본문에 직접 넣을 수 있다 | Task 033·034 PR이 병합되기 전에 ERD와 SQL을 확정한다 |
| 3 | **엔티티 타입이 컬럼 타입과 달라 `ddl-auto=validate`가 기동을 막는다.** `INT` 컬럼에 `Long`, `SMALLINT`에 `Integer` 등. 계약이 `int64`라서 엔티티까지 `Long`으로 쓰고 싶어진다 | 엔티티는 컬럼 타입에 맞추고(`Integer`/`Short`), 계약과 맞추는 변환은 DTO에서 한다(5.2). 알려진 동작이지만 이 저장소에서 실행해 확인하지는 않았다 |
| 4 | **`TIMESTAMPTZ`에 `Instant`를 매핑할 때 `@JdbcTypeCode`를 빠뜨린다.** Hibernate 기본은 시간대 없는 `TIMESTAMP`로 본다(TECH 6절, `BaseTimeEntity` 주석) | `last_verified_at`·`watched_at`·`activated_at`·`data_as_of`·`first_seen_at`·`changed_at`·`fetched_at` 엔티티 필드마다 `@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)` |
| 5 | **Spring Batch의 시각은 시간대가 없다.** `END_TIME`을 `Instant`로 바꿀 때 한국 시간대를 박아 넣으면, JVM 기본 시간대가 UTC인 환경에서 9시간이 어긋난다 | `ZoneId.systemDefault()`로 바꾼다(5.3). JVM 시간대가 환경마다 같은지 확인한다 |
| 6 | **`DEFAULT now()`가 있다고 JPA가 그 값을 쓰지 않는다.** Hibernate는 `@DynamicInsert`를 쓰지 않는 한 null인 필드도 INSERT에 NULL로 넣는다(알려진 동작, 이 저장소에서 실행해 확인하지는 않음). `last_verified_at`처럼 NOT NULL인 열에서 엔티티가 값을 안 채우면 DB 기본값이 아니라 제약 오류가 난다 | 그래서 `last_verified_at`·`activated_at`은 DEFAULT를 두지 않고 도메인 메서드가 채운다. `created_at`/`updated_at`은 `BaseTimeEntity`가 채운다 |
| 7 | **"오늘"을 DB에 맡긴다.** `CURRENT_DATE`는 DB 서버의 시간대 날짜라 한국 새벽에 하루 어긋난다 | 날짜는 자바 `Clock`(한국)에서 만들어 파라미터로 넘긴다. 테스트에서 `Clock`을 고정할 수 있다 |
| 8 | **결제일 31일과 30일인 달.** 사용자 결제일 기준 가격 조회(PRD 40번)에서 4월 31일은 없다. 문서에 규칙이 없다 | 말일로 내려 조회하는 것이 자연스럽지만 정해지지 않았다. Task 063에서 사용자에게 올린다 |
| 9 | **`plan.status` 변경 시 `activated_at`을 따로 갱신한다.** 두 UPDATE로 나누면 중간에 CHECK가 막는다. 또 부분 유니크 인덱스(ACTIVE 하나) 때문에 기존 ACTIVE를 ARCHIVED로 바꾸고 **flush한 뒤** 새 ACTIVE를 올려야 한다(TECH 2절) | 한 엔티티 메서드가 `status`와 `activated_at`을 같이 바꾼다 |
| 10 | **mermaid 연결도 문법.** 속성 줄 끝의 설명은 큰따옴표 하나로 감싸고 그 안에 큰따옴표를 넣지 않는다. 컬럼 이름에 하이픈을 쓰지 않는다 | 수정 후 미리보기로 확인(10.2절 ②) |
| 11 | **값 목록 CHECK와 자바 enum이 어긋난다.** 자바 enum에 값을 더하고 마이그레이션을 빠뜨리면 첫 저장에서 제약 오류가 난다(D3 (c)를 고른 이유이기도 하다) | enum 변경 PR에서 마이그레이션 여부를 체크리스트로 확인한다. 이미 병합된 파일은 새 버전으로 `ALTER`한다 |
| 12 | **`recover`를 STOPPED 확인 없이 호출한다**(4.11) | 먼저 서버의 STOPPED 판정을 통과한 실행에만 호출한다. 아니면 `BATCH_NOT_STOPPED` |
| 13 | **수집이 `tmdb_provider`를 덮어쓴다**(4.3) | 새 제공처만 INSERT |
| 14 | **페이지 정렬이 전순서가 아니다**(4.10) | `t.id`, `c.id`를 정렬 끝에 붙인다 |

## 13. 사용자에게 올릴 불일치

문서끼리 어긋나는 곳이다. **사용자 지시(2026-10-12)에 따라 문서끼리의 어긋남은 계약(`docs/api/openapi.yaml`)에 맞춰 고쳤다.** 마지막 칸이 처리 결과다. `ERD.md`는 사용자가 Task 032에서 직접 고치므로 이 작업에서 수정하지 않았다.

| # | 어긋나는 곳 | 근거 | 제안 | 처리 |
|---|---|---|---|---|
| U1 | **구독 상태가 문서마다 다르다.** PRD FR-08과 "사용 흐름"(49행)은 미구독/구독 중/무료 이용 3상태다. 계약 `ServiceSubscriptionStatus`와 ROADMAP Task 031 진행 기록은 4상태(UNKNOWN을 저장)다. ERD도 아직 `SUBSCRIBED / FREE` | PRD FR-08, 계약, ROADMAP 733행 | 계약이 확정이므로 ERD(이 Task)와 PRD FR-08을 4상태로 고친다. PRD 수정은 이 Task 범위 밖이므로 사용자 결정 | **수정함**: PRD 49행·FR-08·SCR-07을 4상태로. ERD는 032에서 |
| U2 | **ROADMAP Task 055의 API 설명이 계약과 다르다.** 1010행은 `GET/PUT /api/me/subscriptions`, `productId` 입력, SUBSCRIBED/FREE만이다. 계약은 구독을 `/me/settings`에 통합했고 `productId`가 없으며 `planName`·`UNKNOWN`이 있다 | ROADMAP 1010행, 계약 | 계약이 확정이므로 ROADMAP 055를 고친다(D4와 연결) | **수정함**: ROADMAP Task 055 |
| U3 | **ROADMAP Task 046이 `PUT /api/admin/providers/{providerId}`**(923행), 계약은 `PATCH`다 | ROADMAP 923행, 계약 | ROADMAP을 계약에 맞춘다 | **수정함**: ROADMAP Task 046 |
| U4 | **ROADMAP Task 056이 `/api/wishlist-items`**(1018행), 계약은 `/api/me/wishlist-items`다 | ROADMAP 1018행, 계약, ROADMAP 732행("옛 경로 표기를 같이 고쳤다"고 적혔으나 이 줄은 남음) | ROADMAP 056을 고친다 | **수정함**: ROADMAP Task 056 |
| U5 | **ERD 연결도와 표가 열 목록이 다르다.** `plan_assignment`의 `ott_service_id`(표에만), `plan_month.month`(표에만), `availability`의 `unknown_reason`·`checked_at`(표에만) 등 | ERD 346~351행·367행 외 | 8절 ⑨에서 `plan_assignment`·`plan_month`는 맞췄다. 나머지는 사용자 판단 | **032에서 같이 정리**(ERD.md 미수정) |
| U6 | **알림 기본값.** PRD FR-16은 "끄면 알림이 생성되지 않는다"만 쓰고 기본값이 없다. ERD `notification_enabled`도 기본값 없음 | PRD FR-16, ERD | D6. 2단계 알림 설계에서 확정해도 된다 | **수정함**: PRD FR-16에 기본 꺼짐(D6) |
| U7 | **ERD 본문이 옛 규칙을 말한다.** `tmdb_provider` "관심 없는 제공처는 NULL"(127행)은 계약의 `IGNORED`로 대체돼야 하고, `product_price` "기간이 겹치지 않게 서비스에서 검증"(236행)은 종료일 제거 후 의미가 바뀐다 | ERD, 계약 | 8절 ④⑦에서 반영 | **032에서 같이 정리**(ERD.md 미수정, 8절 ④⑦) |
| U8 | **TECH 3절의 복구 API 경로**가 `POST /api/admin/collect/{executionId}/recover (제안)`이다. 계약은 `/api/admin/collect/executions/{executionId}/recover` | TECH 87행, 계약 | 계약이 확정이므로 TECH의 괄호 안 제안 경로를 고친다 | **수정함**: TECH 3절 복구 경로 |
| U9 | **계약 YAML의 description이 "ERD에 …를 추가해야 한다(Task 032)"**라고 말한다(`ServiceSubscriptionStatus`, `UserSubscription.planName`, `ServicePrice.lastVerifiedAt`, `AdminProductPrice.lastVerifiedAt`). ERD를 고치면 낡은 문장이 된다 | 계약 1406행·2064행·2230행·2808행 | 의미를 바꾸지 않는 문구 정리라 급하지 않다. 하려면 계약 수정이므로 프론트 생성물(`npm run api:generate`) 주석에 영향이 있다. 033 PR에서 같이 정리하거나 그대로 둔다 | **수정함**: 계약 description 4곳을 "Task 032에서 반영 예정"으로(구조·example 불변) |
| U10 | **"데이터 기준일"의 출처가 ERD에 없다.** `TitleSearchPage`·`TitleDetail`·`WishlistItemList`의 `dataAsOf`(date)가 어느 값인지 문서가 정하지 않았다. TECH 2절·ROADMAP 063은 `plan.data_as_of`만 "수집 Job 마지막 COMPLETED 종료 시각"으로 정의한다 | 계약, TECH, ROADMAP 1083행 | D9로 아래에 올린다 | **수정함**: PRD 153행, TECH 2절에 한 줄(D9) |
| U11 | **계약 `recover` 응답은 `endedAt: null`이고 Spring Batch 6.0.5 `recover`는 `END_TIME`을 채운다**(소스 확인) | 계약 914행·3106행·3181~3189행, 3.2절 | 문서끼리의 불일치는 아니고 구현에서 만날 불일치다. 4.11 ③의 처리가 필요하다. 계약을 바꿀지(복구 시각을 `endedAt`으로 내림) 구현을 계약에 맞출지가 쟁점이었고 구현을 계약에 맞추기로 확정했다(Task 045) | **확정**: 계약(null)이 기준. TECH 3절·ROADMAP 045에 반영 |

**D9 (확정, 2026-10-12 추천대로: (a) 마지막 `COMPLETED` 수집 실행의 종료 시각). 작품 검색·상세·찜 목록의 `dataAsOf`는 무엇인가.**

| 선택지 | 내용 | 평가 |
|---|---|---|
| (a) 마지막 `COMPLETED` 수집 실행의 종료 시각을 한국 날짜로 | TECH 2절·ROADMAP 063의 `plan.data_as_of`와 같은 정의 | **확정.** 하나의 정의로 통일된다. 비용: `BATCH_JOB_EXECUTION` 조회(캐시 가능) |
| (b) 응답에 포함된 `availability.checked_at`의 최댓값 | 작품마다 다를 수 있는 날짜 | 화면이 "전체 기준일"을 보여 주려는 것과 맞지 않을 수 있다 |
| (c) 아무 값도 저장하지 않고 항상 오늘 | 거짓 정보가 될 수 있음 | 비추천 |

### 13.1 이번 지시로 고친 문서와 남긴 것

수정 위치와 원문→수정문은 작업 보고서의 표에 있다. 문서 수정: `PRD.md`(49·93·101·123 근처·153·209행), `TECH.md`(65·87행), `ROADMAP.md`(Task 045·046·055·056·059·060 줄), `openapi.yaml`(description 4곳). 아래는 **고치지 않고 Task 032에서 같이 정리할 항목**이다.

- ERD 연결도와 표의 열 목록 불일치: `plan_assignment.ott_service_id`, `plan_month.month`, `availability`의 `unknown_reason`·`checked_at`, 그 밖에 연결도에서 빠진 열(U5)
- ERD 본문의 옛 규칙: `tmdb_provider` "관심 없는 제공처는 NULL", `product_price` "기간이 겹치지 않게 서비스에서 검증"(U7)
- 12항목 스키마 변경 전부(8절)

## 14. 확인하지 못한 것

| 항목 | 상태 |
|---|---|
| Jackson 3이 record `isActive` 컴포넌트를 JSON 키 `isActive`로 내보내는지 | 확인 못 함(4.4). 응답 본문 단위 테스트 필요 |
| springdoc이 `@JsonValue` enum을 소문자 스키마로 쓰는지, 잘못된 `mediaType`이 400 `VALIDATION_FAILED`가 되는지 | 확인 못 함(4.12) |
| `Instant`가 JSON에서 `Z`로 나와도 프론트가 한국 날짜로 바꾸는 코드가 정상인지 | 확인 못 함(4.2). 프론트 담당 확인 사항 |
| Spring Batch `recover` 후의 실제 `END_TIME`·컨텍스트 값, `batch.recovered`를 읽는 정확한 API | 소스만 읽음. 실행 확인 필요(Task 045) |
| JVM 기본 시간대가 로컬과 Cloudtype에서 같은지 | 확인 못 함(5.3) |
| Supabase와 Testcontainers(alpine PostgreSQL)의 collation이 같아 한글 제목 정렬이 같은지 | 확인 못 함(4.10) |
| Hibernate 7에서 `SMALLINT`↔`Integer`, `INT`↔`Long` 불일치로 validate가 실제로 실패하는 메시지 | 알려진 동작으로 적었고 이 저장소에서 실행하지 않음(함정 3) |
| `FormatterRegistry.addConverter(Class, Class, Converter)`의 Spring 7 시그니처, 잠금 쿼리가 `FOR UPDATE`인지 `FOR NO KEY UPDATE`인지 | 확인 못 함(4.12, 4.9) |
| Supabase에서 마이그레이션으로 `pg_trgm` 같은 확장을 켤 수 있는지 | 확인 못 함(5.4 C, 7절) |
| 단건 수집(Task 050)이 `availability_change`를 남기는지 | 문서에 없음(D8) |
| `availability`에 행이 없는 서비스를 응답에서 무엇으로 채우는지, 드라마 작품 단위 집계에서 AVAILABLE 외 경우의 규칙 | 계약에 없음(5.1) |
| 로컬·통합 테스트용 상품·가격 시드를 어떻게 두는지 | 문서에 없음(5.4 B) |
| 시작일만 고친 월 구독요금의 `last_verified_at` 갱신 여부 | 계약에 없음(4.2) |
| 결제일 31일과 30일인 달의 가격 조회 규칙 | 문서에 없음(함정 8) |
| `change_summary`의 타입, 컬럼 길이(`name VARCHAR(100)` 등), 변경과 무관한 열의 `NOT NULL` 범위 | ERD에 없음. 스케치의 값은 제안(033·034에서 확정) |
| 페이지네이션 쿼리(`listCollectExecutionChanges`)의 실제 소요 시간(수만 건 규모) | 측정하지 않음. 임시 DB에서는 인덱스 사용과 결과 정렬만 확인(4.10) |
| 계약 대조 범위 | 31개 operation의 요청·응답 스키마를 읽고 대조했다. `x-planned` operation의 서버 구현은 아직 없어 실행으로 확인한 것은 없다 |

## 15. 체크리스트와 ROADMAP 기록 틀

### 15.1 체크리스트

- [ ] ① `develop`에서 `feature/docs-erd-task032` 생성, 브랜치 확인
- [x] ② D1~D9 확정(2026-10-12, 추천대로)
- [ ] ③ A 영역(8절 ②~⑥) 반영, `mapping_state`가 연결도·표 양쪽에
- [ ] ④ B 영역(⑦) 반영, `effective_to`가 설명 한 곳뿐
- [ ] ⑤ C 영역(⑧) 반영, `UNKNOWN`·`plan_name`·온보딩 설명
- [ ] ⑥ D 영역(⑨) 반영, `activated_at`·`data_as_of` 타입
- [ ] ⑦ 머리말 v0.4, 대응 절(선택), 인덱스 절, E 영역 문구
- [ ] ⑧ (권장) 임시 DB에서 9.2절 SQL 재실행
- [ ] ⑨ 13절 불일치 중 고칠 것을 정함(PRD FR-08, ROADMAP 046·055·056, TECH 3절)
- [ ] ⑩ ROADMAP Task 032 `기록:` 작성, ⬜ → ✅
- [ ] ⑪ PR(사용자 요청 시)

### 15.2 ROADMAP `기록:` 틀 (완료 때 채운다)

```
- 기록: {날짜} 완료(문서 PR #{번호}). ERD v0.3 → v0.4. 반영: ① product_price.effective_to 제거(종료일 파생) ② product_price.last_verified_at(TIMESTAMPTZ) ③ tmdb_provider.mapping_state(UNMAPPED/MAPPED/IGNORED)+CHECK ④ user_subscription 상태 UNKNOWN 추가·plan_name(30)·구독 중 전용 열 CHECK ⑤ plan.activated_at 추가·data_as_of TIMESTAMPTZ 명시 ⑥ availability_change(job_execution_id) 인덱스·NULL 허용 ⑦ title.media_type CHECK(변환은 도메인 enum) ⑧ plan_assignment.minutes > 0 CHECK. 변경 없음: active↔isActive(이름 대응), isOnboardingDone(user_setting 행 존재), watchMin(조각은 계산), 찜 개수(watched_at IS NULL 집계, users 행 잠금), 수집 STOPPED(저장하지 않는 파생 상태), R11-18·R11-19. 결정(모두 추천대로 확정): D1 activated_at 추가, D2 data_as_of TIMESTAMPTZ, D3 CHECK 전부(열끼리 규칙+enum 값 목록+변하지 않는 하한), D4 product_id MVP NULL, D5 billing_day·plan_name DB 기본값 없음, D6 알림 기본 꺼짐, D7 MediaType은 catalog.domain, D8 job_execution_id NULL 허용, D9 dataAsOf는 마지막 COMPLETED 수집 종료 시각. 넘길 내용: Task 033(V2·V3 반영), 034(V5·V6 반영), 045(recover 후 endedAt null 처리), 055, 056, 061, 063. 확인하지 못한 것: 안내서 14절.
```

## 부록 A. 임시 DB 검증 스크립트 (전체)

11.1절 순서로 실행한다. 아래는 9.2절 SQL 앞에 FK 대상만 만드는 뼈대와, 기대한 곳에서만 실패하는지 보는 점검이다.

**`00_stubs.sql`**

```sql
-- 검증용 부모 테이블 뼈대(실제 V2~V6 본문이 아니라 FK 대상만 만든다)
CREATE TABLE users (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, email VARCHAR(255) NOT NULL);
CREATE TABLE title (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, media_type VARCHAR(10) NOT NULL, tmdb_id INT NOT NULL, title VARCHAR(300) NOT NULL);
CREATE TABLE watch_unit (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, title_id BIGINT NOT NULL REFERENCES title (id), season_number INT);
CREATE TABLE plan_item (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY);
```

**`50_checks.sql`**

```sql
\set ON_ERROR_STOP off
\echo '--- 기대: 성공 ---'
INSERT INTO users (email) VALUES ('a@x'), ('b@x');
INSERT INTO tmdb_provider (provider_id, tmdb_name, first_seen_at) VALUES (119, 'Amazon Prime Video', now());               -- UNMAPPED 기본값
UPDATE tmdb_provider SET mapping_state = 'IGNORED' WHERE provider_id = 119;                                                      -- IGNORED, 서비스 없음
SELECT provider_id, mapping_state, ott_service_id, tier FROM tmdb_provider ORDER BY provider_id;
\echo '--- 기대: 실패 3건(MAPPED인데 서비스 없음 / IGNORED인데 서비스 있음 / 없는 상태 값) ---'
UPDATE tmdb_provider SET mapping_state = 'MAPPED' WHERE provider_id = 119;
UPDATE tmdb_provider SET ott_service_id = 1 WHERE provider_id = 119;
UPDATE tmdb_provider SET mapping_state = 'DONE' WHERE provider_id = 119;

\echo '--- user_subscription 기대: 성공 3건 ---'
INSERT INTO user_subscription (user_id, ott_service_id, status, plan_name, billing_day) VALUES (1, 1, 'SUBSCRIBED', '스탠다드', 15);
INSERT INTO user_subscription (user_id, ott_service_id, status) VALUES (1, 2, 'FREE');
INSERT INTO user_subscription (user_id, ott_service_id, status) VALUES (1, 3, 'UNKNOWN');
\echo '--- 기대: 실패 4건(NOT_SUBSCRIBED 저장 / SUBSCRIBED인데 이름 없음 / FREE인데 결제일 있음 / 서비스 중복) ---'
INSERT INTO user_subscription (user_id, ott_service_id, status) VALUES (1, 4, 'NOT_SUBSCRIBED');
INSERT INTO user_subscription (user_id, ott_service_id, status, billing_day) VALUES (1, 4, 'SUBSCRIBED', 1);
INSERT INTO user_subscription (user_id, ott_service_id, status, billing_day) VALUES (1, 4, 'FREE', 1);
INSERT INTO user_subscription (user_id, ott_service_id, status) VALUES (1, 3, 'FREE');

\echo '--- product_price 기대: 성공 2건 후 실패 2건(같은 시작일 / 음수 금액) ---'
INSERT INTO subscription_product (name, product_type) VALUES ('넷플릭스 스탠다드', 'SINGLE');
INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at) VALUES (1, 12000, '2025-09-07', now());
INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at) VALUES (1, 13500, '2026-10-02', now());
INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at) VALUES (1, 14500, '2026-10-02', now());
INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at) VALUES (1, -1, '2026-11-11', now());
\echo '--- 가격 조회: 결제일 2026-10-01이면 12000, 2026-10-15이면 13500 ---'
SELECT monthly_price FROM product_price WHERE product_id = 1 AND effective_from <= DATE '2026-10-01' ORDER BY effective_from DESC LIMIT 1;
SELECT monthly_price FROM product_price WHERE product_id = 1 AND effective_from <= DATE '2026-10-15' ORDER BY effective_from DESC LIMIT 1;
\echo '--- 종료일 계산(LEAD): 마지막 줄은 NULL(계속) ---'
SELECT effective_from, LEAD(effective_from) OVER (PARTITION BY product_id ORDER BY effective_from) - 1 AS effective_to FROM product_price ORDER BY effective_from;

\echo '--- plan 기대: 성공(DRAFT 3유형, ACTIVE 1) ---'
INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of) VALUES
 (1, 'DRAFT', 'RECOMMENDED', '2026-10-01', 20000, 900, 'EXACT', 1, 1, 5, 13500, 137400, now()),
 (1, 'DRAFT', 'SAVER',       '2026-10-01', 20000, 900, 'EXACT', 1, 1, 5, 13500, 137400, now()),
 (1, 'DRAFT', 'SIMPLE',      '2026-10-01', 20000, 900, 'EXACT', 1, 1, 5, 13500, 137400, now());
INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of, activated_at) VALUES
 (1, 'ACTIVE', 'RECOMMENDED', '2026-09-01', 20000, 900, 'EXACT', 1, 1, 5, 13500, 137400, now(), now());
\echo '--- 기대: 실패 4건(같은 유형 DRAFT 2개 / ACTIVE 2개 / DRAFT인데 activated_at / ACTIVE인데 activated_at 없음) ---'
INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of) VALUES
 (1, 'DRAFT', 'SAVER', '2026-10-01', 1, 1, 'EXACT', 0, 0, 0, 0, 0, now());
INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of, activated_at) VALUES
 (1, 'ACTIVE', 'SAVER', '2026-10-01', 1, 1, 'EXACT', 0, 0, 0, 0, 0, now(), now());
INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of, activated_at) VALUES
 (2, 'DRAFT', 'SAVER', '2026-10-01', 1, 1, 'EXACT', 0, 0, 0, 0, 0, now(), now());
INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of) VALUES
 (2, 'ACTIVE', 'SAVER', '2026-10-01', 1, 1, 'EXACT', 0, 0, 0, 0, 0, now());
\echo '--- 기대: ACTIVE를 ARCHIVED로 바꾼 뒤 새 ACTIVE 가능(activated_at 유지) ---'
UPDATE plan SET status = 'ARCHIVED' WHERE user_id = 1 AND status = 'ACTIVE';
UPDATE plan SET status = 'ACTIVE', activated_at = now() WHERE user_id = 1 AND plan_type = 'SAVER' AND status = 'DRAFT';
SELECT id, status, plan_type, activated_at IS NOT NULL AS has_activated FROM plan WHERE user_id = 1 ORDER BY id;

\echo '--- plan_assignment: 조각 번호 계산 ---'
INSERT INTO plan_item (id) VALUES (DEFAULT), (DEFAULT);
INSERT INTO plan_month (plan_id, month_index, month, confirmed, cost) VALUES (1,0,'2026-10-01',TRUE,0),(1,1,'2026-11-01',FALSE,13500),(1,2,'2026-12-01',FALSE,0);
INSERT INTO plan_assignment (plan_item_id, plan_month_id, ott_service_id, minutes) VALUES (1,1,1,267),(1,2,1,158),(2,2,1,270);
SELECT a.plan_item_id, m.month_index, a.minutes AS watch_min,
       ROW_NUMBER() OVER (PARTITION BY a.plan_item_id ORDER BY m.month_index) AS segment_index,
       COUNT(*)     OVER (PARTITION BY a.plan_item_id) AS segment_count
FROM plan_assignment a JOIN plan_month m ON m.id = a.plan_month_id
WHERE m.plan_id = 1 ORDER BY m.month_index, a.plan_item_id;
\echo '--- 기대: 실패 2건(0분 / 같은 달 같은 항목 중복) ---'
INSERT INTO plan_assignment (plan_item_id, plan_month_id, ott_service_id, minutes) VALUES (2,3,1,0);
INSERT INTO plan_assignment (plan_item_id, plan_month_id, ott_service_id, minutes) VALUES (1,1,1,10);

\echo '--- 찜 개수: 다 봤음 제외 ---'
INSERT INTO title (media_type, tmdb_id, title) VALUES ('MOVIE', 496243, '기생충'), ('TV', 93405, '오징어 게임');
INSERT INTO watch_unit (title_id, season_number) VALUES (1, NULL), (2, 1), (2, 2);
INSERT INTO wishlist_item (user_id, watch_unit_id, priority, watched_at) VALUES (1,1,'MUST', now()), (1,2,'WANT', NULL), (1,3,'MAYBE', NULL);
SELECT count(*) AS active_count FROM wishlist_item WHERE user_id = 1 AND watched_at IS NULL;
\echo '--- 기대: 실패 2건(media_type 소문자 / 같은 찜 중복) ---'
INSERT INTO title (media_type, tmdb_id, title) VALUES ('movie', 1, 'x');
INSERT INTO wishlist_item (user_id, watch_unit_id, priority) VALUES (1,2,'WANT');

\echo '--- availability_change 조회(제목순, 동률 방지 키 포함) ---'
INSERT INTO availability_change (watch_unit_id, ott_service_id, before_status, after_status, job_execution_id, changed_at) VALUES
 (1, 6, 'AVAILABLE', 'UNKNOWN', 5, now()), (3, 1, NULL, 'AVAILABLE', 5, now()), (2, 1, NULL, 'AVAILABLE', 5, now()), (2, 2, NULL, 'AVAILABLE', 4, now());
SELECT t.media_type, t.tmdb_id, t.title, wu.season_number, c.ott_service_id, c.before_status, c.after_status
FROM availability_change c JOIN watch_unit wu ON wu.id = c.watch_unit_id JOIN title t ON t.id = wu.title_id
WHERE c.job_execution_id = 5
ORDER BY t.title, t.id, wu.season_number, c.ott_service_id, c.id
LIMIT 20 OFFSET 0;
SELECT count(*) AS changed_count FROM availability_change WHERE job_execution_id = 5;
EXPLAIN (COSTS OFF) SELECT count(*) FROM availability_change WHERE job_execution_id = 5;

\echo '--- 탈퇴(users 삭제) 시 사용자 행 정리 ---'
DELETE FROM users WHERE id = 1;
SELECT (SELECT count(*) FROM user_subscription) AS sub, (SELECT count(*) FROM plan WHERE user_id = 1) AS plan, (SELECT count(*) FROM plan_month) AS pm, (SELECT count(*) FROM wishlist_item) AS wl;
```

기대 결과(작성자가 실행한 출력 요약): 제공처 9행(시드 8 + 119 IGNORED). `ERROR:`는 "기대: 실패" 블록에서만 나오고 블록별 개수는 제공처 3, 구독 4, 요금 2, 플랜 4, 배정 2, 작품·찜 2건이다(표준 오류와 표준 출력이 섞여 `ERROR:`가 다음 `\echo` 뒤에 찍히기도 한다). 가격 조회 12000/13500, 종료일 `2026-10-01`/NULL, 조각 번호 (item1: 1/2, 2/2)·(item2: 1/1), 찜 개수 2, 변경 조회 3행 순서 `기생충 → 오징어 게임 시즌1 → 시즌2`, `Bitmap Index Scan on idx_availability_change_job_execution`, 마지막 줄 `0 | 0 | 0 | 0`.
