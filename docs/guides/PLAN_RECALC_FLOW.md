# 매월 재계산 흐름 (Task 069)

> 사용자가 요청해 만든 설명 문서다. 근거 문서가 아니고 **코드가 기준**이다(규칙의 근거는 PRD·ERD·TECH). 2026-10-17 기준.
> 한 줄 요약: 수집 Job이 정상으로 끝나면 서버가 스스로 저장된 플랜(ACTIVE)을 다시 계산하고, 무엇이 왜 바뀌었는지 기록한다. 사용자는 그 기록을 `GET /api/me/plan-changes`로 본다.

## 1. 먼저 알아 둘 말

| 말 | 뜻 |
|---|---|
| 수집 Job (`collectJob`) | TMDB에서 영화·드라마 정보를 모으는 일. 제공처 목록 → 영화 후보 → 드라마 후보 → 영화 상세 → 드라마 상세 → 제공 상태 판정의 Step 6개 |
| 재계산 Job (`planRecalcJob`) | 이번에 새로 만든 Job. 저장된 플랜을 다시 계산하는 일. Step 1개 |
| Job / Step / Tasklet | Job은 일 묶음(레시피), Step은 그 안의 단계, Tasklet은 Step에서 실제로 일하는 코드 |
| JobParameters / JobExecution | Job을 시작할 때 넘기는 입력값(주문서) / Job을 한 번 돌린 기록(영수증, DB에 저장) |
| `JobOperator` | Job을 시작해 주는 Spring Batch 담당자. `operator.start(Job, 입력값)` |
| 플랜 상태 | DRAFT = 계산만 한 임시 결과, ACTIVE = 사용자가 저장한 "내 현재 플랜"(사용자당 최대 1개), ARCHIVED = 예전 ACTIVE가 보관된 것 |
| 이벤트 | "수집이 끝났다"는 방송. 보내는 쪽과 받는 쪽이 서로를 몰라도 된다 |

## 2. 큰 그림

```
[서버 켤 때]   부품 조립(Config)  ─ 수집 Job에 "끝나면 알려 줘" 감지기를 붙이고, 재계산 Job과 시작 담당자를 만든다
[수집 끝날 때] 감지기(afterJob) → 방송 → 수신기(Trigger) → 시작 담당(Launcher) → 재계산 Job
                                                                  └ 대상 플랜 고르기 → 플랜마다 재계산 → 변경 요약 기록
[관리자 복구]  POST /api/admin/plans/monthly/executions → 시작 담당(Launcher) → 위와 같은 재계산 Job
[사용자가 볼 때] GET /api/me/plan-changes → 기록 읽기
```

두 Job은 서로 독립이고, 재계산 Job은 스스로 시작하지 않는다. 수집이 끝날 때 나온 방송이나 관리자 요청이 있어야 시작한다. "수집이 먼저, 재계산이 나중"을 정하는 별도 순서 설정은 없고, 방송이 두 Job을 이어 준다. 두 Job이 겹쳐 도는 것은 양쪽에서 막는다: 수집이 돌고 있으면 재계산을 시작할 수 없고, 재계산이 돌고 있으면 수집을 시작·재시작할 수 없다(9절).

## 3. 서버를 켤 때: 부품 조립

| 파일 | 하는 일 |
|---|---|
| `collect/job/CollectJobConfig` | 수집 Job을 조립하면서 `.listener(collectCompletionListener)` 한 줄로 감지기를 붙인다. 이 등록이 없으면 `afterJob`은 불리지 않는다 |
| `plan/batch/PlanRecalcJobConfig` | 재계산 Job(`planRecalcJob`)과 Step(`planRecalcStep`)을 만들고, Step의 일꾼으로 `PlanRecalcTasklet`을 등록한다(`.tasklet(tasklet)`) |
| `plan/batch/PlanRecalcLaunchConfig` | 재계산 전용 `JobOperator`(`planRecalcJobOperator`)를 만든다. 별도 스레드(`recalc-`, 동시 1개)에서 Job을 돌리도록 설정해서, 시작을 요청한 쪽은 기다리지 않는다 |
| `plan/batch/PlanRecalcLauncher` | 위 operator와 Job을 생성자로 받는다. `@Qualifier("planRecalcJobOperator")`로 이름을 지정하는 이유는 수집 쪽에도 `JobOperator`가 있기 때문이다 |

