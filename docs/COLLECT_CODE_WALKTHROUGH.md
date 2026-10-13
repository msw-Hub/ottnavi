# 수집 코드 읽기 안내 (Task 043)

> 이 문서는 수집 Job 코드를 **호출 순서대로 따라 읽는 법**을 안내한다. "무엇을 하는 Job인가"는 `docs/COLLECT_JOB_FLOW.md`에 있다.
> 경로는 `backend/src/main/java/com/ottnavi/` 아래다. 줄 수와 메서드 이름은 이 문서를 쓴 시점의 코드 기준이다.

## 1. 한눈에

### 호출 흐름

```
collectJob (collect/job/CollectJobConfig)           Job: 순서만 정한다
 ├─ providerListStep        Tasklet 한 번 실행 ──→ ProviderListTasklet ──→ ProviderCollectService ──→ DB(tmdb_provider)
 ├─ movieCandidateStep ┐    chunk(20) 반복
 └─ tvCandidateStep    ┘     Reader.read() x20 ──→ Writer.write(20건)
                              │                      │
                              │ TMDB discover 호출    └─→ TitleCollectService ──→ Title(엔티티) ──→ DB(title)
                              └─ 대상 서비스 목록은 ProviderCollectService에서 받는다
```

### 읽는 순서

| 순서 | 파일 | 줄 수 | 읽는 이유 |
|---|---|---|---|
| 1 | `collect/job/CollectJobConfig` | 26 | Job이 어떤 Step을 어떤 순서로 부르는지 |
| 2 | `collect/step/CollectStepConfig` | 57 | Step마다 무엇을 Reader·Writer로 쓰는지, chunk 크기 |
| 3 | `collect/step/ProviderListTasklet` | 56 | 가장 단순한 Step(한 번 실행) |
| 4 | `provider/service/ProviderCollectService` | 59 | 제공처 저장, 그리고 "어느 서비스를 discover할지" 계산 |
| 5 | `collect/step/CandidateReader` | 106 | 이 Job의 핵심. 쪽 단위로 가져와 한 건씩 내준다 |
| 6 | `collect/step/CandidateMapper` | 67 | TMDB 응답을 우리 값(`CandidateTitle`)으로 바꾼다 |
| 7 | `collect/step/CandidateWriter` | 27 | chunk를 받아 서비스에 넘기기만 한다 |
| 8 | `catalog/service/TitleCollectService` | 43 | 있으면 갱신, 없으면 저장 |
| 9 | `catalog/domain/entity/Title` (`createFromSummary`, `refreshSummary`) | 162 | 값을 실제로 바꾸는 곳 |

`CollectPoolMonitor`(54줄)는 DB 연결 사용량을 로그로 남기는 보조 도구라 처음에는 건너뛰어도 된다.

## 2. 코드를 따라가기

**Config (1·2번).** `CollectJobConfig.collectJob`은 세 줄이 전부다: `.start(providerListStep).next(movieCandidateStep).next(tvCandidateStep)`.
Step 3개를 파라미터로 받고(이름이 Bean 이름이다), 순서만 정한다. 실제 Step 조립은 `CollectStepConfig`에 있다.
`candidateStep`은 `chunk(CHUNK_SIZE)`(20) + `reader` + `writer`를 붙여 만든 Step이다. 영화와 드라마는 Reader에 넘기는 "한 쪽을 가져오는 함수"만 다르다
(`tmdbClient.discoverMovies` / `discoverTvs`).

**ProviderListTasklet (3번).** `execute` 하나가 전부다. TMDB의 KR 제공처 목록(영화·드라마 두 번 호출)을 모아 `ProviderCollectService.registerDiscovered`로 넘긴다.
TMDB 호출을 먼저 끝내고 DB 반영만 서비스(트랜잭션)에 맡긴다.

**ProviderCollectService (4번).** `registerDiscovered`는 처음 보는 제공처만 `UNMAPPED`로 저장한다. 이미 있는 행은 건드리지 않는다(관리자가 정한 `MAPPED`·`IGNORED`를 지우지 않으려고).
`findDiscoverTargets`는 `MAPPED`인 제공처를 우리 서비스별로 묶어 `"8|119"` 같은 문자열(`DiscoverTarget.withWatchProviders`)을 만든다. TMDB에서 `|`는 "또는"이다.

