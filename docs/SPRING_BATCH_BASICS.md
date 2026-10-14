# Spring Batch 기본 개념 (수집 Job을 읽기 위한 최소 지식)

> 근거 문서가 아니고, 수집 Job 코드(`collect/`)를 읽기 위한 설명이다. Job·Step·Reader·Processor·Writer는 자바 문법이 아니라 **Spring Batch 라이브러리가 정해 둔 틀**이다. 라이브러리 소스를 읽을 필요는 없고 아래 약속만 알면 된다.
> 마지막 갱신: 2026-10-14 (Task 044 반영 기준)

## 1. 용어 8개와 코드 위치

| 용어 | 뜻 | 코드에서 보이는 곳 |
|---|---|---|
| Job | 하나의 큰 작업(수집 전체) | `collect/job/CollectJobConfig`의 `collectJob` |
| Step | Job을 이루는 단계. 순서대로 실행된다 | `movieDetailStep`, `tvDetailStep`, `availabilityStep` 등 |
| chunk | N건을 읽어 가공한 뒤 **한 번에 저장**하는 묶음 | `.chunk(CHUNK_SIZE)` (상세 Step은 10건) |
| ItemReader | 한 건씩 읽어 준다 | `JpaPagingItemReader`(DB에서 읽음), `CandidateReader`(TMDB에서 읽음) |
| ItemProcessor | 한 건을 가공한다. **null을 돌려주면 그 건은 저장하지 않고 건너뛴다** | `MovieDetailProcessor`, `TvDetailProcessor`, `AvailabilityProcessor` |
| ItemWriter | chunk를 받아 저장한다 | `DetailWriter`, `AvailabilityWriter`, `CandidateWriter` |
| JobParameters | 실행할 때 넘기는 값. 같은 값이면 같은 실행으로 본다 | `targetDate`, `tier` (`CollectJobParameters`) |
| restart | 실패한 실행을 이어서 한다. 읽은 위치를 DB에 저장해 둔다 | `.saveState(true)`, `BATCH_*` 테이블(V4 마이그레이션) |

## 2. Step 하나의 모양

`DetailStepConfig`의 조립 코드가 Spring Batch 틀의 전부다.

```java
new StepBuilder("movieDetailStep", jobRepository)
        .<DetailTarget, TitleDetail>chunk(CHUNK_SIZE)   // 읽는 틀 → 내보내는 틀
        .transactionManager(transactionManager)         // chunk마다 트랜잭션
        .reader(movieTitleReader)                       // 읽기
        .processor(new MovieDetailProcessor(...))       // 가공
        .writer(writer)                                 // 저장
        .build();
```

`<DetailTarget, TitleDetail>`은 "Reader가 `DetailTarget`을 읽고 Processor가 `TitleDetail`로 바꿔 Writer가 저장한다"는 뜻이다. Processor와 Writer가 어떤 틀을 주고받는지는 이 한 줄에서 읽힌다.

## 3. 한 건이 지나가는 길 (예시 값)

chunk가 10건이면, Reader가 10건을 읽고 → Processor가 한 건씩 가공하고 → Writer가 10건을 한꺼번에 저장한다.

| 순서 | 주체 | 값 |
|---|---|---|
| 1 | Reader | `DetailTarget(titleId=12, TV, tmdbId=90001)` |
| 2 | Processor | TMDB를 불러 `TitleDetail(시즌 1개, 장르 2개, ...)` 로 바꿈 |
| 3 | Writer | 10건을 모아 서비스에 넘김 → DB에 한 트랜잭션으로 저장 |
| 4 | Spring Batch | chunk가 성공하면 "여기까지 했다"는 위치를 `BATCH_*` 테이블에 적음 |

Processor가 TMDB 404를 만나 `null`을 돌려주면 그 작품은 2번에서 빠지고, 나머지 9건만 저장된다.

## 4. 실패하면 무슨 일이 일어나나

| 상황 | 동작 |
|---|---|
| chunk 안에서 예외 | 그 chunk만 롤백된다. 앞 chunk는 이미 저장돼 있다 |
| Step 실패 | Job이 `FAILED`로 끝난다. 같은 파라미터로 `restart`하면 실패한 Step을 이어서 한다 |
| 이어서 읽기 | Reader가 저장해 둔 위치(예: "30건 읽음")부터 읽는다. 롤백된 chunk만 다시 처리한다 |
| JVM이 갑자기 꺼짐 | 실행이 `STARTED`로 남는다. 먼저 `recover`로 실패 상태로 바꾼 뒤 `restart`한다(Task 045 관리자 API) |
| 같은 파라미터로 완료된 Job을 다시 실행 | 거부된다(완료 409). 날짜를 바꿔야 새 실행이다 |

## 5. 이 프로젝트에서 자주 보는 조립 코드 읽는 법

| 코드 | 뜻 |
|---|---|
| `.name("movieTitleReader")` | 읽은 위치를 저장할 때 쓰는 이름표. 없으면 위치 저장이 되지 않는다 |
| `.saveState(true)` | 읽은 위치를 저장한다(재시작 때 이어 읽기) |
| `.pageSize(CHUNK_SIZE)` | 한 번에 DB에서 읽어 오는 건수. chunk와 같게 두면 재시작 위치가 맞는다 |
| `.transactionManager(...)` | 지정하지 않으면 Batch 6 기본값이 "트랜잭션 없음"이라 저장이 묶이지 않는다 |
| `.listener(processor)` | Step이 시작될 때 `beforeStep`을 불러 준다(예: 기준일 `targetDate`를 받는다) |
| `@Bean` Reader를 따로 만듦 | 상태를 가진 Reader를 Step마다 따로 두려는 것 |

## 6. 이 프로젝트의 특이점

- 원래 chunk 안에서는 외부 호출(TMDB)을 하지 않는 것이 규칙인데, 수집 배치만 예외다(TECH T-2). 그래서 chunk를 작게(10건) 두고 TMDB 호출 속도를 제한한다.
- 읽는 목록이 도중에 바뀌면 페이지가 밀리므로, 정렬은 `id` 순으로 하고 Step이 갱신하는 컬럼(`fetched_at` 등)으로 거르지 않는다(TECH 3절).
- Reader가 읽은 위치를 필드로 가진다. 날짜가 다른 Job이 동시에 돌면 서로의 위치를 덮어쓰므로 동시 실행을 막는다(Task 045).

## 7. 더 읽을 곳

| 문서 | 내용 |
|---|---|
| `docs/COLLECT_JOB_FLOW.md` | 후보를 모으는 Step 3개의 흐름(043) |
| `docs/COLLECT_DETAIL_FLOW.md` | 상세·시즌·판정 Step 3개의 흐름(044) |
| `docs/COLLECT_CODE_WALKTHROUGH.md` | 후보 수집 코드를 호출 순서대로 읽기 |
| `docs/PACKAGE_MAP.md` | 폴더 지도 |
