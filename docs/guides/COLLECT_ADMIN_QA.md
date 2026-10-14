# 수집 관리자 API 이해 노트 (질문과 답)

> 설명용 문서다. 근거 문서가 아니고 **코드가 기준**이다(규칙의 근거는 PRD·ERD·TECH, 계약은 `docs/api/openapi.yaml`).
> Task 045 구현을 읽으며 나온 질문을 배경부터 다시 정리했다. 구현 순서와 테스트는 `COLLECT_GUIDE.md` 11절을 본다.
> 번호는 파일 이름만 적고 줄 번호는 적지 않았다(코드가 바뀌면 줄이 밀린다).

## 1. 이 API는 무엇을 하는가

수집 Job은 TMDB에서 작품과 제공처 정보를 모아 DB를 갱신하는 배치다. 매일 한 번 돌고 몇 분이 걸린다.
관리자 API는 그 Job을 조작하는 리모컨이고, 호출하는 쪽은 GitHub Actions cron과 관리자 화면(SCR-16)이다.

| 기능 | 호출 | 하는 일 |
|---|---|---|
| 시작 | `POST /api/admin/collect/executions` | 수집을 시작하고 바로 202와 실행 번호를 돌려준다. 수집은 뒤에서 계속 돈다 |
| 이력 | `GET /api/admin/collect/executions` | 지난 실행 목록과 상태 |
| 변경 | `GET .../{id}/changes` | 그 실행에서 제공 상태가 바뀐 작품 목록 |
| 복구 | `POST .../{id}/recover` | 서버가 죽어 멈춘 채 남은 실행을 "실패"로 정리 |
| 재시작 | `POST .../{id}/restart` | 실패한 실행을 이어서 다시 돌림 |

### 큰 흐름

```
호출자(cron 또는 관리자 화면)
   │  Authorization: Bearer 토큰
   ▼
AdminBatchTokenFilter       토큰 검사 (틀리면 401)
   ▼
AdminCollectController      요청 5개를 받아 서비스에 넘기는 문
   ▼
CollectExecutionService     "해도 되는 요청인가" 판단 + 락 (명령: 시작·재시작·복구)
CollectExecutionQueryService  응답 만들기 (조회: 이력·변경)
   ▼
CollectExecutionRules       판정 규칙 모음 (DB·Spring을 모르는 순수 Java)
CollectExecutionQuery       BATCH_* 표를 SQL로 읽는 곳
   ▼
CollectJobLauncher          Job을 실제로 시작·재시작·복구하는 창구
   ▼
collectJobOperator          비동기 실행기(동시에 1건만) → 수집 Job이 뒤에서 돎
```

판단(`CollectExecutionRules`)과 실행(`CollectJobLauncher`)을 나눈 것이 핵심이다.
시작 API, 재시작 API, 이력의 `canRecover`·`canRestart`가 모두 같은 판정을 쓰므로 "버튼은 켜졌는데 누르면 거절"이 생기지 않는다.

## 2. `BATCH_*` 표는 무엇인가

수집 Job은 몇 분씩 걸려서 중간에 서버가 죽거나 TMDB 오류로 실패할 수 있다.
Spring Batch는 "어디까지 했고 어떻게 끝났는지"를 DB 표에 자동으로 적어 둔다. 이 표들이 `BATCH_`로 시작한다.

| 구분 | 도메인 표(`title` 등) | `BATCH_*` 표 |
|---|---|---|
| 만든 쪽 | 우리(Flyway, JPA 엔티티) | Spring Batch |
| 적는 쪽 | 우리 코드 | Job이 돌 때 Spring Batch가 직접 |
| 모양을 바꾸는 쪽 | 우리 | Batch 버전이 바뀌면 같이 바뀔 수 있음 |
| 우리가 하는 일 | 읽고 씀 | **읽기만** (쓰기는 항상 Batch를 거침) |

### 표 4개와 관계

