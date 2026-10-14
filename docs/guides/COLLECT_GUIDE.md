# 수집 Job 가이드 (Task 043·044·045)

> 2026-10-14 작성(043 설명 문서 3개를 합치고 044를 반영했다), 2026-10-15 045(관리자 실행·복구 API) 절을 11절에 더했다. 수집 Job `collectJob`이 무엇을 하고 코드를 어떤 순서로 읽으면 되는지 설명한다. 근거 문서가 아니고 코드가 기준이다.
> 규칙의 근거는 PRD FR-02·FR-18·5.4·5.5, TECH 1·3절·T-2, ERD A 영역, `docs/guides/AVAILABILITY_RULES.md`이다. Spring Batch 용어는 `docs/guides/SPRING_BATCH_BASICS.md`를 본다.
> 코드는 `backend/src/main/java/com/ottnavi/` 아래 `collect/`(`job/`·`step/candidate/`·`step/detail/`), `catalog/service/`, `provider/service/`, 테스트는 `CollectJobTest`(11건)·`CollectDetailJobTest`(14건)·`DetailMapperTest`(7건).

## 1. 배경

사용자가 작품을 조회할 때는 TMDB를 부르지 않고 우리 DB만 읽는다(TMDB 호출은 느리고 한도가 있다). 그래서 **수집 Job**이 TMDB에서 작품을 미리 가져와 DB에 쌓는다. Job은 단계(Step)를 순서대로 실행하고, 실행 기록은 `BATCH_*` 표(Flyway V4)에 남는다.

## 2. Step 6개와 각각의 역할

Job은 `collectJob` **하나**이고 Step이 6개다. 043이 Step 1~3, 044가 Step 4~6을 만들었다.

| Step | 이름 | Task | 하는 일 | 저장하는 곳 |
|---|---|---|---|---|
| 1 | `providerListStep` | 043 | TMDB 한국 제공처 **전체 목록**을 받아 처음 보는 것을 저장 | `tmdb_provider` (UNMAPPED) |
| 2 | `movieCandidateStep` | 043 | 우리 서비스에 있는 **영화 후보**의 요약 정보 저장 | `title` (MOVIE) |
| 3 | `tvCandidateStep` | 043 | 같은 방식으로 **드라마 후보** | `title` (TV) |
| 4 | `movieDetailStep` | 044 | 영화 상세·장르·출연진·시청 단위 | `title` 갱신, `watch_unit`, 장르, 출연진 |
| 5 | `tvDetailStep` | 044 | 드라마 상세·시즌·장르·출연진 | 위와 같고 시즌 단위 |
| 6 | `availabilityStep` | 044 | 시청 단위마다 서비스 7곳의 **제공 상태 판정** | `availability`, `availability_change` |

앞 Step이 모두 끝나야 다음 Step이 시작된다. 그래서 상세 Step이 읽는 `title` 목록은 시작 시점에 고정이다. Job 파라미터는 `targetDate`(LocalDate) + `tier`(MVP는 `ALL`만)이고, 값이 같으면 같은 실행으로 본다.

### 호출 흐름

```
collectJob (collect/job/CollectJobConfig)           Job: 순서만 정한다
 ├─ 1 providerListStep       Tasklet 한 번 ──→ ProviderListTasklet ──→ ProviderCollectService ──→ DB(tmdb_provider)
 ├─ 2 movieCandidateStep ┐   chunk(20) 반복: CandidateReader ──→ CandidateWriter ──→ TitleCollectService ──→ DB(title)
 ├─ 3 tvCandidateStep    ┘
 ├─ 4 movieDetailStep ┐      chunk(10) 반복: JpaPagingItemReader ──→ Processor(TMDB 호출) ──→ DetailWriter
 ├─ 5 tvDetailStep    ┘                                                └─→ TitleDetailCollectService ──→ DB
 └─ 6 availabilityStep       chunk(10) 반복: JpaPagingItemReader ──→ AvailabilityProcessor ──→ AvailabilityWriter
                                                                   └─→ AvailabilityCollectService ──→ DB(availability, availability_change)
```

## 3. 후보 Step(2·3)과 판정 Step(6)은 무엇이 다른가

