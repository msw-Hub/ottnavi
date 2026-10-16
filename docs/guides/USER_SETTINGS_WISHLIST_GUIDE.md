# 내 설정·찜 API 이해 노트 (Task 055·056)

> 이 문서는 사용자가 요청해 만든 설명 문서다. 근거 문서가 아니고 **코드가 기준**이다(규칙의 근거는 PRD·ERD·TECH, 계약은 `docs/api/openapi.yaml`).
> 경로 기준은 `backend/src/main/java/com/ottnavi/`이고, 코드는 "요청이 들어와 응답이 나가는 순서"로 읽는다.

## 1. 한눈에 보기

로그인한 사용자가 **"나는 어떤 OTT를 쓰고, 한 달에 얼마·얼마나 볼 수 있는지"(설정)** 와 **"보고 싶은 작품 목록(찜)"** 을 저장하고 읽는 API 6개다. 이후 플랜 계산(Task 062~)은 이 두 가지를 입력으로 쓴다.

| 비유 | 실제 |
|---|---|
| 장보기 전에 적는 **예산 메모** | 내 설정(월 예산, 월 시청 시간, 서비스별 이용 상태) |
| **장바구니**(20칸, "이미 먹었음" 칸으로 옮기면 칸이 비워짐) | 찜(계산 대상은 "다 봤음"이 아닌 것 20개까지) |
| 창구 앞 **번호표**(내 이름표가 붙은 창구는 한 번에 한 명만 받음) | 사용자 행 잠금(`SELECT ... FOR UPDATE`) |

| API | operationId | 하는 일 |
|---|---|---|
| `GET /api/me/settings` | `getMySettings` | 내 설정 조회. 서비스 7개를 모두 채워 돌려준다 |
| `PUT /api/me/settings` | `updateMySettings` | 설정 전체 교체 저장. 처음 저장하면 온보딩 완료 |
| `GET /api/me/wishlist-items` | `listWishlistItems` | 찜 목록(최근 순, 전부) |
| `POST /api/me/wishlist-items` | `addWishlistItem` | 찜 추가. 새로 만들면 201, 이미 있으면 200 |
| `PATCH /api/me/wishlist-items/{id}` | `updateWishlistItem` | 우선순위·"다 봤음" 변경 |
| `DELETE /api/me/wishlist-items/{id}` | `removeWishlistItem` | 찜 삭제(없어도 204) |

## 2. 내 설정

### 2.1 저장되는 모양

| 테이블 | 행의 의미 | 규칙 |
|---|---|---|
| `user_setting` | 사용자당 1행: 월 예산, 월 시청 분, 프리셋 | **행이 있으면 온보딩을 마친 사용자**. 가입 때 만들지 않고 첫 PUT이 만든다 |
| `user_subscription` | 서비스 하나의 이용 상태 | `SUBSCRIBED`·`FREE`·`UNKNOWN`만 저장한다. **미구독(`NOT_SUBSCRIBED`)은 행을 두지 않고** 조회 때 서버가 채운다 |

### 2.2 조회 `GET` 흐름 (단순하다)

`UserSettingController` → `UserSettingService.getSettings`

1. 설정 행을 찾는다. 없으면 `isOnboardingDone=false`와 빈 값으로 끝낸다.
2. 있으면 내 구독 행을 읽는다.
3. 서비스 7개에 맞춰, 행이 없는 서비스는 `NOT_SUBSCRIBED`로 채워 돌려준다(서비스 ID 오름차순).

### 2.3 저장 `PUT` 흐름 (전체 교체)

`UserSettingController` → `UserSettingService.updateSettings`

| 단계 | 하는 일 | 건드리는 곳 |
|---|---|---|
| 1 | 서비스 7개를 읽고 요청 항목을 **검증·정규화** | DB 읽기만, 쓰기 없음 |
| 2 | 사용자 행을 `FOR UPDATE`로 읽어 잠금 | `users` |
| 3 | 기존 구독 행을 벌크로 전부 삭제 | `user_subscription` |
| 4 | 설정 행을 만들거나 값만 고침 | `user_setting` |
| 5 | `NOT_SUBSCRIBED`가 아닌 항목만 구독 행으로 INSERT | `user_subscription` |
| 6 | 방금 저장한 값을 7개 서비스로 채워 응답(DB를 다시 읽지 않는다) | — |

