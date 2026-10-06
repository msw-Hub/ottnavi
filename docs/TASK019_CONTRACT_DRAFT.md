# Task 019 계약 초안 (서비스 목록 API · 상태 확인 API)

> 준비용 초안이다. `docs/api/openapi.yaml`은 아직 고치지 않았다. 실제 반영은 Task 019 구현 PR에서 계약 → 프론트 `npm run api:generate` → 백엔드 구현 순서로 같은 PR에 담는다(`api-contract.md`).
> 근거: ROADMAP Task 019·023, ERD `ott_service`(109행), `api-contract.md`(경로·operationId·응답 형식). 이름·경로는 모두 **제안**이다.

## 1. 정해야 할 것 (사용자 결정)

| # | 항목 | 제안 | 이유 |
|---|---|---|---|
| 1 | 상태 확인 API 경로 | `GET /api/public/status`, operationId `getStatus` | ROADMAP은 "상태 확인 API"만 말하고 경로는 정하지 않았다. 배포 확인(V-DEPLOY)을 Vercel을 거쳐 하려면 `/api/**` 아래여야 한다. `/actuator/health`와는 별개 |
| 2 | 상태 확인 응답 | `{ "success": true, "data": { "status": "UP" } }` | 가장 작은 형태. DB 연결 확인은 서비스 목록 API가 대신한다 |
| 3 | `logoPath` | `nullable: true` | ERD는 TMDB 이미지 상대 경로이나 시드 시점에 값이 없을 수 있다. 값은 TMDB 확인 후 채운다 |
| 4 | 서비스 목록 `data` 형태 | 배열(래퍼 안에 7개 고정 목록) | 페이지네이션 대상이 아니다. `api-contract.md`의 페이지 형식은 쓰지 않는다 |
| 5 | 태그 | `product` (서비스 목록), 상태 확인은 태그 없음 또는 `product` | `product` 태그 설명이 "OTT 서비스·상품·가격" |

## 2. openapi.yaml 추가 초안

두 operation 모두 이 Task에서 구현까지 하므로 `x-planned`는 달지 않는다(구현 PR에서 계약과 함께 들어간다).