```
BATCH_JOB_INSTANCE (작업 종류 + 파라미터의 묶음)
      │ 1 : N
BATCH_JOB_EXECUTION (실제로 돌린 한 번 한 번의 기록)
      │ 1 : N                         │ 1 : N
BATCH_STEP_EXECUTION (단계별 기록)    BATCH_JOB_EXECUTION_PARAMS (실행 파라미터)
```

| 표 | 한 줄이 뜻하는 것 | 우리가 쓰는 컬럼 |
|---|---|---|
| `BATCH_JOB_INSTANCE` | 같은 작업 + 같은 파라미터의 묶음 | `JOB_INSTANCE_ID`, `JOB_NAME`(`collectJob`) |
| `BATCH_JOB_EXECUTION` | Job을 한 번 돌린 기록. 화면의 `executionId`가 이 줄의 번호 | `JOB_EXECUTION_ID`, `STATUS`, `EXIT_CODE`, `EXIT_MESSAGE`, `CREATE_TIME`, `START_TIME`, `END_TIME` |
| `BATCH_JOB_EXECUTION_PARAMS` | 시작할 때 준 값. **이름별로 한 줄** | `PARAMETER_NAME`(`targetDate`, `tier`), `PARAMETER_VALUE` |
| `BATCH_STEP_EXECUTION` | Step 하나의 기록 | `STEP_NAME`, `STATUS`, `WRITE_COUNT`, `EXIT_MESSAGE` |

읽던 위치를 저장하는 `BATCH_*_CONTEXT` 표도 있다(재시작 때 어디서부터 이어갈지). 이력 조회는 위 4개만 읽는다.

### 값 예시: 10월 15일 수집이 실패한 뒤 재시작해서 성공

`BATCH_JOB_EXECUTION`

| JOB_EXECUTION_ID | JOB_INSTANCE_ID | STATUS | EXIT_CODE | START_TIME | END_TIME |
|---|---|---|---|---|---|
| 11 | 5 | FAILED | FAILED | 10-15 02:00 | 10-15 02:03 |
| 12 | 5 | COMPLETED | COMPLETED | 10-15 02:10 | 10-15 02:14 |

실행 11과 12는 같은 인스턴스 5다(같은 파라미터로 다시 시작했기 때문).

`BATCH_JOB_EXECUTION_PARAMS`(실행당 두 줄)

| JOB_EXECUTION_ID | PARAMETER_NAME | PARAMETER_VALUE |
|---|---|---|
| 12 | targetDate | 2026-10-15 |
| 12 | tier | ALL |

`BATCH_STEP_EXECUTION`(실행 12의 일부): `movieDetailStep` WRITE_COUNT 340, `tvDetailStep` WRITE_COUNT 210 → 화면의 `processedCount`는 550.

### 이력 한 줄을 만들 때 값이 오는 곳

| 화면 값 | 어디서 오나 |
|---|---|
| 대상 날짜, 범위 | `PARAMS`의 `targetDate`, `tier` 줄 두 개(같은 표를 두 번 붙인다) |
| 상태, 시작·종료 시각 | `JOB_EXECUTION` |
| 실패 사유 | `JOB_EXECUTION`의 `EXIT_MESSAGE`(비면 실패한 Step의 메시지) |
| 처리 건수 | `STEP_EXECUTION`의 두 상세 Step `WRITE_COUNT` 합 |

## 3. `BATCH_*`를 왜 SQL(`JdbcTemplate`)로 읽는가

### 읽는 방법 세 가지

| 방법 | 비유 | 이번 코드 |
|---|---|---|
| 1. Batch가 주는 조회 기능(`JobRepository`, `JobExplorer`) | 은행 창구에 "이 거래 하나만 보여 주세요" | 실행 한 건을 읽어 Job을 조작할 때(`CollectJobLauncher`) |
| 2. 표를 엔티티로 만들어 JPA/QueryDSL로 읽기 | 장부를 복사해 우리 서류함에 같은 양식으로 둠 | 사용 안 함 |
| 3. SQL 직접 읽기(`JdbcTemplate`) | 장부를 열어 필요한 줄을 직접 찾음 | 이력 조회 전체와 서비스의 단건 확인(`CollectExecutionQuery`) |

