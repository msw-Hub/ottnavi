# 제공 상태 판정과 러닝타임 계산 규칙 (Task 042)

> 2026-10-13 작성. Task 042가 만든 규칙을 한 곳에 정리한 설명 문서다. 규칙의 근거는 PRD FR-02·5.4·5.5, ERD A 영역이고, 이 문서는 구현을 설명한다.
> 코드는 `backend/src/main/java/com/ottnavi/catalog/domain/rule/`, 테스트는 같은 구조의 `backend/src/test/java/` 아래(37건).
> 2026-10-17 추가: 드라마 시즌 단위 제공처가 비면 작품 단위로 대신 판정하는 규칙(3절 마지막 항목). 판정기(`AvailabilityJudge`) 자체는 바뀌지 않았고, 판정기에 **어떤 KR 제공처를 넘기는지**가 수집 단계(`AvailabilityProcessor`)에서 바뀌었다.

## 1. 배경

사용자가 작품을 조회할 때는 TMDB를 부르지 않고 우리 DB를 읽는다. 그래서 수집 Job(Task 043·044)이 TMDB에서 받은 응답을 **우리 형식으로 손질해 저장**해 둔다. Task 042는 그 손질 규칙 중 **제공 상태 판정**과 **러닝타임 계산**을, Spring·DB·TMDB에 의존하지 않는 순수 Java 클래스로 만든 것이다. 입력은 인자로 받고 결과는 반환값으로 돌려주기 때문에 DB나 TMDB 없이 값만 넣어 테스트할 수 있다(`engine/`과 같은 방식).

```
수집 Job (043·044)                                         ← 서버 안의 코드
  ① TMDB에서 받음      작품·시즌의 KR 제공처 조회 응답 (드라마 시즌 응답에 KR이 없으면 작품 단위를 한 번 더 받음)
  ② DB에서 읽음        매핑표(tmdb_provider), 서비스 목록(ott_service)
  ③ 판정·계산 호출     AvailabilityJudge, RuntimeCalculator  ← Task 042 (순수 Java)
  ④ 비교·저장          AvailabilityChangeDetector → availability, availability_change 저장 (044)
```

## 2. TMDB 응답과 매핑표

TMDB는 "이 영화가 한국에서 어디서 제공되나"를 TMDB 자기 번호로 알려 준다(`GET /movie/{id}/watch/providers`).

```json
{ "results": { "KR": {
    "flatrate": [ {"provider_id": 8,   "provider_name": "Netflix"},
                  {"provider_id": 337, "provider_name": "Disney Plus"} ],
    "rent":     [ {"provider_id": 356, "provider_name": "wavve"} ] } } }
```

- `flatrate`는 구독, `rent`는 대여, `buy`는 구매다. **구독(flatrate)만 "있음"**으로 본다.
- TMDB 번호가 우리 서비스 중 누구인지는 DB의 **매핑표 `tmdb_provider`**가 알려 준다(예: 8=NETFLIX, 1796=NETFLIX의 광고형 AD tier, 337=DISNEY_PLUS). 매핑표에 없는 번호는 우리 서비스가 아니라 무시한다.
- 판정기에는 `MAPPED`인 제공처만 넘긴다(`UNMAPPED`/`IGNORED`는 넣지 않는다).

## 3. 제공 상태 판정 (`AvailabilityJudge`)

`judge(시청 단위 종류, KR 제공처, 서비스 목록, 매핑)`가 서비스마다 `AvailabilityVerdict`(상태, 모름 이유, `inStandard`, `inAdTier`)를 돌려준다. 서비스마다 아래 순서대로 먼저 맞는 규칙을 쓴다.

| 순서 | 조건 | 결과 |
|---|---|---|
| 1 | 영화 단위이고 서비스가 쿠팡플레이 | UNKNOWN / `STRUCTURAL_GAP` (TMDB에 영화 데이터가 구조적으로 비어 있음. KR 데이터가 있어도 동일) |
| 2 | KR 데이터 자체가 없음 (드라마 시즌은 작품 단위까지 확인한 뒤에도 없을 때) | UNKNOWN / `NO_KR_DATA` |
| 3 | KR flatrate에 이 서비스로 매핑된 번호가 있음 | AVAILABLE (`inStandard`/`inAdTier`는 해당 tier) |
| 4 | 그 외(대여·구매만, flatrate 목록이 비어 있음 등) | NOT_AVAILABLE |

- KR 키가 없으면 `KrProviders.noKrData()`, KR은 있는데 flatrate가 없거나 비었으면 `KrProviders.of(빈 집합)`을 넘긴다. 앞은 "모름", 뒤는 "없음"이다.
- `data_quality`(쿠팡플레이의 `INSUFFICIENT`)는 화면 표시용이라 판정에 쓰지 않는다. 쿠팡플레이 드라마는 일반 규칙을 따른다.
- 드라마는 시즌 단위로 따로 판정한다(시즌마다 제공처가 다를 수 있다).
- **시즌 단위가 비면 작품 단위로 대신 판정한다**(PRD 11절 43번). TMDB가 시즌 단위(`/tv/{id}/season/{n}/watch/providers`) KR 제공처를 비워 두는 작품이 많기 때문이다(로컬 DB 드라마 시즌 20,057개 중 4,203개가 7개 서비스 전부 `NO_KR_DATA`였다). 어떤 제공처를 판정기에 넘기는지는 아래 순서로 정한다.