`@Bean`은 "이 메서드가 돌려주는 객체를 Spring 부품으로 등록"하는 표시이고, `@Configuration`은 그런 메서드를 모아 둔 조립 설명서다. 부품을 쓰는 쪽은 `new`로 만들지 않고 생성자로 받는다(생성자 주입, `@RequiredArgsConstructor`가 생성자를 만들어 준다).

## 4. 수집이 끝날 때: 자동 재계산

값 예시: 수집 실행 7번이 정상 종료. 사용자 A는 11월에 넷플릭스를 구독하는 플랜(`planId=10`)을 갖고 있고, 이번 수집에서 "카지노"가 넷플릭스에서 빠졌다.

| 순서 | 파일.메서드 | 일어나는 일 |
|---|---|---|
| 1 | `CollectCompletionListener.afterJob` | Spring Batch가 수집 Job이 끝나는 순간 **한 번** 불러 준다. 상태가 `COMPLETED`가 아니거나 일부 범위 수집(`scope=AVAILABILITY_TV`)이면 `return`(방송 없음, 일부 범위 수집은 info 로그만). 아니면 `CollectCompletedEvent(7)`을 방송한다. 받는 쪽 예외는 여기서 잡아 로그만 남긴다 |
| 2 | `PlanRecalcTrigger.onCollectCompleted` | `@EventListener`가 붙은 메서드. Spring이 이벤트 타입이 같은 메서드를 찾아 불러 준다. 설정 `app.plan-recalc.auto-start`가 꺼져 있으면 아무것도 안 한다(테스트 기본값) |
| 3 | `PlanRecalcLauncher.startAfterCollect` → `start` | 락을 잡고 (a) 재계산이 이미 실행 중인지 (b) 다른 수집이 실행 중인지(7번 자기 자신은 제외, 이 시점엔 DB에 아직 실행 중으로 남아 있다) 검사한다. 걸리면 `PlanRecalcAlreadyRunningException` |
| 4 | `PlanRecalcLauncher.start` | `operator.start(planRecalcJob, 입력값)`. 입력값은 `collectExecutionId=7`, `trigger=COLLECT`, `requestedAt=지금`. DB에 실행 기록을 만들고 별도 스레드에서 Job이 돈다. 영수증 번호만 돌려주고 끝난다. **여기까지가 우리 코드의 호출이다** |
| 5 | Spring Batch → `PlanRecalcTasklet.execute` | Step이 Tasklet의 `execute`를 부른다. 우리 코드가 직접 부르는 곳은 없고(테스트만 직접 부른다), `implements Tasklet`으로 "execute를 만들어 둘 테니 불러라"고 약속해 둔 것이다 |
| 6 | `PlanRecalcTasklet.execute` (1) | 수집 7번이 DB에 `COMPLETED`로 저장될 때까지 0.2초마다 확인하며 최대 60초 기다린다. `afterJob` 시점에는 저장 전이고, 플랜의 데이터 기준 시각에 이번 수집이 들어가야 하기 때문이다 |
| 7 | `PlanRecalcTargetService.findTargetPlanIds` | 대상 플랜 ID를 고른다(5절) |
| 8 | `PlanRecalcTasklet.execute` (2) | 플랜마다 `PlanRecalcService.recalcOne`을 부른다. 예외가 나면 error 로그만 남기고 **다음 플랜으로 넘어간다**. 대상 수는 Step의 읽은 건수로 센다 |
| 9 | `PlanRecalcService.recalcOne` | 5절 이후 |

## 5. 재계산 대상은 누구인가

대상은 **ACTIVE 플랜 중 다시 계산할 이유가 있는 것**이다. DRAFT는 임시 결과라 건드리지 않는다.

| 이유 | 예시 |
|---|---|
| a. 플랜의 첫 달이 이번 달 1일(KST)보다 앞이다 | 10월에 저장한 3개월 플랜이 11월이 됐다. 3개월 롤링이라 달이 바뀌면 계획 창을 밀어야 하므로 전부 대상 |
| b. 플랜 안 작품 중 하나라도 플랜의 `data_as_of` 이후 제공 상태가 바뀌었다 | "카지노"가 넷플릭스에서 빠짐 → 사용자 A의 `planId=10`이 대상 |

둘 다 아니면 건드리지 않는다. 제공 상태 비교는 다른 도메인 리포지토리를 직접 쓰지 않고 `AvailabilityChangeQueryService`로 작품별 최근 변경 시각을 받아 Java에서 한다. 이름은 "매월"이지만 실제로는 수집이 `COMPLETED`로 끝날 때마다 돌고, 월이 바뀐 플랜은 a로 전부 대상이 된다.