Step 2·3은 영화별 제공처를 받아 오지 않는다. **서비스별로 "이 서비스에 있는 작품 목록"을 받아 오는 것**이다.

| | Step 2·3 (discover) | Step 6 (availabilityStep) |
|---|---|---|
| 묻는 방향 | 서비스 → 작품 목록 | 작품(시청 단위) → 제공처 목록 |
| TMDB 요청 | "제공처 `8\|1796`(넷플릭스)에 있는 한국 구독 영화를 20건씩 주세요" | "영화 603의 한국 제공처를 알려 주세요" |
| 저장하는 것 | 작품만(`title`) | 서비스별 판정(`availability` 7행) |
| 서비스 정보를 저장하나 | 저장하지 않는다 | 저장한다 |

- Step 2·3이 받는 것은 "한국에서 우리 서비스(MAPPED 제공처)의 구독으로 볼 수 있는 작품"이다. 모든 작품이 아니고, 어느 서비스에서 왔는지는 저장하지 않는다.
- Step 6은 **제목이 아니라 TMDB 번호**로 묻는다. 영화는 영화 번호, 시즌은 드라마 번호 + 시즌 번호다.
- 다시 묻는 이유: 서비스 7곳 각각의 있음/없음/모름, 시즌별 제공처, 일반형·광고형 구분, 한국 데이터 유무는 작품별 제공처 조회에서만 알 수 있다. discover는 "어떤 작품을 관리할지 정하는 필터"다.
- 제공처가 없는 것과 404는 다르다. 한국 데이터가 없으면 `UNKNOWN(NO_KR_DATA)`로, 대여·구매만 있으면 `NOT_AVAILABLE`로 저장하고, 404(TMDB가 그 번호를 모름)이면 그 단위를 건너뛰어 기존 상태를 유지한다.

## 4. Step별 설명

**Step 1 `providerListStep`(Tasklet).** `ProviderListTasklet.execute` 하나가 전부다. 영화·드라마 제공처 목록을 두 번 받아 번호 기준으로 합친 뒤 `ProviderCollectService.registerDiscovered`로 넘긴다. 이미 있는 행(MAPPED·IGNORED)은 덮어쓰지 않고 처음 보는 것만 UNMAPPED로 저장한다. 시드로 MAPPED 8행(넷플릭스 일반 8·광고형 1796, 디즈니+ 337, 티빙 1883, 왓챠 97, 웨이브 356, 쿠팡플레이 1881, 애플TV+ 350)이 이미 들어 있다. UNMAPPED는 후보 수집·판정에 쓰이지 않고 관리자 화면(Task 046)에서 정리한다.

**Step 2·3 후보 수집.** `CandidateReader`가 서비스 7곳을 차례로 돌며 discover를 마지막 쪽까지(최대 500쪽) 요청하고 한 건씩 내준다. `CandidateMapper`가 TMDB 응답을 `CandidateTitle`로 바꾸고, `CandidateWriter`가 20건씩 `TitleCollectService.saveCandidates`에 넘긴다. 같은 작품이 여러 서비스에 나오면 `(media_type, tmdb_id)`로 확인해 한 행만 두고, 있으면 포스터·배경·인기도·평점 4개만 갱신한다. 서비스별 요청 값 `with_watch_providers`는 MAPPED 제공처에서 만든다(`8|1796`처럼 `|`는 "또는").

```
GET /3/discover/movie?with_watch_providers=8|1796&watch_region=KR&with_watch_monetization_types=flatrate&language=ko-KR&page=1
{ "page": 1, "total_pages": 3, "results": [ { "id": 900001, "title": "테스트 영화 1", ... } ] }   (드라마는 name)
```

**Step 4 `movieDetailStep`.** `MovieDetailProcessor`가 영화 상세(+출연진)를 받고, 한국어 줄거리가 비었을 때만 영어로 한 번 더 호출한다. `DetailMapper.fromMovie`가 `TitleDetail`로 바꾸고 `DetailWriter` → `TitleDetailCollectService.saveDetails`가 `title` 갱신, 시청 단위(MOVIE 1개), 장르, 출연진을 저장한다.