순서의 이유:

- **검증이 잠금보다 앞**: 틀린 요청은 잠금을 잡지도 않고 400으로 끝난다. 잠금 구간에는 DB 쓰기만 남아 같은 사용자의 다른 요청이 기다리는 시간이 짧다. 창구에 서기 전에 서류를 먼저 쓰는 것과 같다.
- **잠금을 거는 이유**: 같은 사용자의 PUT 두 개가 동시에 들어오면 둘 다 기존 행을 지우고 같은 `(user_id, ott_service_id)`를 넣다가 한쪽이 유니크 위반(500)이 난다. 잠금이 둘을 한 줄로 세운다.
- **삭제를 벌크 SQL로 먼저**: Hibernate는 같은 트랜잭션에서 `delete` 뒤 `save`를 하면 INSERT를 DELETE보다 먼저 보내 유니크 위반이 난다(ERD "같은 트랜잭션에서 지우고 다시 넣기").
- **한 트랜잭션**: 5단계에서 실패하면 3단계 삭제도 취소된다. 저장이 중간에 실패해도 기존 구독 정보가 사라지지 않는다.
- **설정 행은 지우고 다시 넣지 않는다**: 행이 있으면 값만 고치고(더티 체킹), 없으면 만든다. 구독 행만 "삭제 후 재삽입"이다.

### 2.4 `validateAndNormalize`: 검증과 정규화

요청 항목을 하나씩 검사하며 "저장할 깨끗한 맵(서비스 ID → 상태)"으로 바꾼다.

```text
요청
[0] {id:1, SUBSCRIBED, planName:" 스탠다드 ", billingDay:null}
[1] {id:4, FREE,       planName:null,         billingDay:null}
[2] {id:1, SUBSCRIBED, planName:"프리미엄",    billingDay:5}      ← 서비스 1 중복 → 400, 전체 거절
```

| 역할 | 규칙 | 실패하면 |
|---|---|---|
| 검증 | 서비스 7개에 없는 ID | 400 `subscriptions[i].ottServiceId` |
| 검증 | 같은 서비스를 두 번 보냄 | 400 (어느 쪽이 의도인지 서버가 알 수 없어 **둘 다 저장하지 않고 요청 전체를 거절**) |
| 검증 | `SUBSCRIBED`인데 요금제 이름이 비었음 | 400 `subscriptions[i].planName` |
| 검증 | `SUBSCRIBED`가 아닌데 이름·결제일이 있음 | 400 (DB `CHECK`에 걸려 500이 되기 전에 의미 있는 400으로) |
| 정규화 | 결제일이 `null` | 1일로 채운다(PRD FR-08) |
| 정규화 | 요금제 이름 앞뒤 공백 | 뗀다 |

형태·범위(예산 0 이상, 시청 분 120~44640, 서비스 1~7개, 이름 30자, 결제일 1~31)는 요청 DTO의 Bean Validation이 먼저 막고, 위 표는 **서비스 간 규칙**이다.

### 2.5 프론트가 정상이어도 서버가 또 검증하는 이유

프론트(`features/onboarding/schema.ts`)는 서비스 7개를 항상 전부 보내고, 구독 중이 아니면 `planName`·`billingDay`를 `null`로 맞춘다. 그래도 서버는 입력을 믿지 않는다. 개발자 도구·`curl`·나중의 다른 클라이언트·프론트 버그가 서버에 도달할 수 있고, 서버가 막지 않으면 DB `CHECK`·`UNIQUE`에 걸려 의미 없는 500이 된다.

### 2.6 읽는 순서와 깊이