### 3번을 고른 이유

1. 화면은 "10월 15일 ALL 범위만, 날짜 최신순으로, 20개씩, 각 실행의 저장 건수까지"를 요구한다.
   Batch 조회 기능은 실행 한 건이나 인스턴스 단위 조회는 되지만 파라미터 조건, 정렬, 페이지, 묶음 합계는 지원하지 않는다.
   `JobExplorer`는 제거 예정(`@Deprecated(forRemoval)`)이기도 하다.
2. 2번은 Batch가 계속 고쳐 쓰는 표를 엔티티로 복사해 두는 일이라, Batch 버전이 바뀌면 표 모양이 달라져 `validate`가 깨지거나
   캐시가 낡은 값을 들 수 있다. "읽기만 하는데 복사본을 둘 필요가 없다"는 판단이다.
3. 쓰기는 한 곳도 SQL로 하지 않는다. 시작·재시작·복구는 항상 `JobOperator`와 `JobRepository`를 거친다.

### 알아 둘 점

- `CollectExecutionQuery`는 `@Repository`가 붙은 데이터 접근 전용 클래스다. SQL이 서비스에 있지 않고 이 클래스에 모여 있다.
  다만 도메인 JPA 리포지토리(`domain/repository`)와 달리 `collect/job`에 있고 이름도 `Query`다. Batch 표는 도메인 엔티티가 아니기 때문이다.
- **단건 읽기도 SQL이다.** 서비스(`restart`, `recover`, `getExecution`, `listChanges`)는 `findById`(SQL)로 한 건을 읽는다.
  `JobRepository.getJobExecution`은 `CollectJobLauncher`에서만 쓴다. 단건을 SQL로 읽는 이유는 목록과 같은 SELECT·같은 변환기·같은
  결과 형식(`CollectExecutionRecord`)을 써서 상태·버튼 판정을 한 코드로 통일하려는 것이고, SQL 조건에 `JOB_NAME = 'collectJob'`이
  있어 다른 Job의 실행 번호는 404로 걸러진다. 단건을 Batch 기능으로 대체할 수 있는지(`isLatest` 계산, Job 이름 확인)는 검증하지 않았다.
- SQL 문자열은 컬럼 이름에 의존하므로 Batch 버전이 바뀌면 깨질 수 있다. `CollectExecutionQueryTest`와 이력 API 테스트가 6.0.5 스키마 기준으로 확인한다.
- 값은 모두 `?`로 바인딩한다. 실행 번호 목록(`IN`)은 `?` 개수만 코드로 만든다.

### QueryDSL은 안 되나

QueryDSL은 SQL을 자바 코드로 안전하게 조립하는 도구인데 **JPA 엔티티 위에서 동작한다**(엔티티로 `QTitle` 같은 도우미를 만든다).
그래서 `BATCH_*`에 쓰려면 2번(표를 엔티티로 만들기)이 먼저다. 이 프로젝트에는 QueryDSL이 없고,
PRD 11절 28번에 "Spring Boot 4.1·Hibernate 7 호환 버전은 도입 시점에 확인"으로 **보류**되어 있다.

| | `JdbcTemplate`(현재) | 엔티티 + QueryDSL |
|---|---|---|
| 오타 검출 | 실행해 봐야 안다(테스트가 잡음) | 컴파일 때 안다 |
| 선행 작업 | 없음 | 엔티티 4개, 라이브러리 도입, Boot 4.1 호환 확인 |
| Batch 표 변경의 영향 | 컬럼 이름 문자열이 깨짐 | 엔티티 필드가 깨짐(같음) |