**Step 5 `tvDetailStep`.** 영화와 같은 뼈대에 시즌 처리가 붙는다. `TvDetailProcessor`가 드라마 상세를 받고, 시즌 0(스페셜)을 뺀 시즌마다 `getTvSeason`을 불러 방영된 회차의 러닝타임을 합산한다. 방영 회차가 0개인 시즌은 단위를 만들지 않는다. 방영 기준일은 Job 파라미터 `targetDate`를 `beforeStep`에서 받는다.

**Step 6 `availabilityStep`.** `AvailabilityProcessor`가 시청 단위마다 제공처를 받고(영화는 영화 제공처, 시즌은 시즌 제공처), `DetailMapper.toKrProviders`가 `KrProviders`로 바꾼다. `AvailabilityCollectService.apply`가 서비스 7곳마다 `AvailabilityJudge`로 판정해 `availability`를 만들거나 갱신하고, 처음 생겼거나 상태가 바뀐 것만 `availability_change`를 쌓는다(처음이면 before가 null). 제공 상태 변경과 이력은 한 트랜잭션이다.

### 값이 바뀌어 가는 순서 (영화 한 편)

1. TMDB 목록: `{"id": 603, "title": "…"}` → `title` 한 행(요약 값만)
2. TMDB 상세: `{"runtime": 118, "genres": [...], "credits": {...}}` → `TitleDetail`(러닝타임 118, 장르, 출연진 상위 10명)
3. TMDB 제공처: KR flatrate `[8, 1883]` → `KrProviders.of({8, 1883})` → `AvailabilityJudge`
4. 결과: `availability` 7행(넷플릭스·티빙 AVAILABLE 2, 쿠팡플레이 UNKNOWN 1(영화는 항상 모름, STRUCTURAL_GAP), 나머지 NOT_AVAILABLE 4), 신규라서 `availability_change` 7줄(before null)

## 5. 규칙과 결정

- 방영된 회차가 0개인 시즌은 저장하지 않는다. 시즌 0(스페셜)은 호출도 하지 않는다.
- `watch_unit.episode_count`는 시즌 상세의 회차 목록 개수다(시즌 요약 값은 쓰지 않는다).
- 한국어 줄거리가 비면 영어로 대체하고, 둘 다 비면 기존 줄거리·언어 표시를 유지한다.
- 장르는 `genre` 표에 시드가 없어 상세 응답의 `{id, 이름(ko)}`로 등록한다. 기존 장르 이름은 덮어쓰지 않는다.
- 장르·출연진·시청 단위는 지우고 다시 넣지 않고 기존 행과 비교해 차이만 반영한다(같은 유니크 키를 지웠다 넣으면 INSERT가 먼저 나가 위반, TECH 6절). 응답에 없는 기존 시청 단위는 건드리지 않는다.
- 이력은 행이 처음 생기거나 `status`가 바뀔 때만 쌓는다(`AvailabilityChangeDetector`, 이유·요금제 플래그만 바뀌면 남기지 않는다). `Availability`는 AVAILABLE이면 `in_standard`·`in_ad_tier` 중 하나가 true여야 한다(엔티티가 검사).
- 쿠팡플레이 영화는 항상 모름(STRUCTURAL_GAP), `tier`는 `ALL`만 허용한다.

## 6. 실패와 재시작

| 상황 | 동작 |
|---|---|
| 작품 상세·시즌이 404 | 그 작품만 건너뛴다. 드라마는 시즌 하나라도 404면 작품 전체를 건너뛰어 부분 저장이 없다. 기존 행은 그대로다 |
| 제공처 조회가 404 | 그 단위만 건너뛴다. 기존 제공 상태·이력을 건드리지 않는다 |
| 5xx·429·타임아웃(재시도 후) | Step이 실패한다. 실패한 chunk는 롤백되고 앞 chunk는 커밋돼 있다 |
| 같은 파라미터로 완료된 Job을 다시 실행 | 거부된다(`JobInstanceAlreadyCompleteException`) |
| 서버가 갑자기 꺼짐 | 실행이 `STARTED`로 남는다. 이번 서버 기동 전에 만들어진 실행이라 화면 상태는 `STOPPED`(중단됨)이다. 복구 API로 실패로 바꾼 뒤 재시작한다(11절) |
| 후보 Step(2·3) 재시작 | **처음부터** 다시 읽는다. Reader가 위치를 저장하지 않고 이미 저장된 작품은 갱신만 하므로 결과가 같다 |
| 상세·판정 Step(4~6) 재시작 | **저장된 위치부터** 이어 읽는다(`JpaPagingItemReader`의 `saveState`). 롤백된 chunk만 다시 처리하고 같은 입력의 재처리는 멱등이다 |

