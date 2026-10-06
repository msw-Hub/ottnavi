# 오류 코드

> API 오류 응답(`application/problem+json`)의 `errorCode` 목록이다. 프론트의 `src/lib/errorMessages.ts`는 이 표와 일치해야 한다.
> 백엔드 `ErrorCode`(Task 023)도 이 표를 기준으로 만든다. 새 코드는 계약(`openapi.yaml`)과 같은 PR에서 이 표에 추가한다.
> "(제안)"은 TECH에서 제안만 된 값이며, 해당 기능을 구현하는 Task에서 확정한다.
> 백엔드 테스트 `ErrorCodeDocumentTest`가 아래 "공통 코드" 표의 코드·HTTP 상태(첫 숫자)와 `ErrorCode` enum이 같은지 검사한다. 표 형식(`| \`코드\` | 상태 | ...`)을 바꾸면 테스트도 함께 고친다.

## 공통 코드

| errorCode | HTTP 상태 | 의미 | 화면 메시지 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 값 검증 실패(본문 필드 검증, 쿼리·경로 파라미터 제약 위반·형식 오류·누락). 응답의 `fieldErrors`에 필드별 오류 목록 포함 | 입력한 내용을 다시 확인해 주세요. |
| `INVALID_REQUEST` | 400 (406·415 등 기타 4xx는 원래 상태 유지) | 필드를 특정할 수 없는 요청 오류(깨진 JSON 본문, 지원하지 않는 Content-Type 등). `fieldErrors` 없음 | 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| `UNAUTHORIZED` | 401 | 인증 필요 또는 Access Token 만료·무효 | 로그인이 필요합니다. |
| `FORBIDDEN` | 403 | 권한 없음(관리자 전용 API 등) | 접근 권한이 없습니다. |
| `RESOURCE_NOT_FOUND` | 404 | 요청한 경로(API)가 없음. 특정 데이터가 없는 경우는 `TITLE_NOT_FOUND`처럼 도메인 코드를 쓴다 | 요청한 페이지를 찾을 수 없습니다. |
| `METHOD_NOT_ALLOWED` | 405 | 경로는 있지만 HTTP 메서드를 지원하지 않음. `Allow` 헤더 포함 | 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| `TITLE_NOT_FOUND` | 404 | 요청한 작품(TMDB ID)을 찾을 수 없음 | 작품을 찾을 수 없습니다. |
| `RATE_LIMIT_EXCEEDED` | 429 | 비로그인 공개 API 요청 제한 초과 | 요청이 많습니다. 잠시 후 다시 시도해 주세요. |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. |
| `EXTERNAL_API_ERROR` | 502 | 외부 API(TMDB 등) 호출 실패 | 외부 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. |

`INVALID_REQUEST`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`는 Task 023에서 추가했다(없는 경로·잘못된 요청에도 `errorCode`가 오게 하는 V-API 완료 기준). 화면 메시지는 제안이며 프론트 `errorMessages.ts`(Task 025)에서 확정한다.

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
| `PLAN_DRAFT_STALE` | 409 | 저장하려는 DRAFT의 데이터 기준일이 최신 수집 완료 시각보다 이름. 프론트가 받으면 자동으로 재계산을 요청한다 | 데이터가 갱신되어 플랜을 다시 계산하고 있습니다. | TECH 2절, PRD 5.4 (제안) |
| `PLAN_DRAFT_NOT_FOUND` | 404 | 저장하려는 DRAFT가 없거나 본인 것이 아님 | 계산된 플랜을 찾을 수 없습니다. 다시 계산해 주세요. | TECH 2절 (제안) |
| `BATCH_ALREADY_RUNNING` | 409 | 같은 `targetDate`+`tier`의 수집 Job이 이미 실행 중(관리자 API) | 같은 날짜의 수집이 이미 실행 중입니다. | TECH 3절 (도출) |
| `BATCH_ALREADY_COMPLETED` | 409 | 같은 `targetDate`+`tier`의 수집 Job이 이미 완료됨(관리자 API) | 해당 날짜의 수집은 이미 완료되었습니다. | TECH 3절 (도출) |