| 순서 | 시즌 단위 응답 | 판정기에 넘기는 KR 제공처 |
|---|---|---|
| 1 | KR 정보가 있음 (flatrate가 비어 있어도) | 시즌 단위 결과만 쓴다. 작품 단위는 조회하지 않는다 → flatrate가 없으면 NOT_AVAILABLE |
| 2 | KR 키가 없음 → 작품 단위(`/tv/{id}/watch/providers`)를 조회해 KR 정보가 있음 | 작품 단위 결과 |
| 3 | KR 키가 없음 → 작품 단위에도 KR 정보가 없음 (404 포함) | `KrProviders.noKrData()` → UNKNOWN / `NO_KR_DATA` |

  - 같은 시리즈의 시즌을 이어서 처리할 때는 직전 시리즈 한 건의 작품 단위 결과만 들고 있어 호출을 줄인다.
  - **한계**: 작품 단위 값은 시리즈 전체 기준이라, 시즌마다 제공처가 다른 드라마에서는 시즌별로 부정확할 수 있다.
  - 예: 드라마 "카지노"(tmdbId 112486)는 시즌 단위가 비어 있지만 작품 단위에 Disney Plus(337) flatrate가 있다 → 시즌도 디즈니+ AVAILABLE.
  - 영화 판정과 쿠팡플레이 영화 `STRUCTURAL_GAP` 규칙은 그대로다.

예: 영화, KR flatrate = {8, 1796}, 웨이브는 대여만 → 넷플릭스 AVAILABLE(`inStandard`·`inAdTier` 둘 다 true), 웨이브 NOT_AVAILABLE, 쿠팡플레이 UNKNOWN(`STRUCTURAL_GAP`).

## 4. 변경 비교 (`AvailabilityChangeDetector`)

`detect(기존 상태 또는 null, 새 상태)`는 상태가 바뀌었으면 `StatusChange(before, after)`를 돌려주고 같으면 비어 있는 값을 돌려준다. 기존 상태가 없으면 신규로 보고 `before`가 null이다. **status 변화만** 이력 대상이다(`unknown_reason`이나 tier 플래그만 바뀌면 `availability` 행은 갱신하지만 `availability_change`는 만들지 않는다). 저장은 044가 한다.

## 5. 러닝타임 계산 (`RuntimeCalculator`)

| 대상 | 규칙 |
|---|---|
| 영화 `forMovie(runtime, 기본값)` | runtime 그대로. 비었으면(null 또는 0 이하) 기본값 120분으로 채우고 추정 표시 |
| 시즌 `forSeason(회차들, 기준일, 애니메이션 여부, 기본값)` | **방영된 회차**(`air_date`가 있고 기준일 이하)의 runtime 합. runtime이 비어 있는 회차는 기본값(45분, 애니메이션 24분)으로 채우고 누락 수를 센다. 방영일이 없거나 기준일 이후인 미방영 회차는 합계와 누락 수에서 모두 뺀다. 전체 회차 수는 받은 회차 목록의 길이다 |
| 애니메이션 판별 `isAnimation(장르 id들)` | 장르 id 16이 있으면 애니메이션 |

결과 `RuntimeResult`는 `episode_count`, `aired_episode_count`, `total_runtime_min`, `runtime_missing_count`, `runtime_estimated`에 그대로 대응한다.

예(시즌, 기준일 2023-03-10): 회차 runtime = 62, 58, 비어 있음(방영됨), 비어 있음(미방영) → 방영 3회, 62+58+43 = **163분**, 누락 1, 추정 true.

기본값은 코드에 박지 않고 `RuntimeDefaults`로 받는다. 설정은 `application.yml`의 `app.collect.runtime-defaults`(`movie-minutes: 100`, `episode-minutes: 43`, `animation-episode-minutes: 24`. 2026-10-15 첫 전체 수집의 실측 중앙값으로 보정, PRD 11절 25번)이고, 바인딩 클래스는 Task 043·044에서 만든다. 기준일은 `LocalDate.now()`가 아니라 호출자가 넘긴다(테스트가 날짜에 흔들리지 않게, 수집 기준일 `targetDate` 사용).

## 6. 소규모 규칙 (`TitleDetailRules`)

| 함수 | 규칙 |
|---|---|
| `collectableSeasonNumbers` | 시즌 0(스페셜)은 수집하지 않는다 |
| `pickOverview(한국어, 영어)` | 한국어 줄거리가 있으면 `ko`, 없으면 영어로 대체하고 `en`(`overview_lang`에 저장) |
| `topCast` | 출연진은 `order` 순 상위 10명 |
| `parseDate` | TMDB 날짜 문자열을 날짜로 바꾼다. 비었거나 형식이 이상하면 null(미방영 취급). 이상한 날짜 하나가 수집을 멈추지 않게 하려는 것이다 |

## 7. 이 Task가 하지 않은 것, 이어서 할 일(Task 044)

- 042는 순수 규칙만 만든다. TMDB 호출, DB 저장, 이력 엔티티 생성, 수집 Job은 043·044가 한다.
- 044가 정할 것: TMDB 응답 객체를 `KrProviders`·`EpisodeRuntime`·`CastCandidate`로 바꾸는 변환, 방영 0회 시즌(합계 0분)을 저장할지, `watch_unit.episode_count`를 시즌 요약과 시즌 상세 중 어디서 가져올지, 영어 줄거리를 얻는 추가 호출.

## 8. 확인한 것과 한계

- 새 테스트 37건 통과(판정 11, 변경 비교 5, 러닝타임 12, 소규모 규칙 9), 전체 빌드 512건 실패 0.
- 애니메이션 장르 id 16은 `docs/ERD.md`의 장르 표가 근거이고 TMDB 공식 문서로는 확인하지 못했다.
- 실제 TMDB 응답으로 끝까지 시험하지는 않았다. 042에는 TMDB 호출이 없고 변환은 044에서 시험한다.
- 시즌 `air_date`가 비어 있는 경우 샘플에는 `""`만 있었다.