재시작은 테스트로 확인했다: 25편 중 15번째에서 실패 → 첫 10편은 TMDB 호출 1회, 롤백된 11~14번째는 2회, 결과는 25편 모두 저장.

## 7. 호출량

- 영화: 작품당 상세 1회(+줄거리가 빈 경우 영어 1회) + 제공처 1회.
- 드라마: 작품당 상세 1회(+영어 1회) + 시즌마다 (시즌 상세 1회 + 방영 회차가 있으면 제공처 1회).
- 후보 수집: 서비스마다 discover를 쪽 수만큼(20건/쪽).
- 지표: 기존 `tmdb.call`의 `endpoint` 태그(`movie_detail`, `tv_detail`, `tv_season`, `movie_providers`, `tv_season_providers`)와 작품당 시즌 수 분포 `collect.tv.seasons`. 총 호출 수 추정(R11-26)은 Task 048 첫 전체 수집에서 한다.

## 8. 읽는 순서

줄 수는 빈 줄을 뺀 값이고 코드가 바뀌면 달라질 수 있다.

| 순서 | 파일 | 줄 수 | 읽는 이유 |
|---|---|---|---|
| 1 | `collect/job/CollectJobConfig` | 29 | Step 6개의 순서 |
| 2 | `collect/step/candidate/CandidateStepConfig` | 51 | Step 1~3 조립. `candidateStep`은 영화·드라마 공통 조립 도우미 |
| 3 | `collect/step/candidate/ProviderListTasklet` | 51 | 가장 단순한 Step(한 번 실행) |
| 4 | `provider/service/ProviderCollectService` | 60 | 제공처 저장, "어느 서비스를 discover할지" 계산, MAPPED 조회 |
| 5 | `collect/step/candidate/CandidateReader` | 94 | 043의 핵심. 쪽 단위로 가져와 한 건씩 내준다 |
| 6 | `collect/step/candidate/CandidateMapper`, `CandidateWriter` | 57, 23 | 변환과 저장 위임 |
| 7 | `catalog/service/TitleCollectService` | 39 | 있으면 갱신, 없으면 저장 |
| 8 | `catalog/domain/entity/Title` (`createFromSummary`, `refreshSummary`) | 136 | 값을 실제로 바꾸는 곳 |
| 9 | `collect/step/detail/DetailStepConfig` | 107 | Step 4~6 조립, chunk 10, 위치 저장 |
| 10 | `collect/step/detail/MovieDetailProcessor` | 44 | 가장 단순한 상세 Processor |
| 11 | `catalog/service/model/TitleDetail`, `collect/step/detail/DetailWriter` | 28, 23 | 작품 한 건의 결과 틀, 저장 위임 |
| 12 | `catalog/service/TitleDetailCollectService` | 152 | `saveDetails`부터. 장르·출연진·시청 단위 동기화는 나중에 |
| 13 | `collect/step/detail/TvDetailProcessor` | 97 | 시즌 반복 |
| 14 | `collect/step/detail/AvailabilityProcessor`, `AvailabilityWriter` | 34, 30 | 제공처 조회, `jobExecutionId` 전달 |
| 15 | `catalog/service/AvailabilityCollectService` | 110 | 판정 호출과 이력 저장 |
| 16 | `catalog/domain/rule/AvailabilityJudge` | 59 | 판정 규칙 |

필요할 때만: `collect/step/detail/DetailMapper`(189, 변환 모음), `collect/step/CollectPoolMonitor`(47, 연결 사용량 로그), `infra/tmdb/TmdbClient`(157, TMDB 호출 창구).

## 9. 헷갈리기 쉬운 점

