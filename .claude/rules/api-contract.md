---
paths:
  - "docs/api/**"
  - "backend/src/main/java/**/api/**"
  - "frontend/src/api/**"
---

> `docs/api/openapi.yaml`은 백엔드와 프론트 사이의 계약이다. 이 파일은 계약을 작성·변경할 때의 규칙을 다룬다.
> 백엔드 구현 규칙은 `backend.md`, 프론트 구현 규칙은 `frontend.md`를 따른다.

## 변경 절차

1. **계약을 먼저 고친다.** 엔드포인트, 요청·응답 필드, 오류 코드를 바꿀 때는 `openapi.yaml`부터 수정한다.
2. **프론트 코드를 다시 생성한다.** `frontend`에서 `npm run api:generate`를 실행해 타입·훅·목업을 갱신하고, 타입 오류가 나는 화면을 고친다.
3. **백엔드를 구현한다.** 컨트롤러와 DTO를 계약에 맞춘다.
4. **CI에서 확인한다.** springdoc이 생성한 명세와 `openapi.yaml`이 다르면 CI가 실패한다. 어느 쪽이 맞는지 판단해 한쪽을 고친다.

- 계약 변경과 그에 따른 코드 변경은 같은 커밋(또는 같은 PR)에 담는다.
- **호환을 깨는 변경**(필드 삭제·이름 변경, 타입 변경, 필수값 추가, 경로 변경)은 커밋 메시지와 PR 설명에 명시하고, 영향받는 화면과 API를 함께 적는다.
- 응답에 필드를 **추가**하는 것은 호환되는 변경으로 본다.

## 경로

- 공개 API: `/api/public/**` — 로그인 없이 호출, 요청 제한 적용
- 회원 API: `/api/**` — Access Token 필요
- 관리자 API: `/api/admin/**` — ADMIN 역할 필요
- 경로는 소문자 kebab-case 복수형 명사를 쓴다 (`/api/wishlist-items`, `/api/admin/products`).
- 동작이 CRUD로 표현되지 않으면 하위 경로에 동사를 둔다 (`POST /api/plans/calculate`, `POST /api/admin/collect/run`).
- 작품은 외부에 TMDB 기준으로 노출한다: `/api/public/titles/{mediaType}/{tmdbId}`. 내부 `id`는 경로에 쓰지 않는다.
- 로그인한 사용자 본인의 데이터는 경로에 userId를 넣지 않고 `/api/me/**`로 표현한다 (예: `/api/me/settings`, `/api/me/subscriptions`).

## operationId (프론트 훅 이름)

- 모든 operation에 `operationId`를 붙인다. orval이 이 값으로 함수와 훅 이름을 만든다 (`searchTitles` → `useSearchTitles`).
- 형식은 camelCase `동사 + 대상`이다: `searchTitles`, `getTitleDetail`, `getMySettings`, `updateMySettings`, `calculatePlan`, `getCurrentPlan`.
- 한 번 정한 `operationId`는 함부로 바꾸지 않는다. 바꾸면 프론트의 훅 이름이 모두 바뀐다.
- `tags`는 도메인 단위로 붙인다: `catalog`, `user`, `wishlist`, `product`, `plan`, `admin`. orval이 태그별로 파일을 나눈다.

## 데이터 형식

| 항목 | 규칙 | 예 |
|---|---|---|
| 필드명 | camelCase (Java record 필드명과 동일) | `watchUnitId`, `totalRuntimeMin` |
| ID | 정수(int64). 내부 ID는 `xxxId`, TMDB ID는 `tmdbId`·`providerId` | `"watchUnitId": 1024` |
| 날짜 | `YYYY-MM-DD` 문자열 (`format: date`) | `"2026-11-01"` |
| 일시 | ISO-8601, 오프셋 포함 (`format: date-time`) | `"2026-10-03T14:00:00+09:00"` |
| 월 | `YYYY-MM` 문자열 | `"2026-11"` |
| 금액 | 원 단위 정수. 소수·통화 기호 없음 | `"monthlyPrice": 13500` |
| 시간 | 분 단위 정수, 필드명 끝에 `Min` | `"monthlyWatchMinutes": 900` |
| enum | UPPER_SNAKE_CASE 문자열, 스키마에 `enum` 목록 명시 | `"status": "AVAILABLE"` |
| boolean | `is`/`has` 접두사 | `"isRuntimeEstimated": true` |
| 이미지 | TMDB 상대 경로 그대로 전달, URL 조립은 프론트 | `"posterPath": "/abc.jpg"` |

- 값이 없을 수 있는 필드는 `nullable: true`로 명시하고, 필수 필드는 `required`에 넣는다. orval이 이 정보로 `undefined`/`null` 타입을 만든다.
- enum 값을 추가하면 프론트의 표시 매핑(라벨, 아이콘)도 함께 고친다.

## 공통 응답

**성공**: 모든 성공 응답은 `CommonResponse` 래퍼로 감싼다.

```json
{ "success": true, "data": { } }
```

- 스키마는 `components/schemas`에 응답별로 정의한다 (예: `CommonResponseTitleDetail`). orval이 정확한 타입을 만들 수 있게 제네릭처럼 뭉뚱그리지 않는다.
- 본문이 없는 성공(삭제 등)은 `204 No Content`로 응답하고 래퍼를 쓰지 않는다.

**오류**: 모든 오류는 `application/problem+json`의 ProblemDetail이다.

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "작품을 찾을 수 없습니다.",
  "instance": "/api/public/titles/movie/999",
  "errorCode": "TITLE_NOT_FOUND"
}
```

- `errorCode`는 SCREAMING_SNAKE_CASE이며, 각 operation의 오류 응답에 발생 가능한 `errorCode`를 설명으로 적는다.
- 전체 오류 코드 목록은 `docs/api/error-codes.md`에 HTTP 상태, 의미, 화면 메시지와 함께 관리한다. 프론트의 `lib/errorMessages.ts`는 이 목록과 일치해야 한다.
- 공통 오류: `400 VALIDATION_FAILED`(필드 오류 목록 포함), `401 UNAUTHORIZED`, `403 FORBIDDEN`, `429 RATE_LIMIT_EXCEEDED`, `500 INTERNAL_ERROR`, `502 EXTERNAL_API_ERROR`.

## 페이지네이션

- 요청: `page`(0부터), `size`(기본 20, 최대 100) 쿼리 파라미터.
- 응답 `data`는 다음 형태로 고정한다. Spring의 `Page` 객체를 그대로 직렬화하지 않는다.

```json
{ "content": [ ], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }
```

## 인증

- `components/securitySchemes`에 `bearerAuth`(HTTP bearer, JWT)를 정의하고, 회원·관리자 API에만 `security`를 붙인다.
- Refresh Token 쿠키는 계약에 필드로 노출하지 않는다. `/api/auth/refresh`의 설명에 "HttpOnly 쿠키 사용"이라고만 적는다.

## 예시(example) 작성

- 모든 응답 스키마에 `example`을 넣는다. orval이 이 값으로 MSW 목업을 만들고, 1주차 골격 화면이 이 데이터로 그려진다.
- 예시는 현실적인 값으로 쓴다: 실제 작품명, 7개 서비스 코드(`NETFLIX`, `DISNEY_PLUS`, `APPLE_TV_PLUS`, `TVING`, `WAVVE`, `WATCHA`, `COUPANG_PLAY`), 실제와 비슷한 가격과 러닝타임.
- 개인 정보처럼 보이는 실제 이메일·이름은 예시에 쓰지 않는다.
