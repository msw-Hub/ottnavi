# 수집 Job 가이드 (Task 043·044)

> 2026-10-14 작성(043 설명 문서 3개를 합치고 044를 반영했다). 수집 Job `collectJob`이 무엇을 하고 코드를 어떤 순서로 읽으면 되는지 설명한다. 근거 문서가 아니고 코드가 기준이다.
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

**Step 6 `availabilityStep`.** `AvailabilityProcessor`가 시청 단위마다 제공처를 받고(영화는 영화 제공처, 시즌은 시즌 제공처), `DetailMapper.toKrProviders`가 `KrProviders`로 바꾼다. `AvailabilityCollectService.apply`가 서비스 7곳마다 `AvailabilityJudge`로 판정해 `availability`를 만들거나 갱신하고, 상태가 바뀐 것만 `availability_change`를 쌓는다. 제공 상태 변경과 이력은 한 트랜잭션이다.

### 값이 바뀌어 가는 순서 (영화 한 편)

1. TMDB 목록: `{"id": 603, "title": "…"}` → `title` 한 행(요약 값만)
2. TMDB 상세: `{"runtime": 118, "genres": [...], "credits": {...}}` → `TitleDetail`(러닝타임 118, 장르, 출연진 상위 10명)
3. TMDB 제공처: KR flatrate `[8, 1883]` → `KrProviders.of({8, 1883})` → `AvailabilityJudge`
4. 결과: `availability` 7행(넷플릭스·티빙 AVAILABLE, 나머지 NOT_AVAILABLE), 신규라서 `availability_change` 7줄(before null)

## 5. 규칙과 결정

- 방영된 회차가 0개인 시즌은 저장하지 않는다. 시즌 0(스페셜)은 호출도 하지 않는다.
- `watch_unit.episode_count`는 시즌 상세의 회차 목록 개수다(시즌 요약 값은 쓰지 않는다).
- 한국어 줄거리가 비면 영어로 대체하고, 둘 다 비면 기존 줄거리·언어 표시를 유지한다.
- 장르는 `genre` 표에 시드가 없어 상세 응답의 `{id, 이름(ko)}`로 등록한다. 기존 장르 이름은 덮어쓰지 않는다.
- 장르·출연진·시청 단위는 지우고 다시 넣지 않고 기존 행과 비교해 차이만 반영한다(같은 유니크 키를 지웠다 넣으면 INSERT가 먼저 나가 위반, TECH 6절). 응답에 없는 기존 시청 단위는 건드리지 않는다.
- 이력은 `status`가 바뀔 때만 쌓는다(`AvailabilityChangeDetector`). `Availability`는 AVAILABLE이면 `in_standard`·`in_ad_tier` 중 하나가 true여야 한다(엔티티가 검사).
- 쿠팡플레이 영화는 항상 모름(STRUCTURAL_GAP), `tier`는 `ALL`만 허용한다.

## 6. 실패와 재시작

| 상황 | 동작 |
|---|---|
| 작품 상세·시즌이 404 | 그 작품만 건너뛴다. 드라마는 시즌 하나라도 404면 작품 전체를 건너뛰어 부분 저장이 없다. 기존 행은 그대로다 |
| 제공처 조회가 404 | 그 단위만 건너뛴다. 기존 제공 상태·이력을 건드리지 않는다 |
| 5xx·429·타임아웃(재시도 후) | Step이 실패한다. 실패한 chunk는 롤백되고 앞 chunk는 커밋돼 있다 |
| 같은 파라미터로 완료된 Job을 다시 실행 | 거부된다(`JobInstanceAlreadyCompleteException`) |
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
- Reader 빈이 읽는 위치를 필드로 가져, 날짜가 다른 Job이 한 프로세스에서 동시에 돌면 서로 덮어쓴다. Task 045의 실행 API에서 동시 실행을 막거나 `@StepScope`로 바꾼다.
- `@Version`(낙관적 락)은 도입하지 않았다. 수집을 한 번에 하나만 돌린다는 가정에 기댄다.
- 인기도·평점은 날마다 바뀌므로 요약 갱신 UPDATE가 많을 수 있다. 운영 부하는 첫 실제 수집에서 확인한다.
- TV 출연진은 TMDB가 "가장 최근 시즌" 기준으로 준 값이다.