**두 개의 20.** `buffer`의 20은 TMDB가 discover 한 쪽을 20건으로 주기 때문이고, `chunk(20)`은 우리가 정한 값이다. 서로 무관하다. 저장(Writer)은 `read()`를 20번 모았을 때만 일어나고, 마지막 쪽이 20건보다 적으면 Reader가 다음 서비스의 첫 쪽까지 이어 읽으므로 chunk는 서비스 경계를 넘을 수 있다.

**Reader를 `new`로 만드는 이유.** `CandidateReader`는 `targets`, `targetIndex`, `nextPage`, `buffer` 같은 읽는 위치 상태를 필드로 가진다. 영화 Step과 드라마 Step이 한 객체를 같이 쓰면 위치가 섞이므로 Step마다 따로 만든다. `CandidateWriter`와 `AvailabilityProcessor`는 상태가 없어 하나를 공유해도 되므로 `@Component`다.

**Step 번호와 `// 1.` 주석 번호는 다르다.** Step 1~6은 Job의 큰 단계이고, 메서드 안의 `// 1.` ~ `// 5.`는 그 메서드 안의 읽는 순서다. 예: `ProviderListTasklet.execute`의 `// 5. 처음 발견한 제공처만 DB에 저장`은 Step 1 안의 다섯 번째 작업이다.

**`record`·DTO·엔티티.** `record`는 자바 문법(값만 담는 불변 클래스), DTO는 역할(주고받는 틀), 엔티티는 DB 테이블과 연결된 클래스다. 셋은 같은 층위가 아니다. 엔티티는 JPA가 기본 생성자와 가변 필드(더티 체킹)를 요구해 `record`로 쓰지 않는다.

**복합 키와 조인은 다르다.** `TitleGenre`는 기본키가 `(title_id, genre_id)` 두 칸이라 키 클래스 `TitleGenreId`가 있다. "복합 키"는 키가 몇 칸인가의 이야기이고, "조인"은 테이블을 합쳐 읽는 쿼리 이야기다. 이 연결 표는 같은 작품·장르 쌍이 두 번 들어가지 않게 하려고 복합 키를 쓴다.

**`flatrate`와 매핑표.** `flatrate`는 TMDB가 구독형(월정액)을 가리키는 값이다. `TmdbClient`가 구독형만 요청하도록 고정한다. 매핑표는 `tmdb_provider` 표이고 TMDB 제공처 번호가 우리 서비스의 어느 쪽인지를 MAPPED로 적어 둔다. UNMAPPED(처음 봄)·IGNORED(우리 7곳이 아님)는 discover 대상에서 빠진다.

## 10. 한계와 확인하지 못한 것

- 실제 TMDB 응답으로 끝까지 돌려 보지 않았다(WireMock 샘플만 사용). Task 048에서 확인한다.
- TMDB discover는 500쪽(1만 건)까지만 주므로 한 서비스가 그보다 많으면 뒷부분이 잘린다. 실제 규모는 확인하지 못했다.
- chunk 안에서 TMDB를 부르는 동안 DB 연결을 쥔다(T-2 예외). 상세·판정 Step은 Reader가 자기 연결을 써서 한 chunk 동안 2개까지 쓰인다(풀 5). 운영 환경 값은 확인하지 못했다.
- Reader 빈이 읽는 위치를 필드로 가져, 날짜가 다른 Job이 한 프로세스에서 동시에 돌면 서로 덮어쓴다. 전체 동시 실행 금지로 해결했다(Task 045, 11절). `@StepScope`는 쓰지 않는다.
- `@Version`(낙관적 락)은 도입하지 않았다. 수집을 한 번에 하나만 돌린다는 가정에 기댄다.
- 인기도·평점은 날마다 바뀌므로 요약 갱신 UPDATE가 많을 수 있다. 운영 부하는 첫 실제 수집에서 확인한다.
- TV 출연진은 TMDB가 "가장 최근 시즌" 기준으로 준 값이다.

## 11. 관리자 API로 실행·복구·재시작 (Task 045)

