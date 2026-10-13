# 수집 Job의 후보 모으기 흐름 (Task 043)

> 2026-10-13 작성. Task 043이 만든 수집 Job의 첫 단계를 정리한 설명 문서다. 규칙의 근거는 PRD FR-18·5.5, TECH 3절·T-2, ERD A 영역이고 이 문서는 구현을 설명한다.
> 코드는 `backend/src/main/java/com/ottnavi/collect/`(`job/`·`step/`), `catalog/service/`, `provider/service/`, 테스트는 `CollectJobTest`(11건).

## 1. 배경

사용자가 작품을 조회할 때는 TMDB를 부르지 않고 우리 DB만 읽는다(TMDB 호출은 느리고 한도가 있다). 그래서 **수집 Job**이 밤마다 TMDB에서 작품을 미리 가져와 DB에 쌓는다. 이 Job은 단계(Step)를 순서대로 실행하고, 실행 기록은 `BATCH_*` 표(Flyway V4)에 남는다. 043은 그 뼈대와 첫 단계, **"어떤 작품을 수집할지 후보를 모으는 일"**을 만들었다. 작품 상세·시즌·제공 상태 판정은 Task 044가 한다.

## 2. Step 3개 흐름

| Step | 하는 일 | 저장 |
|---|---|---|
| `providerListStep` | TMDB KR 제공처 전체 목록(영화·드라마)을 받아, 처음 보는 제공처를 저장한다. 우리 7곳이 아닌 제공처도 버리지 않는다 | `tmdb_provider` UNMAPPED + `first_seen_at`. 이미 있는 행(MAPPED·IGNORED)은 덮어쓰지 않는다 |
| `movieCandidateStep` | 서비스 7곳을 차례로 돌며 KR 구독 영화 discover를 마지막 쪽(`total_pages`)까지 요청한다 | `title` MOVIE |
| `tvCandidateStep` | 같은 방식으로 드라마 | `title` TV |

서비스별 요청 값 `with_watch_providers`는 DB의 MAPPED 제공처에서 만든다(Netflix는 `8|1796`, 한 서비스에 번호가 둘이면 `|`로 묶음).

## 3. TMDB 요청·응답 예시

```
GET /3/discover/movie?with_watch_providers=8|1796&watch_region=KR&with_watch_monetization_types=flatrate&language=ko-KR&page=1
{ "page": 1, "total_pages": 3, "results": [ { "id": 900001, "title": "테스트 영화 1", ... } ] }   (드라마는 name)
GET /3/watch/providers/movie?watch_region=KR&language=ko-KR
{ "results": [ { "provider_id": 9999, "provider_name": "어떤 제공처", "logo_path": "/l.jpg" } ] }
```

저장 결과: `tmdb_provider(9999, "어떤 제공처", UNMAPPED)`와 `title`. title에는 목록이 이미 주는 요약 값을 저장한다(추가 호출 없음): 제목, 원제, 개봉일(영화 `release_date` / 드라마 `first_air_date`), `poster_path`, `backdrop_path`, 원어, `popularity`(소수 3자리), `vote_average`(소수 2자리), `refresh_tier=WEEKLY`, `fetched_at`=수집 시각. 줄거리·`overview_lang`·`tv_status`는 비워 두고 044가 채운다.

## 4. 중복과 재시작 규칙

- **중복과 갱신**: 같은 작품이 여러 서비스 목록에 나올 수 있어 저장 전에 `(media_type, tmdb_id)`로 확인한다. 없으면 새로 저장하고, 있으면 **포스터·배경·인기도·평점 4개만** 요약값으로 갱신한다(제목·원제·개봉일·줄거리 등은 044가 상세로 채우므로 건드리지 않고 `fetched_at`도 그대로). 값이 같으면 UPDATE가 나가지 않는다.
- **식별 파라미터**: `targetDate`(LocalDate) + `tier`(`CollectTier` DAILY/WEEKLY/ALL). 값이 같으면 같은 실행이다. 작품의 `RefreshTier`는 DB CHECK 때문에 따로 둔다. enum은 2단계용으로 남기되 **MVP는 `ALL`만 실행**하고 DAILY·WEEKLY는 시작 때 거부한다.
- **완료 후**: 같은 파라미터로는 다시 시작할 수 없다(`JobInstanceAlreadyCompleteException`).
- **실패 후**: 같은 실행을 `restart`하면 실패한 Step을 **처음부터** 다시 한다. Reader가 진행 위치를 저장하지 않고, 이미 저장된 작품은 건너뛰므로 결과가 같다. 이미 끝난 Step은 다시 돌지 않는 것이 Spring Batch 기본 동작이다(이번 테스트로 직접 확인하지는 않았다).
- chunk는 20건(discover 한 쪽)이다. 서비스의 마지막 쪽은 20건보다 적어 쪽 경계와 chunk가 늘 맞지는 않으나, 실패 시 롤백되는 것은 chunk 하나이고 재시작이 멱등이라 결과에 영향이 없다.

## 5. 실행 방법 (코드에서)

```java
JobParameters parameters = CollectJobParameters.of(LocalDate.of(2026, 10, 14), CollectTier.ALL);
JobExecution execution = jobOperator.start(collectJob, parameters);   // 기본은 동기. 끝나면 execution.getStatus() == COMPLETED
// 실패했다면: jobOperator.restart(execution)
```

기동 때는 자동 실행하지 않는다(`spring.batch.job.enabled=false`). `jobOperator`와 `collectJob`은 주입받는다.

## 6. 이어질 일

- **Task 044**: `title`을 읽어 상세·시즌·제공처를 받고 판정(042 규칙)해 저장한다. Job에 Step을 이어 붙인다.
- **Task 045**: 관리자 실행 API. 비동기 시작(`@BatchTaskExecutor`), 중복·완료를 409로 매핑하고 `CollectJobParameters.of`를 재사용한다.
- 047(cron), 050(단건 수집), 077(DAILY·WEEKLY 등급 분리).

## 7. 확인한 것과 한계

- 신규 테스트 11건(정상 수집, 중복, 매핑 보존, 영화·드라마, 여러 쪽, 완료 후 재실행 거부, 실패 후 재시작, 잘못된 tier·파라미터, 요약 필드 저장, 있는 작품의 부분 갱신, 변화 없으면 UPDATE 없음) 통과, 전체 빌드 523건 실패 0. Spring Batch 6.0.5 기준으로 설치된 jar와 소스로 확인했다.
- chunk 안에서 TMDB를 부르는 동안 DB 연결 1개를 쥔다(T-2 예외). 테스트에서 최대 사용은 1개였고 운영 환경 값은 확인하지 못했다.
- TMDB discover는 500쪽(1만 건)까지만 주므로 한 서비스가 그보다 많으면 뒷부분이 잘린다. 실제 규모는 확인하지 못했다.
- 실제 TMDB 응답으로 끝까지 시험하지는 않았다(WireMock 샘플만 사용).
- 인기도·평점은 날마다 바뀌므로 요약 갱신 UPDATE가 많을 수 있다. 운영 부하는 첫 실제 수집에서 확인한다.