```yaml
paths:
  /public/ott-services:           # servers가 /api라 경로에는 /api를 쓰지 않는다
    get:
      tags: [product]
      operationId: listOttServices
      summary: OTT 서비스 목록 조회
      description: |
        국내 OTT 7개 서비스를 `displayOrder` 순으로 반환한다. 비로그인 공개 API(요청 제한 적용).
        쿠팡플레이는 `dataQuality`가 `INSUFFICIENT`(데이터 부족)다.
      responses:
        '200':
          description: 서비스 목록
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CommonResponseOttServiceList'
        '429':
          $ref: '#/components/responses/TooManyRequests'
        '500':
          $ref: '#/components/responses/InternalError'
  /public/status:
    get:
      tags: [product]
      operationId: getStatus
      summary: 서버 상태 확인
      description: 로그인 없이 호출하는 간단한 상태 확인. 배포 후 Vercel 경유 연결 확인에 쓴다.
      responses:
        '200':
          description: 서버 정상
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CommonResponseStatus'
        '500':
          $ref: '#/components/responses/InternalError'

components:
  schemas:
    OttService:
      type: object
      description: 국내 OTT 서비스 1개
      required: [ottServiceId, code, name, displayOrder, dataQuality]
      properties:
        ottServiceId:
          type: integer
          format: int64
          description: 서비스 내부 ID
        code:
          type: string
          description: 서비스 코드
          enum: [NETFLIX, DISNEY_PLUS, APPLE_TV_PLUS, TVING, WAVVE, WATCHA, COUPANG_PLAY]
        name:
          type: string
          description: 화면 표시 이름
        logoPath:
          type: string
          nullable: true
          description: TMDB 이미지 상대 경로. URL 조립은 프론트가 한다
        displayOrder:
          type: integer
          format: int32
          description: 화면 정렬 순서
        dataQuality:
          type: string
          description: 데이터 충분도. INSUFFICIENT는 "데이터 부족"으로 표시한다
          enum: [NORMAL, INSUFFICIENT]
      example:
        ottServiceId: 1
        code: NETFLIX
        name: 넷플릭스
        logoPath: null
        displayOrder: 1
        dataQuality: NORMAL
    CommonResponseOttServiceList:
      type: object
      required: [success, data]
      properties:
        success:
          type: boolean
        data:
          type: array
          items:
            $ref: '#/components/schemas/OttService'
      example:
        success: true
        data:
          - { ottServiceId: 1, code: NETFLIX, name: 넷플릭스, logoPath: null, displayOrder: 1, dataQuality: NORMAL }
          - { ottServiceId: 2, code: DISNEY_PLUS, name: 디즈니+, logoPath: null, displayOrder: 2, dataQuality: NORMAL }
          - { ottServiceId: 3, code: APPLE_TV_PLUS, name: 애플 TV+, logoPath: null, displayOrder: 3, dataQuality: NORMAL }
          - { ottServiceId: 4, code: TVING, name: 티빙, logoPath: null, displayOrder: 4, dataQuality: NORMAL }
          - { ottServiceId: 5, code: WAVVE, name: 웨이브, logoPath: null, displayOrder: 5, dataQuality: NORMAL }
          - { ottServiceId: 6, code: WATCHA, name: 왓챠, logoPath: null, displayOrder: 6, dataQuality: NORMAL }
          - { ottServiceId: 7, code: COUPANG_PLAY, name: 쿠팡플레이, logoPath: null, displayOrder: 7, dataQuality: INSUFFICIENT }
    StatusResponse:
      type: object
      required: [status]
      properties:
        status:
          type: string
          description: 서버 상태
          enum: [UP]
      example:
        status: UP
    CommonResponseStatus:
      type: object
      required: [success, data]
      properties:
        success:
          type: boolean
        data:
          $ref: '#/components/schemas/StatusResponse'
      example:
        success: true
        data:
          status: UP
```

## 3. 백엔드 쪽 대응 (구현 때 참고)

| 계약 | 백엔드 |
|---|---|
| `ottServiceId` | `ott_service.id`(`OttService.id`)를 `ottServiceId`로 노출. 응답 DTO 필드명은 계약과 정확히 같게 한다 |
| `code` enum | 엔티티·DTO에 enum을 두면 `@Enumerated(EnumType.STRING)`, 계약 enum과 대조 테스트(T-5)가 비교한다 |
| `dataQuality` enum | `NORMAL`/`INSUFFICIENT` |
| `V1__create_ott_service.sql` | ERD 컬럼: `id`, `code`(UK, VARCHAR 30), `name`(50), `logo_path`(200), `display_order`, `data_quality`(20). 시드 7건, 쿠팡플레이만 `INSUFFICIENT` |
| 응답 래퍼 | `CommonResponse.success(data)`(Task 023) |
| 경로 보안 | `/api/public/**`는 이후 요청 제한 대상. 이 Task에서는 `permitAll` 임시 설정 위에서 오리진 비밀 헤더 필터만 적용 |

## 4. 확인하지 못한 것 / 주의

- 서비스 한글 표시명(`넷플릭스`, `디즈니+` 등)은 예시이며 PRD·ERD에 확정 표기가 있는지 확인하지 않았다. 시드 작성 때 PRD를 확인한다.
- `logoPath` 실제 값은 TMDB 조회가 필요하다(Task 019에서는 null로 두거나 확인된 값만 넣는다).
- 이 초안은 `OpenApiContractTest`(Task 023)가 비교하는 대상이 되므로, 구현 DTO와 `required`·`nullable`·`enum`이 어긋나지 않게 한다.
- 계약 변경과 코드 변경은 같은 PR에 담는다. 이 초안 파일(`TASK019_CONTRACT_DRAFT.md`)은 Task 019 PR에서 삭제하거나 `기록:`에 흡수한다.
