# 오류 코드

> API 오류 응답(`application/problem+json`)의 `errorCode` 목록이다. 프론트의 `src/lib/errorMessages.ts`는 이 표와 일치해야 한다.
> 백엔드 `ErrorCode`(Task 023)도 이 표를 기준으로 만든다. 새 코드는 계약(`openapi.yaml`)과 같은 PR에서 이 표에 추가한다.
> "(제안)"은 TECH에서 제안만 된 값이며, 해당 기능을 구현하는 Task에서 확정한다.
> 백엔드 테스트 `ErrorCodeDocumentTest`가 "공통 코드" 표의 모든 코드가 `ErrorCode` enum에 있고, enum의 모든 코드가 "공통 코드" 또는 "TECH 신규 코드" 표에 같은 HTTP 상태(첫 숫자)로 있는지 검사한다(공통 ⊆ enum ⊆ 공통 ∪ TECH). TECH 표에만 있고 enum에 없는 코드는 아직 구현 전이라는 뜻이다. 표 형식(`| \`코드\` | 상태 | ...`)을 바꾸면 테스트도 함께 고친다.

## 공통 코드

| errorCode | HTTP 상태 | 의미 | 화면 메시지 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 값 검증 실패(본문 필드 검증, 쿼리·경로 파라미터 제약 위반·형식 오류·누락). 응답의 `fieldErrors`에 필드별 오류 목록 포함 | 입력한 내용을 다시 확인해 주세요. |
| `INVALID_REQUEST` | 400 (406·415 등 기타 4xx는 원래 상태 유지) | 필드를 특정할 수 없는 요청 오류(깨진 JSON 본문, 지원하지 않는 Content-Type 등). `fieldErrors` 없음 | 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| `UNAUTHORIZED` | 401 | 인증 필요 또는 Access Token 만료·무효. `POST /api/auth/refresh`에서는 `rt` 쿠키 없음·형식 오류·만료·재사용 감지(계열 폐기) | 로그인이 필요합니다. |
| `FORBIDDEN` | 403 | 권한 없음(관리자 전용 API 등), 또는 Vercel을 거치지 않은 직접 호출(오리진 비밀 헤더 불일치, Task 019), 또는 `POST /api/auth/refresh`·`/api/auth/logout`의 `Origin` 헤더가 `APP_FRONTEND_ORIGIN`과 다름(Task 054) | 접근 권한이 없습니다. |
| `RESOURCE_NOT_FOUND` | 404 | 요청한 경로(API)가 없음. 특정 데이터가 없는 경우는 `TITLE_NOT_FOUND`처럼 도메인 코드를 쓴다. 예외로 관리자 API(`/api/admin/**`)의 없는 데이터(상품·요금 이력·제공처·수집 실행)는 이 코드를 쓴다(Task 031, 아래 설명) | 요청한 페이지를 찾을 수 없습니다. |
| `METHOD_NOT_ALLOWED` | 405 | 경로는 있지만 HTTP 메서드를 지원하지 않음. `Allow` 헤더 포함 | 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| `TITLE_NOT_FOUND` | 404 | 요청한 작품(TMDB ID)을 찾을 수 없음 | 작품을 찾을 수 없습니다. |
| `RATE_LIMIT_EXCEEDED` | 429 | 비로그인 공개 API·인증 경로(`/api/auth/**`) 요청 제한 초과, 단건 수집 한도 초과·저장소 장애 시 단건 수집 거부. `Retry-After` 헤더(초) 포함 | 요청이 많습니다. 잠시 후 다시 시도해 주세요. |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. |
| `EXTERNAL_API_ERROR` | 502 | 외부 API(TMDB 등) 호출 실패 | 외부 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. |