**CandidateReader (5번).** `read()`는 버퍼가 비어 있으면 `fetchNextPage()`로 다음 쪽을 채우고, 버퍼에서 한 건 꺼내 준다. 더 읽을 쪽이 없으면 `null`을 돌려주고, Spring Batch는 `null`을 "끝"으로 안다.
`fetchNextPage()`는 지금 서비스(`targetIndex`)의 `nextPage`쪽을 TMDB에서 받아 `buffer`에 넣고, 마지막 쪽이면 다음 서비스로 넘어간다(`MAX_PAGE` 500쪽까지).
`open()`은 상태를 처음으로 되돌리고, `update()`는 **일부러 비워 두었다**. `update()`는 "지금까지 어디까지 읽었는지 저장"하는 자리인데, 저장하면 실패 후 재시작이 중간부터 이어진다.
이 Job은 "실패하면 처음부터 다시 읽고, 이미 저장된 작품은 건너뛴다"를 규칙으로 삼았기 때문에 저장하지 않는다.

**CandidateWriter (7번).** `write(chunk)`는 받은 항목을 `TitleCollectService.saveCandidates`에 넘기고 로그만 남긴다.

**TitleCollectService (8번).** 후보마다 `findByMediaTypeAndTmdbId`로 먼저 찾는다. 있으면 `refreshSummary`, 없으면 `Title.createFromSummary`로 만들어 `save`한다.
있는 경우 `save`를 부르지 않는 점에 주목한다. 조회한 엔티티는 영속 상태라 **값이 바뀐 경우에만 커밋 때 UPDATE가 자동으로 나간다(더티 체킹)**.

**Title (9번).** `createFromSummary`는 새 엔티티를 만들고, `refreshSummary`는 포스터·배경·인기도·평점이 **달라졌을 때만** 대입한다.
같은 값이면 대입하지 않으니 UPDATE가 나가지 않는다(`BigDecimal`은 자릿수가 달라도 같은 값이면 같다고 본다).

## 3. 개념 정리

| 용어 | 뜻 | 이 코드에서 |
|---|---|---|
| Job | 실행 단위 하나. Step들의 순서 | `collectJob` |
| Step | Job 안의 한 단계 | `providerListStep`, `movieCandidateStep`, `tvCandidateStep` |
| Tasklet | "한 번 실행하고 끝"인 Step의 몸체 | `ProviderListTasklet` |
| chunk | 여러 건을 모아 한 트랜잭션으로 저장하는 묶음 | `chunk(20)` |
| Reader | 한 건씩 꺼내 준다. 없으면 `null` | `CandidateReader` |
| Writer | chunk가 차면 한꺼번에 받는다 | `CandidateWriter` |

### 버퍼와 chunk

두 개의 20이 나오지만 **서로 무관하다**.

- `buffer`의 20: TMDB가 discover 한 쪽을 **20건으로 주기 때문**이다(TMDB의 사정).
- `chunk(20)`: 우리가 정한 값이다. 한 쪽(20건)을 한 번에 커밋하려고 맞춰 두었을 뿐, 다르게 정해도 동작한다.

실제 흐름은 이렇다. Spring Batch가 `read()`를 호출하면 버퍼가 비었을 때만 TMDB를 부르고(쪽당 1회), 이후 19번은 버퍼에서 꺼내기만 한다.
**저장(Writer)은 `read()`를 20번 모았을 때만** 일어난다. 마지막 쪽이 20건보다 적으면 Reader가 **다음 서비스의 첫 쪽까지 이어서** 읽어 chunk를 채우므로, chunk는 서비스 경계를 넘을 수 있다.
마지막에 남은 건수가 20 미만이면 `read()`가 `null`을 돌려준 시점에 남은 만큼만 저장한다.

### 왜 `CandidateReader`를 `new`로 만드는가

Reader는 `targets`, `targetIndex`, `nextPage`, `buffer` 같은 **읽는 위치 상태**를 필드로 가진다. 영화 Step과 드라마 Step이 한 객체를 같이 쓰면 위치가 섞인다.
그래서 Bean(`@Component`)이 아니라 `CollectStepConfig`에서 Step마다 `new`로 따로 만든다. 반대로 `CandidateWriter`는 상태가 없어 하나를 공유해도 되므로 `@Component`다.

## 4. 헷갈리기 쉬운 점

**`record`·DTO·엔티티.** `record`는 자바 **문법**(값만 담는 불변 클래스), DTO는 **역할**(주고받는 틀), 엔티티는 **DB 테이블과 연결된** 클래스다. 셋은 같은 층위가 아니다.
DTO를 `record`로 쓰는 건 흔하다. 엔티티는 `record`로 쓰지 않는다: JPA가 객체를 만들고 값을 바꾸려면 기본 생성자와 가변 필드(더티 체킹)가 필요한데, `record`는 불변이라 맞지 않는다.