## 6. 플랜 한 건의 재계산

`PlanRecalcService.recalcOne(10)`은 트랜잭션이 없는 진입점이고, 쓰기만 `PlanRecalcWriter`의 쓰기 트랜잭션 한 번에 묶는다. 엔진 계산(CPU)이 트랜잭션 안에서 DB 연결을 쥐지 않게 하려는 것이다.

| 순서 | 일어나는 일 |
|---|---|
| 1 | 그 플랜이 아직 ACTIVE인지 확인한다. 그 사이 사용자가 직접 저장해 보관됐다면 건너뛴다 |
| 2 | `PlanInputService.assemble`로 새 입력을 모은다. 설정이 없거나 다 보지 않은 찜이 없어 계산할 수 없으면 건너뛴다(info 로그) |
| 3 | `PlanSolveService.solve`로 세 유형을 엔진으로 풀고 이전 플랜과 같은 유형의 결과만 쓴다 |
| 4 | `PlanRecalcWriter.write` (쓰기 트랜잭션 한 번): 사용자 행을 잠근다 → 읽을 때 본 ACTIVE가 그대로인지 다시 확인(아니면 건너뜀) → 이전 ACTIVE를 `archive()`하고 `flush()`(사용자당 ACTIVE 1개 인덱스 때문에 새 것보다 먼저 반영) → `Plan.createRecalculated`로 새 ACTIVE 저장(`previous_plan_id`=이전 플랜) |
| 5 | 이전·새 플랜을 같은 모양의 값(`PlanSnapshot`)으로 읽고(`PlanSnapshotLoader`), 이유 근거를 모아(`PlanChangeEvidenceAssembler`: 이전 플랜 이후의 제공 상태 변경, 그 뒤 "다 봤음"으로 체크된 작품, 서비스 이름) `PlanChangeDetector.detect`로 비교한다 |
| 6 | 기록할 변경이 있으면 `recordChangeSummary(JSON)`로 `plan.change_summary`에 남긴다 |
| 7 | 같은 트랜잭션에서 알림 행을 만든다(Task 073). 월이 바뀐 재계산이면 MONTHLY, 월이 그대로이고 변경 요약이 있으며 이번 달 확정 달에 배정된 작품이 그 서비스에서 빠졌으면 PLAN_CHANGE. 알림을 끈 사용자는 만들지 않고, 같은 키는 `ON CONFLICT DO NOTHING`으로 한 번만 만든다. 알림 생성이 실패하면 이 플랜의 재계산도 되돌아간다 |
| 8 | 오래된 ARCHIVED를 벌크로 지운다. 변경 기록이 있는 최근 6개와 바로 이전 1개는 남긴다 |

**변경 요약을 기록하는 조건** (`PlanChangeDetector`, 순수 Java):
1. 두 플랜이 함께 다루는 달(겹치는 달)에서 상품 집합이나 같은 상품의 적용 가격이 다르거나, 작품의 자리(시청 완료 달 + 그 달의 서비스)가 바뀐 변화가 1건 이상 있어야 한다. 새 플랜에만 있는 달만 생긴 경우는 변화로 세지 않는다.
2. 이유가 1건 이상 있어야 한다. 이유는 4종이다: `PRICE_CHANGED`, `TITLE_LEFT_SERVICE`, `TITLE_JOINED_SERVICE`, `MARKED_WATCHED`. 이유가 하나도 없으면 플랜은 갱신하되 요약은 남기지 않는다(찜·예산만 바꿔서 달라진 경우 등).
3. 기록할 때 새 달은 `monthChanges`에 항상 넣는다(`previousCost`는 null).

예시 결과: 요약 "11월 구독이 넷플릭스에서 디즈니+로 바뀌었어요", 이유 `TITLE_LEFT_SERVICE`(카지노가 넷플릭스에서 빠짐).

## 7. 관리자 수동 재실행

`POST /api/admin/plans/monthly/executions` → `AdminPlanRecalcController.createPlanRecalcExecution` → `PlanRecalcLauncher.startManual` → 4절의 4~9번과 같다(`trigger=MANUAL`, `collectExecutionId=0`). 응답은 202와 새 실행 ID. 재계산이나 수집이 실행 중이면 409 `BATCH_ALREADY_RUNNING`. ADMIN JWT와 배치 토큰을 모두 허용하고 일반 사용자는 403이다. 자동 흐름이 실패했을 때 복구하는 입구다.

## 8. 사용자가 변경 내역을 볼 때