## 4. "바뀐 작품 목록"은 Batch 기록이 아니다

`GET .../{id}/changes`는 `BATCH_*`가 아니라 **우리 도메인 표 `availability_change`**를 읽는다.

1. 수집 Job의 판정 Step이 제공 상태가 바뀐 작품을 발견하면 `availability_change`에 한 줄씩 저장한다(JPA 엔티티 `AvailabilityChange`).
2. 그 줄에는 어느 실행에서 바뀌었는지를 나타내는 `job_execution_id`가 같이 들어간다. 이 번호가 `BATCH_JOB_EXECUTION`의 실행 번호와 같을 뿐 표는 우리 것이다.
3. 조회는 `AvailabilityChangeRepository`(JPA)가 `job_execution_id`로 한 페이지를 읽고, 서비스가 `PageResponse`로 바꾼다.

| 조회 대상 | 어디에 있나 | 읽는 방식 |
|---|---|---|
| 실행 이력(상태, 시각, 처리 건수) | `BATCH_*` | `JdbcTemplate` |
| 바뀐 작품 목록, 변경 건수 | `availability_change` | JPA |

## 5. 락과 트랜잭션

### `launchLock.lock()`이란

자바 표준 도구(`ReentrantLock`)로, 한 번에 한 요청만 통과시키는 열쇠다. 필요한 이유는 "검사하고 시작하기" 사이에 다른 요청이 끼는 문제다.

```
요청 A: "실행 중인 수집 있나?" → 없음 ──┐
요청 B: "실행 중인 수집 있나?" → 없음 ──┤ 둘 다 없다고 봄
요청 A: 시작!                           │
요청 B: 시작!                           ┘ ← 수집이 두 번 시작됨
```

```java
launchLock.lock();      // 열쇠를 잡는다. 이미 누가 잡고 있으면 끝날 때까지 기다린다
try {
    // 검사 → 시작 (이 사이에는 한 요청만 들어올 수 있다)
} finally {
    launchLock.unlock(); // 반드시 돌려놓는다(예외가 나도)
}
```

### 락은 요청의 내용을 비교하지 않는다

락은 같은 요청인지 다른 요청인지 가리지 않고 **모두 한 줄로 세운다.** 막을지 말지는 락 안에서 규칙(`judgeLaunch`)이 DB 상태를 보고 정한다.

```
요청 A (10/15 시작)    요청 B (10/14 시작)    요청 C (10/15 시작)
   │ 열쇠 잡음           │ 대기                  │ 대기
   │ 검사: 실행 없음      │                       │
   │ 시작 접수           │                       │
   │ 열쇠 놓음 ──────────▶ 열쇠 잡음              │
                         │ 검사: 다른 날짜가 실행 중 → 409 BATCH_ALREADY_RUNNING
                         │ 열쇠 놓음 ────────────▶ 열쇠 잡음
                                                  │ 검사: 같은 날짜가 실행 중 → 409
```

- 락을 쥐는 시간은 짧다. 시작은 접수만 하고 돌아오므로(수집은 별도 스레드) "검사 + 접수" 동안만 잡는다.
- 수집이 도는 동안 뒤 요청을 막는 것은 락이 아니라 DB 기록(`STARTED`)과 규칙이다.
- 조회 요청(이력·변경)은 락을 쓰지 않는다. 복구 요청은 같은 락을 쓴다.

### 요청이 많이 오면

락에 닿기 전에 관문이 있다: 오리진 비밀 헤더 필터, 그다음 `AdminBatchTokenFilter`. 토큰이 없으면 컨트롤러와 락에 닿지 못하고 401로 끝난다.
락에 닿는 것은 토큰 보유자(cron, 관리자)뿐이라 현실적인 동시 요청은 매우 적다.