**값이 틀을 바꿔 가는 흐름.**

```
TMDB JSON → infra/tmdb/dto(TmdbMovieSummary) → service/model(CandidateTitle) → 엔티티(Title) → DB(title)
                                                                                    └→ dto/response(OttServiceResponse) → 프론트 JSON
```

각 칸은 **그 경계의 약속**만 안다. TMDB 응답 틀은 `infra` 밖으로 나가지 않고(`CandidateMapper`가 변환), 엔티티는 컨트롤러 밖 응답으로 그대로 나가지 않는다(`OttServiceResponse.from`이 변환).

**`dto`와 `service/model`을 나누는 이유.** `dto`는 **프론트와의 약속**(`openapi.yaml`)이라 필드 이름을 마음대로 못 바꾼다. `service/model`은 **서버 안에서 서비스와 호출하는 쪽의 약속**이라
내부 사정으로 바꿔도 된다. 바뀌는 이유가 달라서 폴더를 나눴다. `dto`와 이름이 겹치지 않게 `model`을 썼다.

**`domain`(규칙)과 `service`(흐름).** `domain/rule`은 "무엇이 맞는가"를 정하는 순수 규칙(예: 제공 상태 판정, 러닝타임 계산)이고, `service`는 "어떤 순서로 부르는가"를 조율한다(트랜잭션, 리포지토리, 규칙 호출).
`collect`에는 `domain`·`service` 폴더가 없다. 업무 도메인이 아니라 **Job을 실행하는 입구**이기 때문이다. 업무는 `catalog`·`provider` 서비스를 호출해 맡긴다.

**이전 프로젝트(CodeHansabari) 구조와의 대응.**

| 이전 | 이 프로젝트 |
|---|---|
| controller | `controller` |
| service | `service` |
| dto | `dto/{request,response}` (서버 내부 값 묶음은 `service/model`) |
| entity·repository·enums | `domain/entity`·`domain/repository`·`domain/enums` |
| client | `infra/tmdb` |
| scheduler | `collect` (Job 실행 입구) |

**`flatrate`와 매핑표.** `flatrate`는 TMDB가 "구독형(월정액)"을 가리키는 값이다. 후보 수집은 구독형만 찾도록 `TmdbClient`가 고정해서 넘긴다(대여·구매 제외).
매핑표는 `tmdb_provider` 테이블이다. TMDB 제공처 번호(예: 8)가 우리 서비스(예: 넷플릭스)의 어느 쪽인지를 `MAPPED`로 적어 둔다. 한 서비스에 번호가 여럿일 수 있어 `|`로 묶고,
`UNMAPPED`(처음 봄)·`IGNORED`(우리 7곳이 아님)는 discover 대상에서 빠진다.

**복합 키와 `Serializable`, 복합 키와 조인은 다르다.** `TitleGenre`는 기본키가 `(title_id, genre_id)` 두 칸이라 키 클래스 `TitleGenreId`가 따로 있다.
JPA는 이 키 객체를 `equals`/`hashCode`로 비교하고 세션에 보관하므로 `Serializable`과 두 메서드가 필요하다. `@MapsId`는 키 안의 값을 연관 객체(`Title`, `Genre`)에서 가져오라는 뜻이다.
"복합 키"는 **키가 몇 칸인가**의 이야기이고, "조인"은 **테이블을 합쳐 읽는 쿼리** 이야기라 서로 다른 개념이다.
이 연결 테이블은 복합 키를 쓰는 이유가 중복 연결 방지(같은 작품·장르 쌍이 두 번 들어가지 않게)다.

## 5. 한계와 확인하지 못한 것

- 이 문서는 코드를 읽고 쓴 안내라서 줄 수·메서드 이름이 코드가 바뀌면 틀어질 수 있다(Task 044가 Step을 더 붙이면 `collectJob`이 길어진다).
- "실패 후 `restart`하면 처음부터 다시 읽는다"는 코드 구조로 설명했고, `restart` 동작 자체를 이번에 직접 실행해 확인하지는 않았다.
- `TitleGenre`는 Task 044 이후 상세 수집에서 쓰인다. 이번 수집 흐름(043)에서는 아직 호출되지 않는다.
- 이전 프로젝트(CodeHansabari) 대응표는 사용자가 알려 준 폴더 이름 기준이며 그 저장소를 직접 확인하지는 않았다.