| 순서 | 파일 | 깊이 | 볼 점 |
|---|---|---|---|
| 1 | `user/controller/UserSettingController` | 훑기 | `@AuthenticationPrincipal`로 userId, DTO → State 변환 |
| 2 | `user/dto/request/UpdateMySettingsRequest` | 훑기 | 형태·범위 검증 |
| 3 | `user/service/UserSettingService` | **꼭 읽기** | 6단계 순서, `validateAndNormalize` |
| 4 | `user/service/UserQueryService` | **꼭 읽기** | `lockUser`가 `Propagation.MANDATORY`인 이유 |
| 5 | `user/domain/entity/UserSetting`, `UserSubscription` | 넘어가기 | 평범한 엔티티 |

## 3. 찜

### 3.1 시청 단위로 저장한다

`wishlist_item`은 작품이 아니라 **시청 단위(`watch_unit`)** 를 가리킨다.

| 사용자가 찜한 것 | `watch_unit` |
|---|---|
| 기생충 | 영화 1행 |
| 오징어 게임 시즌 1 | 시즌 1행 |
| 오징어 게임 시즌 2 | 다른 시즌 행(같은 작품이어도 다른 찜) |

요청은 `mediaType + tmdbId + seasonNumber`로 오고, 서버가 먼저 내부 `watch_unit` ID로 바꾼다(`WatchUnitQueryService.getWatchUnitId`). 작품이 DB에 없으면 404이고, **찜 추가는 TMDB를 호출하지 않는다**(검색·상세로 이미 저장된 작품만).

### 3.2 추가 `POST` 흐름 (가장 중요)

`WishlistController.addWishlistItem` → `WishlistService.add`

| 단계 | 하는 일 |
|---|---|
| 1 | 영화인데 시즌 번호가 있거나, 드라마인데 없으면 400 |
| 2 | 단위 ID를 찾는다. 없으면 404 `TITLE_NOT_FOUND` |
| 3 | 내 사용자 행을 잠근다 |
| 4 | 이미 찜했으면 **새로 만들지 않고 기존 ID로 200**(우선순위·"다 봤음" 불변) |
| 5 | 활성 찜(`watched_at`이 없는 것)이 20개 이상이면 409 `WISHLIST_LIMIT_EXCEEDED` |
| 6 | 저장하고 201 |

- **4가 5보다 먼저**: 이미 20개여도 이미 있는 항목을 다시 담는 것은 오류가 아니라 200이어야 하기 때문이다.
- **3이 4~6보다 먼저**: 잠금 없이 "개수 확인(19) → 저장"을 하면 동시 요청 두 개가 둘 다 19로 읽고 둘 다 저장해 21개가 된다. 잠금이 둘을 한 줄로 세워 두 번째는 20으로 읽고 409를 받는다.
- 개수 상한은 DB 제약이 아니라 **애플리케이션 규칙**이다(ERD). 그래서 직렬화가 코드의 몫이다.

### 3.3 목록 `GET` 흐름

`WishlistService.list`: 쿼리 횟수가 작품 수와 무관하게 고정이다.

| 단계 | 하는 일 |
|---|---|
| 1 | 내 찜을 최근 순(`created_at`, `id` 내림차순)으로 읽는다 |
| 2 | 찜의 단위 ID를 모아 `loadSummaries`로 **한 번에** 요약·서비스별 상태·기준일 조회 |
| 3 | 내가 이용 중인(`SUBSCRIBED`·`FREE`) 서비스 ID 집합 조회(찜이 없으면 생략) |
| 4 | 찜마다 응답 항목 하나 조립, 활성 개수 세기 |

`isWatchableNow`는 그 단위의 서비스별 상태 중 `AVAILABLE`인 서비스가 내 이용 서비스 집합에 있으면 true다(`UNKNOWN` 구독은 제외). `dataAsOf`는 판정 확인일 중 가장 오래된 날짜(KST)다.

### 3.4 변경 `PATCH`

| 입력 | 동작 |
|---|---|
| `priority`만 | 우선순위 변경(더티 체킹) |
| `isWatched: true` | `watchedAt` 기록. 이미 있으면 처음 시각 유지 |
| `isWatched: false` | 되돌림. **이때만** 사용자 행을 잠그고 활성 20개면 409 |
| 둘 다 없음 | 400 |