`GET /api/me/plan-changes` → `PlanController.listPlanChanges` → `PlanChangeHistoryService.listRecent`가 변경 요약이 기록된 내 플랜(ACTIVE·ARCHIVED)을 최신순 최대 6건 읽고, `PlanChangeSummaryCodec.read`로 JSON을 푼다(읽지 못하는 기록은 목록에서 뺀다) → 내부용 필드(`watchUnitId` 등)를 뺀 응답 DTO로 변환. 기록이 없으면 200과 빈 배열. 재계산은 건드리지 않고 읽기만 한다.

## 9. 조건이 걸린 곳 정리

| 위치 | 조건 | 걸리면 |
|---|---|---|
| `CollectCompletionListener.afterJob` | 수집 상태가 `COMPLETED`가 아님 | 방송을 보내지 않는다 |
| `CollectCompletionListener.afterJob` | `scope=AVAILABILITY_TV` 수집(시즌 제공 상태 판정만 다시 돌리는 일부 범위 수집, 로컬에서만 가능) | 방송을 보내지 않는다(info 로그만) |
| `CollectExecutionService.start`·`restart` (수집 시작 요청) | 재계산이 실행 중 | 409 `BATCH_ALREADY_RUNNING`, 메시지 "재계산이 실행 중이라 수집을 시작할 수 없습니다." |
| `PlanRecalcTrigger.onCollectCompleted` | `app.plan-recalc.auto-start`가 꺼짐 | 시작하지 않는다 |
| `PlanRecalcLauncher.start` | 재계산이나 다른 수집이 실행 중 | `PlanRecalcAlreadyRunningException`(수동 API에서는 409) |
| `PlanRecalcWriter.write` | 읽은 뒤 사용자가 직접 저장해 ACTIVE가 달라짐 | 그 플랜은 건너뛴다 |

## 10. 알려진 한계와 미결

1. **해소: 양방향으로 막는다.** 수집이 돌고 있으면 재계산 시작이 거절되고(`PlanRecalcLauncher`), 재계산이 돌고 있으면 수집 시작·재시작이 거절된다(`CollectExecutionService`가 `PlanRecalcExecutionQuery`로 확인). 이 검사를 `collect`가 아니라 `admin`에 둔 이유는 `plan`이 이미 `collect`를 쓰고 있어서 `collect`가 `plan`을 부르면 서로를 부르는 순환이 되기 때문이다. 수집을 시작하는 경로는 이 서비스뿐이고(`collect.yml` cron도 같은 관리자 API를 지난다), 서버 기동 전에 만들어진 "실행 중" 재계산 기록은 중단된 것으로 보고 막지 않는다. 남은 한계: 두 시작 담당이 서로 다른 락을 쓰므로 거의 동시에 요청이 오면 이론상 아주 짧은 틈이 있다(서버 한 대 전제, 테스트로 겨루지 않았다).
2. **해소(사용자 결정 2026-10-17): `scope=AVAILABILITY_TV` 수집은 `COMPLETED`로 끝나도 재계산을 시작하지 않는다.** 이 수집은 이미 모은 데이터에 새 판정 규칙만 다시 적용하는 로컬 전용 작업이라, 그 결과 반영은 다음 전체 수집 뒤 재계산에 맡긴다. 운영에서는 `scope`가 거부되므로 로컬에서만 생기는 경우다.
3. **간헐 실패 테스트**: `CollectExecutionQueryTest`의 복구 테스트가 두 번 실패했고(실제 값 `COMPLETED`) 재현하지 못했다. 이 기능 때문이라는 증거는 없다. HANDOFF 이월 항목에 있다.
4. 조사(을/를, 이/가)는 한글 받침으로 고른다. 영문·기호로 끝나는 서비스명은 어색할 수 있다. 현재 서비스명은 문제없다.
5. 번들 상품은 Task 076에서 생긴다. 지금은 `PlanChangeProduct.ottServiceIds`가 항상 길이 1이다.

## 11. 읽는 순서

1. `CollectCompletionListener` → `PlanRecalcTrigger` → `PlanRecalcLauncher` (3절~4절의 1~4번)
2. `PlanRecalcJobConfig`, `PlanRecalcLaunchConfig` (조립)
3. `PlanRecalcTasklet` → `PlanRecalcTargetService` (4절의 5~8번, 5절)
4. `PlanRecalcService` → `PlanRecalcWriter` (6절)
5. `PlanChangeDetector` (변경 판정, 가장 길다)
6. `PlanChangeHistoryService` (8절)