앞에서는 Job이 무엇을 하는지를 봤다. 이 절은 **Job을 사람이나 cron이 어떻게 켜고, 멈춘 Job을 어떻게 되살리는지**를 다룬다. 호출하는 쪽은 관리자 화면(SCR-16)과 GitHub Actions cron(아직 없음)이고, 둘 다 같은 5개 경로를 쓴다. 서버는 Job을 별도 스레드에서 시작하고 바로 202와 `executionId`를 돌려준다. 결과는 이력 조회로 본다.

| 경로 (모두 `Authorization: Bearer <배치 토큰>`, 없거나 틀리면 401) | 하는 일 | 성공 |
|---|---|---|
| `POST /api/admin/collect/executions` | 수집 시작. `{"tier":"ALL"}`, `targetDate`는 비우면 오늘(KST) | 202 |
| `GET /api/admin/collect/executions` | 이력(기준일 내림차순, `page`·`size`) | 200 |
| `GET …/{executionId}/changes` | 그 실행에서 제공 상태가 바뀐 작품 | 200 |
| `POST …/{executionId}/recover` | 중단됨 실행을 실패로 복구 | 200 |
| `POST …/{executionId}/restart` | 실패한 실행을 새 실행으로 재시작 | 202 |

**상태**: Batch 상태 `STARTING`·`STARTED`·`STOPPING`은 이번 서버 기동 뒤에 만든 실행이면 `RUNNING`, 기동 전에 만든 실행(서버가 죽어 기록만 남음)이면 `STOPPED`다. `STOPPED`는 저장하지 않고 조회 때마다 판정한다. `COMPLETED`는 그대로, 나머지는 모두 `FAILED`이며 복구한 실행은 종료 코드 `RECOVERED`로 구분한다(TECH 3절).

**409 판정 순서**: 같은 `targetDate`+`tier`의 가장 최근 실행을 먼저 본다(실행 중 `BATCH_ALREADY_RUNNING`, 중단됨 `BATCH_RECOVERY_REQUIRED`, 완료 `BATCH_ALREADY_COMPLETED`). 거절되지 않았으면 다른 날짜가 실행 중인지 본다(`BATCH_ALREADY_RUNNING`, 전체 동시 실행 금지). 같은 키의 최근 실행이 실패면 시작 요청도 재시작으로 처리된다.

**버튼**: `canRecover` = 상태가 `STOPPED`. `canRestart` = 상태 `FAILED`(Batch `ABANDONED` 제외) ∧ 같은 날짜·범위의 가장 최근 실행 ∧ 날짜 허용(운영은 오늘만) ∧ 다른 수집이 실행 중이 아님. 서버 거절과 같은 `CollectExecutionRules` 판정을 쓰므로 버튼과 거절이 어긋나지 않는다.

**값이 바뀌어 가는 예** (오늘 `2026-10-15`, 실행 ID는 예시)

| 순서 | 일어난 일 | 실행 11 | 실행 12 | 화면 버튼 |
|---|---|---|---|---|
| 1 | 11번이 도는 중 서버가 죽었다가 다시 켜짐 | `STOPPED` | | 복구만 켜짐 |
| 2 | `POST …/11/recover` | `FAILED`, 사유 "중단된 실행을 관리자가 실패로 복구함", `endedAt` null | | 재시작 켜짐 |
| 3 | `POST …/11/restart` → 202, 새 실행 12 | `FAILED`(최신이 아니라 재시작 꺼짐) | `RUNNING` | |
| 4 | 12번도 도는 중 서버가 또 죽음 | | `STOPPED` | 12번 복구만 켜짐. 새 시작은 409 `BATCH_RECOVERY_REQUIRED` |
| 5 | `POST …/12/recover`, 다시 `restart` → 새 실행 13 | | `FAILED` | 13번이 `COMPLETED`면 같은 날짜 시작은 409 `BATCH_ALREADY_COMPLETED` |

두 번째 복구가 되는 것이 핵심이다. 복구 표시를 Batch 실행 컨텍스트에 남기면 재시작이 이전 컨텍스트를 복사해 두 번째 `recover`가 아무 일도 하지 않는다.