| 한계 | 내용 |
|---|---|
| 순서 | 공정(FIFO) 모드가 아니라 먼저 온 요청이 먼저라는 보장이 없다 |
| 타임아웃 | 대기 시간 제한이 없다(`tryLock` 미사용) |
| 스레드 | 대기하는 요청 하나가 요청 처리 스레드 하나를 쓴다 |
| DB 연결 | 서비스에 `@Transactional`이 없어 대기 중에는 연결을 쥐지 않는다 |
| 서버 한 대 전제 | 락은 JVM 안에서만 통한다. 서버가 여러 대면 DB 잠금이 필요하다 |

### 트랜잭션(`@Transactional`)으로는 왜 안 하나

| 도구 | 지키는 것 | 비유 |
|---|---|---|
| `@Transactional` | 여러 DB 작업을 **전부 성공하거나 전부 취소** | 송금: 출금과 입금이 함께 되거나 함께 안 됨 |
| `ReentrantLock` | 이 구간을 **한 번에 한 명만** 지나감 | 화장실 열쇠 |

트랜잭션이 있어도 두 요청이 각자 "없음"을 읽고 둘 다 INSERT할 수 있다(PostgreSQL 기본 격리 수준). 막으려면 DB 행 잠금이나 유일 제약을 따로 걸어야 한다.
이 서비스는 오히려 트랜잭션을 **일부러 붙이지 않았다.**

1. Spring Batch는 이미 열린 트랜잭션 안에서 Job 실행 기록을 만드는 것을 거부한다("Existing transaction detected").
2. 트랜잭션 안에서 만든 실행 기록은 커밋 전에는 비동기 스레드가 읽지 못한다.

Batch도 같은 파라미터의 중복 실행은 스스로 막는다(`JobExecutionAlreadyRunningException`). 하지만 "다른 날짜가 돌아도 막는다"(전체 동시 실행 금지)는
우리 규칙이라 락과 규칙으로 직접 지킨다. 락을 통과한 뒤 Batch가 같은 파라미터 중복을 던지면 `translateRace`가 409로 바꾼다.

## 6. 용어 풀이

### `@Qualifier("collectJobOperator")`

Spring은 객체(빈)를 만들어 두고 타입으로 찾아 넣는다. `JobOperator` 타입이 둘이다.

| 이름 | 성격 |
|---|---|
| Boot가 만든 기본 operator | 동기: 수집이 끝날 때까지 기다림 |
| `collectJobOperator`(우리가 만듦) | 비동기: 접수만 하고 바로 돌아옴 |

`@Qualifier`는 이름으로 정확히 지정하는 표시다. 비동기 operator는 `defaultCandidate = false`로 등록해
이름을 지정하지 않는 곳(기존 수집 테스트)에는 끼어들지 않게 했다.

### `JobRepository`

Batch 장부(`BATCH_*`)를 읽고 쓰는 담당자다. Job이 도는 동안 진행 기록을 적는 일도 이 객체가 한다. 우리 코드는 두 곳에서만 쓴다.

| 메서드 | 쓰는 곳 |
|---|---|
| `getJobExecution(번호)` | 재시작 때 이전 파라미터, 복구 때 대상 실행을 읽음 |
| `update(실행)` | 복구한 실행의 종료 코드를 `RECOVERED`로 저장 |

### `collectJobOperator` 조립 코드(`CollectLaunchConfig`)

`JobOperator`는 Job을 시작·재시작·복구하는 리모컨이다. 비동기로 도는 리모컨을 조립한다.

| 부분 | 쉬운 설명 |
|---|---|
| `SimpleAsyncTaskExecutor("collect-")` | 일을 별도 스레드에서 돌리는 일꾼. 접두사는 로그 구분용 |
| `setConcurrencyLimit(1)` | 일꾼이 동시에 1건만 처리(전체 동시 실행 금지의 보조 장치) |
| `MapJobRegistry` | "Job 이름 → Job 객체" 명부. 재시작할 때 이름으로 Job을 찾는 데 필요 |
| `JobOperatorFactoryBean` | 재료(`jobRepository`, 명부, 트랜잭션 관리자, 일꾼)를 조립해 operator를 만드는 공장 |