`INVALID_REQUEST`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`는 Task 023에서 추가했다(없는 경로·잘못된 요청에도 `errorCode`가 오게 하는 V-API 완료 기준). 화면 메시지는 제안이며 프론트 `errorMessages.ts`(Task 025)에서 확정한다.

**관리자 API의 404 예외(Task 031, 2026-10-12 사용자 확정).** 관리자 화면은 운영자 한 명이 쓰고, 없는 데이터는 다른 탭에서 지웠거나 주소를 직접 고친 경우뿐이라 데이터 종류마다 코드를 나눌 이득이 적다. 그래서 관리자 API의 없는 데이터는 도메인 코드를 만들지 않고 `RESOURCE_NOT_FOUND`를 쓴다. 사용자 API(`/api/public/**`, `/api/me/**`)는 원칙대로 도메인 코드(`TITLE_NOT_FOUND`, `WISHLIST_ITEM_NOT_FOUND`, `SERVICE_PRICE_NOT_FOUND`, `PLAN_DRAFT_NOT_FOUND`)를 쓴다.

### `fieldErrors` 구조 (`VALIDATION_FAILED`)

계약의 `FieldError` 스키마(`field`, `message` 둘 다 필수)와 같다. 순서는 보장하지 않는다.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "요청 값이 올바르지 않습니다.",
  "instance": "/api/me/settings",
  "errorCode": "VALIDATION_FAILED",
  "fieldErrors": [
    { "field": "monthlyBudget", "message": "0 이상이어야 합니다." }
  ]
}
```

- 본문 필드 오류는 `field`가 필드 경로(예: `monthlyBudget`), 쿼리·경로 파라미터 오류는 파라미터명이다.
- 한 필드가 제약 두 개를 어기면 같은 `field`로 두 건이 온다.
- 객체 단위 검증 오류(필드가 아닌 오류)는 `field`가 요청 객체명이다.

## TECH 신규 코드

| errorCode | HTTP 상태 | 의미 | 화면 메시지 | 근거 |
|---|---|---|---|---|
| `PLAN_DRAFT_STALE` | 409 | 저장하려는 DRAFT의 데이터 기준일이 최신 수집 완료 시각보다 이름. 프론트가 받으면 자동으로 재계산을 요청한다 | 데이터가 갱신되어 플랜을 다시 계산하고 있습니다. | TECH 2절, PRD 5.4 |
| `PLAN_DRAFT_NOT_FOUND` | 404 | 저장하려는 DRAFT가 없거나 본인 것이 아님. 이미 저장돼 DRAFT가 아니게 됐거나 다시 계산해 지워진 경우 포함 | 계산된 플랜을 찾을 수 없습니다. 다시 계산해 주세요. | TECH 2절 |
| `BATCH_ALREADY_RUNNING` | 409 | 같은 날짜 또는 다른 날짜의 수집이 실행 중(전체 동시 실행 금지, 관리자 API) | 수집이 이미 실행 중입니다. 끝난 뒤 다시 시도해 주세요. | TECH 3절 (도출) |
| `BATCH_ALREADY_COMPLETED` | 409 | 같은 `targetDate`+`tier`의 수집 Job이 이미 완료됨(관리자 API) | 해당 날짜의 수집은 이미 완료되었습니다. | TECH 3절 (도출) |
| `BATCH_RECOVERY_REQUIRED` | 409 | 같은 `targetDate`+`tier`에 중단된(`STOPPED`) 실행이 있어 새로 실행하거나 재시작할 수 없음. 먼저 복구해야 한다(수집 실행·재시작 API) | 중단된 실행이 있어 새로 실행할 수 없습니다. 이력에서 [복구]를 먼저 실행하세요. | TECH 3절 "중단 Job 복구", Task 029·031 |
| `BATCH_NOT_STOPPED` | 409 | 복구하려는 실행이 중단됨(`STOPPED`)이 아님(복구 API 전용. 재시작 API는 실행 상태에 맞는 `BATCH_ALREADY_*`·`BATCH_RECOVERY_REQUIRED`를 쓴다) | 중단된 실행만 복구할 수 있습니다. | TECH 3절, Task 029·031 |
| `PRICE_START_DATE_NOT_AFTER_LAST` | 409 | 추가·수정하려는 월 구독요금 이력의 적용 시작일이 바로 앞 요금 이력의 시작일보다 늦지 않음(관리자 API) | 적용 시작일은 마지막 월 구독요금의 시작일보다 늦어야 합니다. | PRD FR-19 "기간이 겹치지 않는다", Task 029·031 |
| `PRICE_START_DATE_DUPLICATE` | 409 | 같은 상품에 같은 적용 시작일의 요금 이력이 이미 있음(ERD `UNIQUE(product_id, effective_from)`, 관리자 API) | 같은 적용 시작일의 월 구독요금이 이미 있습니다. | ERD `product_price`, Task 029·031 |
| `PRICE_FIRST_START_DATE_IN_FUTURE` | 409 | 상품의 첫(유일한) 월 구독요금 이력의 적용 시작일이 오늘보다 늦음. 첫 요금 이력이 미래면 오늘 적용할 요금이 없다(관리자 API) | 첫 월 구독요금의 적용 시작일은 오늘 또는 그 이전이어야 합니다. | Task 029·031 |
| `PRICE_NOT_CURRENT` | 409 | [요금 확인함]을 현재 적용 중인 요금이 아닌 요금 이력(지난 요금 이력·예정 요금 이력)에 요청함(관리자 API) | 지금 적용 중인 월 구독요금만 확인할 수 있습니다. | PRD 11절 41번, Task 029·031 |
| `PRICE_NOT_LATEST` | 409 | 가장 최근(마지막) 요금 이력이 아닌 월 구독요금 이력을 수정·삭제하려 함(관리자 API) | 가장 최근 월 구독요금만 수정하거나 삭제할 수 있습니다. | Task 029·031 |
| `PRICE_LAST_ROW_CANNOT_DELETE` | 409 | 상품의 월 구독요금 이력이 하나뿐이라 삭제할 수 없음(관리자 API) | 월 구독요금이 하나뿐이라 삭제할 수 없습니다. | Task 029·031 |
| `WISHLIST_LIMIT_EXCEEDED` | 409 | "다 봤음"이 아닌 찜이 이미 20개라 새로 추가하거나 "다 봤음"을 되돌릴 수 없음. 이미 찜한 작품(시즌)의 중복 추가는 이 오류가 아니라 200이다 | 찜은 20개까지 담을 수 있어요. 찜 목록에서 정리해 주세요. | PRD 11절 36번, FR-09, SCR-08 토스트 문구 |
| `WISHLIST_ITEM_NOT_FOUND` | 404 | 바꾸려는 찜 항목이 없거나 본인 것이 아님(삭제는 없어도 204라 이 오류가 없다) | 찜 항목을 찾을 수 없습니다. | PRD FR-09 "다른 사용자의 찜은 조회되지 않는다", Task 031·056 |
| `SERVICE_PRICE_NOT_FOUND` | 404 | 내 월 요금을 고치거나 되돌리려는 상품이 요금을 고칠 수 있는 상품이 아님(없는 상품이거나 판매 종료, 현재 적용 중인 요금이 없는 판매 중 상품도 포함하며 목록에서는 이런 상품이 빠진다) | 이 서비스의 요금 정보를 찾을 수 없습니다. | ERD `user_price_override`(상품 단위), Task 031·060 |

위 표의 Task 031 행은 계약(`openapi.yaml`)의 operation 설명과 같은 이름·상태다. 백엔드 `ErrorCode` enum에는 `BATCH_*` 4개(Task 045), `WISHLIST_*` 2개(Task 056), `PRICE_*` 6개(Task 059), `SERVICE_PRICE_NOT_FOUND`(Task 060)가 반영돼 있고, 나머지는 해당 기능 구현 Task가 추가한다. 프론트 `errorMessages.test.ts`는 이 파일의 모든 표 행을 읽어 `ERROR_MESSAGES`와 비교하므로, 이 표에 행을 추가한 PR에서 프론트 `TECH_ERROR_MESSAGES`에도 같은 코드·문구를 넣어야 한다(`PROPOSED_ERROR_MESSAGES` 8개는 옮기고, 새 코드 3개 `WISHLIST_LIMIT_EXCEEDED`·`WISHLIST_ITEM_NOT_FOUND`·`SERVICE_PRICE_NOT_FOUND`는 추가).