상한을 넘길 수 있는 동작은 **추가**와 **되돌리기** 둘뿐이라 이 둘만 잠근다("다 봤음" 표시는 활성 개수를 줄이기만 한다). 본인 것이 아니거나 없으면 404 `WISHLIST_ITEM_NOT_FOUND`다.

### 3.5 삭제 `DELETE`

`deleteByIdAndUserId` 한 줄이다. 조건에 `userId`가 붙어 남의 찜은 지워지지 않고, 없어도 204다(여러 번 호출해도 결과가 같다).

### 3.6 읽는 순서와 깊이

| 순서 | 파일 | 깊이 | 볼 점 |
|---|---|---|---|
| 1 | `wishlist/controller/WishlistController` | 훑기 | 200/201 분기(`ResponseEntity`) |
| 2 | `wishlist/service/WishlistService.add` | **꼭 읽기** | 중복 → 상한 → 저장 순서, 잠금 위치 |
| 3 | `wishlist/service/WishlistService.update` | **꼭 읽기** | 되돌리기만 잠그는 이유 |
| 4 | `wishlist/service/WishlistService.list` | 훑기 | 일괄 조회 |
| 5 | `catalog/service/WatchUnitQueryService` | 훑기 | `getWatchUnitId`, `loadSummaries`(N+1 방지) |
| 6 | `user/service/UserQueryService` | 훑기 | `lockUser`, `listUsableServiceIds` |
| 7 | `wishlist/domain/entity/WishlistItem` | 넘어가기 | `userId`·`watchUnitId`를 값으로만 보관(다른 도메인 엔티티 미참조) |

## 4. 두 코드의 차이

| | 내 설정 | 찜 |
|---|---|---|
| 저장 방식 | 구독 행 전체 교체 | 행 단위 추가·변경·삭제 |
| 핵심 위험 | 삭제 후 재삽입의 유니크 위반 | "개수 확인 → 저장" 사이의 경합 |
| 잠금 | 모든 PUT | 추가, "다 봤음" 되돌리기 |
| 잠금 방법 | 같음: `UserRepository.findByIdForUpdate` | |

## 5. 테스트가 확인하는 것

| 테스트 | 확인하는 것 | 막으려는 사고 |
|---|---|---|
| `UserSettingServiceTest` | 결제일 기본 1, 미구독 행 미저장, 없는 서비스·중복·이름 규칙 | 잘못된 입력이 DB까지 가서 500이 되는 일 |
| `UserSettingApiTest` | 저장 후 조회 일치, 재저장 시 교체, 사용자 격리, 이중 PUT 경합 | 다른 사람의 설정이 보이거나 동시 저장이 500이 되는 일 |
| `WishlistServiceTest` | 상한 판정, 중복 200, 되돌리기 상한 | 21번째 찜, 중복 행 |
| `WishlistApiTest` | 시즌 찜 → 목록 → PATCH → 삭제, 타인 항목 404·삭제 안 됨, 작품 없음 404, 동시 추가 직렬화 | 남의 찜 조작, 동시 요청으로 21개가 되는 일 |

## 6. 깨뜨려 보기 (이해 점검)

1. `WishlistService.add`의 `lockUser` 호출을 주석 처리하고 `WishlistApiTest`의 동시 추가 테스트를 여러 번 돌린다. 확률적이라 한두 번은 통과할 수 있고, 여러 번이면 21개가 되는 경우가 나온다.
2. `UserSettingService.updateSettings`에서 구독 벌크 삭제를 지우고 두 번 저장해 본다. 같은 서비스가 유니크 위반으로 실패한다.
3. `add`에서 4(중복 확인)와 5(상한 확인) 순서를 바꾼다. 이미 20개인 상태에서 같은 작품을 다시 담으면 200이 아니라 409가 되어 어떤 테스트가 깨지는지 본다.