비동기로 만든 이유: 시작 API가 수집이 끝날 때까지 몇 분 기다리면 안 되므로 접수하고 바로 202와 실행 번호를 준다.

### Spring Batch는 어떤 기술인가

Spring 생태계의 공식 프로젝트다(`spring-boot-starter-batch-jdbc`). JPA 같은 "DB 접근 기술"이 아니라
**오래 걸리는 대량 작업을 안전하게 돌리는 프레임워크**다. 읽기·가공·쓰기를 chunk로 나누고, 실패하면 이어서 하도록 진행 기록을 남긴다.
그 기록을 JDBC로 DB에 저장하는 것이 `BATCH_*` 표다. 목적은 대량 작업 실행이고 DB는 기록 수단이다.

### `@Operation(operationId = "createCollectExecution", summary = "수집 실행 요청")`

API 문서(OpenAPI)에 이 엔드포인트를 어떻게 적을지 알려 주는 설명표다. 실행 동작에는 영향이 없다.

| 속성 | 의미 |
|---|---|
| `operationId` | 이 API의 고유한 이름. 겹치면 안 된다 |
| `summary` | 문서에 표시되는 한 줄 제목 |

이 이름은 두 곳에서 실제로 쓰인다.

1. 프론트 코드 생성: `openapi.yaml`을 읽는 생성기(orval)가 `createCollectExecution` 함수와 `useCreateCollectExecution` 훅을 만든다. 이름을 바꾸면 프론트가 깨진다.
2. 계약 대조 테스트: 백엔드 코드에서 만든 문서와 `openapi.yaml`이 같은지 `OpenApiContractTest`가 `operationId`로 짝을 맞춘다.

## 7. 알려진 한계 모음

| 항목 | 내용 |
|---|---|
| 서버 한 대 전제 | 락과 "서버 기동 시각" 판정은 서버가 한 대라는 전제다. 재배포로 옛·새 컨테이너가 겹치면 새 서버가 옛 서버에서 도는 실행을 "중단됨"으로 볼 수 있다 |
| 경합 경로 | 락 통과 뒤 Batch가 던지는 "이미 실행 중/완료" 예외를 409로 바꾸는 `translateRace`는 자동 테스트가 없다(락이 먼저 막는 보조 장치) |
| `ABANDONED` | 이 서비스가 만들지 않는 상태. 화면에는 실패로 보이고 재시작 버튼은 꺼지지만 API로 직접 요청하면 500이 날 수 있다 |
| 토큰 | 지금은 정적 배치 토큰 하나만 검사한다. 관리자 JWT는 Task 054에서 합친다 |
| 락 대기 | 타임아웃·공정성이 없다 |

## 8. 읽는 순서

| 순서 | 파일 | 보는 것 |
|---|---|---|
| 1 | `admin/controller/AdminCollectController` | 요청 5개의 입구 |
| 2 | `collect/job/CollectExecutionRules` | 모든 판정의 기준(가장 중요) |
| 3 | `admin/service/CollectExecutionService` | 시작·재시작·복구의 순서와 락 |
| 4 | `collect/job/CollectJobLauncher` | Job을 실제로 시작·재시작·복구 |
| 5 | `collect/job/CollectLaunchConfig` | 비동기 실행기 조립 |
| 6 | `collect/job/CollectExecutionQuery` | `BATCH_*` 조회 SQL |
| 7 | `admin/service/CollectExecutionQueryService` | 이력·변경 응답 만들기 |
| 8 | `global/security/AdminBatchTokenFilter` | 토큰 검사 |
| 9 | `collect/job/ServerBootTime`, `admin/service/model/CollectViewContext` | 기동 시각, 요청당 맥락 |
