# 수집 Job의 상세·시즌·판정 흐름 (Task 044)

> 2026-10-14 작성. Task 044가 수집 Job에 이어 붙인 세 Step을 설명하는 문서다. 규칙의 근거는 PRD 5.4·FR-02·FR-18, TECH 1·3절·T-2, ERD A 영역, `docs/AVAILABILITY_RULES.md`이고 이 문서는 구현을 설명한다.
> 코드는 `backend/src/main/java/com/ottnavi/collect/step/detail/`, `catalog/service/`(`TitleDetailCollectService`, `AvailabilityCollectService`), 테스트는 `CollectDetailJobTest`(14건)·`DetailMapperTest`(7건).

## 1. 배경

043까지의 Job은 `title`에 요약 값만 저장했다. 044는 작품마다 TMDB 상세·시즌·제공처를 조회해 줄거리, 장르, 출연진, 시청 단위(영화 1편·시즌 1개), 서비스별 제공 상태, 상태 변경 이력을 채운다. 규칙(러닝타임, 3상태 판정, 줄거리 대체)은 042가 만든 순수 Java(`catalog/domain/rule`)를 그대로 호출한다.

## 2. Step 순서와 하는 일

| Step | Reader(읽는 것) | TMDB 호출 | 저장 |
|---|---|---|---|
| `movieDetailStep` | MOVIE title을 id 순으로 | 영화 상세(+credits), 줄거리가 비면 en-US 한 번 더 | `title` 갱신, MOVIE 단위, 장르, 출연진 |
| `tvDetailStep` | TV title을 id 순으로 | 드라마 상세(+credits), 시즌 ≥1마다 시즌 상세 | `title` 갱신, SEASON 단위(방영 회차가 있는 시즌만), 장르, 출연진 |
| `availabilityStep` | 모든 시청 단위를 id 순으로 | 영화: 영화 제공처 / 시즌: 시즌 제공처 | `availability` 7행, 상태가 바뀐 것만 `availability_change` |

chunk는 10건이다. 후보 Step 3개가 끝난 뒤에 시작하므로 읽는 목록은 그 시점에 고정이다.

## 3. 값이 바뀌어 가는 순서 (영화 한 편)

1. TMDB JSON: `{"title":"테스트 영화 1","runtime":118,"genres":[{"id":18,"name":"드라마"}],"credits":{...}}`
2. `DetailMapper`: 규칙으로 `TitleDetail`(저장용 record)로 바꾼다. runtime 118 → `RuntimeResult(1,1,118,0,false)`, 줄거리 → `OverviewPick("...","ko")`, 출연진 → order순 상위 10명.
3. `TitleDetailCollectService`: `title.updateDetail`, `watch_unit`, `genre`·`title_genre`, `title_cast`.
4. 판정 Step: KR flatrate `[8, 1883]` → `KrProviders.of` → `AvailabilityJudge`(MAPPED 제공처만 매핑에 넣는다) → `availability` 서비스 7행, 신규면 `availability_change` 7줄(before null).

## 4. 규칙과 결정

- 방영된 회차가 0개인 시즌은 저장하지 않는다(다음 수집에서 첫 회가 방영되면 생긴다). 시즌 0(스페셜)은 호출도 하지 않는다.
- `watch_unit.episode_count`는 시즌 상세의 회차 목록 개수다(시즌 요약 값은 쓰지 않는다).
- 방영 여부 기준일은 Job 파라미터 `targetDate`다(실행 API가 KST 날짜를 넘긴다, Task 045).
- 장르는 `genre` 표에 시드가 없어 상세 응답의 `{id, 이름(ko)}`로 등록한다. 기존 장르 이름은 덮어쓰지 않는다.
- 장르·출연진·시청 단위는 지우고 다시 넣지 않고 기존 행과 비교해 차이만 반영한다(같은 유니크 키를 지웠다 넣으면 INSERT가 먼저 나가 위반, TECH 6절). 응답에 없는 기존 시청 단위는 건드리지 않는다.
- 이력은 `status`가 바뀔 때만 쌓는다(`AvailabilityChangeDetector`). 제공 상태 변경과 이력은 한 트랜잭션이다. `Availability`는 AVAILABLE이면 `in_standard`·`in_ad_tier` 중 하나가 true여야 한다(엔티티가 검사).
- 쿠팡플레이 영화는 항상 모름(STRUCTURAL_GAP), tier는 `ALL`만 허용(043과 같다).

## 5. 실패와 재시작

| 상황 | 동작 |
|---|---|
| 작품 상세·시즌이 404 | 그 작품만 건너뛴다(Processor가 null 반환). 드라마는 시즌 하나라도 404면 작품 전체를 건너뛰어 부분 저장이 없다. 기존 행은 그대로다 |
| 제공처 조회가 404 | 그 단위만 건너뛴다. 기존 제공 상태·이력을 건드리지 않는다(조회 실패를 "없음"으로 덮어쓰지 않는다) |
| 5xx·429·타임아웃(재시도 후) | Step이 실패한다. 실패한 chunk는 롤백되고 앞 chunk는 커밋돼 있다 |
| 재시작 | Reader가 읽은 위치를 저장한다(`JpaPagingItemReader`, Spring Batch 6.0.5). 같은 파라미터로 `restart`하면 커밋된 위치부터 이어 읽는다. 롤백된 chunk만 다시 처리하고, 같은 입력의 재처리는 멱등이다 |

재시작은 테스트로 확인했다: 25편 중 15번째에서 실패 → 첫 10편은 TMDB 호출 1회, 롤백된 11~14번째는 2회, 결과는 25편 모두 저장.

## 6. 호출량

- 영화: 작품당 상세 1회(+줄거리가 빈 경우 영어 1회) + 제공처 1회.
- 드라마: 작품당 상세 1회(+영어 1회) + 시즌마다 (시즌 상세 1회 + 방영 회차가 있으면 제공처 1회).
- 지표: 기존 `tmdb.call`의 `endpoint` 태그(`movie_detail`, `tv_detail`, `tv_season`, `movie_providers`, `tv_season_providers`)와 작품당 시즌 수 분포 `collect.tv.seasons`(DistributionSummary). 실제 후보 수로 총 호출 수를 추정하는 일(R11-26)은 Task 048 첫 전체 수집에서 한다.

## 7. 알려진 한계

- chunk 안에서 TMDB를 부르는 동안 DB 연결을 쥔다(T-2 예외). Reader는 자기 EntityManager를 쓰므로 한 chunk 동안 연결이 2개 쓰일 수 있다(풀 5).
- Reader 빈이 읽는 위치를 필드로 가지므로, 날짜가 다른 Job이 한 프로세스에서 동시에 돌면 서로의 상태를 덮어쓴다. 실행 API(045)에서 동시 실행을 막거나 `@StepScope`로 바꾼다(043과 같은 이월 항목).
- 응답에 없어진 시즌·시청 단위는 지우지 않는다. 줄거리는 새 응답에 값이 있으면 덮어쓰고, 한국어·영어가 모두 비면 기존 줄거리와 언어 표시를 유지한다.
- TV 출연진은 TMDB가 "가장 최근 시즌" 기준으로 준 값이다.
- `@Version`(낙관적 락)은 도입하지 않았다. 수집을 한 번에 하나만 돌린다는 가정에 기댄다.
- 실제 TMDB 응답으로 끝까지 돌려 보지는 않았다(WireMock 샘플만 사용).