**호출 예** (로컬, 배치 토큰 기본값 `local-admin-batch-token`)

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/admin/collect/executions -Headers @{Authorization="Bearer local-admin-batch-token"} -ContentType "application/json" -Body '{"tier":"ALL"}'
Invoke-RestMethod -Uri http://localhost:8080/api/admin/collect/executions -Headers @{Authorization="Bearer local-admin-batch-token"}
```

**읽는 순서** (줄 수는 빈 줄을 뺀 값)

| 순서 | 파일 | 줄 수 | 읽는 이유 |
|---|---|---|---|
| 1 | `collect/job/CollectExecutionRules` | 197 | 상태·409·버튼·실패 사유 규칙이 전부 여기 있다(순수 규칙) |
| 2 | `collect/job/CollectLaunchConfig` | 42 | 수집 전용 비동기 operator 조립 |
| 3 | `collect/job/CollectJobLauncher` | 84 | 시작·재시작·복구 창구(`RECOVERED` 저장) |
| 4 | `admin/service/CollectExecutionService` | 216 | 락 안에서 "검사 → 시작", 날짜 규칙, 409 변환 |
| 5 | `collect/job/CollectExecutionQuery` | 163 | `BATCH_*` 표를 SQL로 읽는 곳 |
| 6 | `admin/service/CollectExecutionQueryService` | 179 | 이력 응답 조립, 쿼리 수가 행 수에 비례하지 않게 묶어 조회 |
| 7 | `admin/controller/AdminCollectController` | 85 | 경로 5개 |
| 8 | `global/security/AdminBatchTokenFilter` | 81 | 배치 토큰 검사(Security 앞 필터) |

**테스트가 확인하는 것**

| 테스트 | 확인하는 것 / 막으려는 사고 |
|---|---|
| `CollectExecutionRulesTest` | 상태 대응, 409 판정 순서, 버튼 규칙, 실패 사유 300자·고정 문구. 규칙이 슬쩍 바뀌는 사고 |
| `CollectExecutionQueryTest` | SQL이 `BATCH_*`에서 날짜·최신 여부·처리 건수를 맞게 읽는지, 복구가 `RECOVERED`로 저장되는지 |
| `CollectLaunchConfigTest` | 비동기 operator가 시작 직후 끝나지 않은 실행을 돌려주고, 타입 주입 operator는 기존 동기 것인지 |
| `AdminCollectCommandApiTest` | 202·401·409(같은 날짜·다른 날짜·완료)·400 필드 오류, 동시 요청 중 하나만 202 |
| `AdminCollectRecoveryApiTest` | 복구·재시작·복구 반복 흐름과 409 사유 |
| `AdminCollectHistoryApiTest` | 이력 정렬·페이지·처리 건수·변경 목록과 쿼리 수 |
| `AdminCollectProdModeApiTest` | 운영 모드에서 오늘이 아닌 날짜 거절 |
| `AdminBatchTokenFilterTest` | 보호 경로만 검사, 인코딩·세미콜론 우회 방지 |

**깨뜨려 보기** (규칙 한 줄을 바꿔 어떤 테스트가 깨지는지 보며 이해를 점검한다)

1. `CollectExecutionRules.judgeLaunch`에서 `isAnotherRunning` 검사를 같은 키 `COMPLETED` 검사보다 위로 올린다. `CollectExecutionRulesTest`의 `같은_키가_완료면_다른_실행이_돌아도_ALREADY_COMPLETED다`가 깨진다(같은 날짜 완료가 "다른 날짜 실행 중"으로 보이는 사고).
2. `CollectExecutionRules.canRestart`의 `!isLatest` 검사를 지운다. `CollectExecutionRulesTest`의 `최신이_아닌_실패는_canRestart가_거짓이다`가 깨진다. `AdminCollectRecoveryApiTest`의 `복구_재시작_또_중단_다시_복구가_차례로_동작한다`도 3단계 `canRestart`가 `false`여야 한다는 확인에서 깨질 수 있다.
3. `CollectJobLauncher.recover`에서 `markRecovered` 호출을 지운다. `CollectExecutionQueryTest`의 `복구하면_중단된_실행은_FAILED와_RECOVERED_종료_코드로_바뀐다`와 `AdminCollectRecoveryApiTest`의 복구 테스트(고정 사유·`endedAt` null)가 깨진다.
