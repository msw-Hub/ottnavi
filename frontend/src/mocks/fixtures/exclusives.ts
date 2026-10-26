/*
 * 서비스별 독점 목록 목업 데이터(Task 079). 독점 화면(SCR-03)과 홈의 "서비스 독점 작품" 줄 응답을 만드는 재료다.
 * Task 075 1차안의 정적 샘플(features/exclusives/sampleData.ts)을 API 응답 모양으로 옮긴 것이다.
 *
 * - 작품 제목은 모두 **가상 작품**이다. 실제 작품명에 "독점"을 붙이면 사실처럼 보일 수 있어 피했다(홈 목업과 같은 방침).
 *   tmdbId도 임의 값(9200xx)이라 카드를 누르면 상세 화면이 "찾을 수 없음"으로 나온다(목업의 한계).
 * - 포스터는 외부(TMDB) 요청 없이 목업 포스터 도우미의 data URI를 posterPath 자리에 넣는다(getTmdbImageUrl이 목업에서만 통과시킨다).
 *   한 작품은 포스터 없음(null)으로 둬 대체 표시도 본다.
 * - 쿠팡플레이(7번)는 데이터 부족 서비스지만 드라마 독점은 판정한다(isJudged=true, 드라마 3편).
 *   이전: 판정하지 않음(isJudged=false, 빈 목록). 백엔드가 드라마는 판정하게 바뀌는 것에 맞춰 바꿨다.
 * - 영화는 쿠팡플레이를 비교에서 빼므로(구조적 공백) 비교한 다른 서비스가 5곳, 드라마는 6곳이다.
 * - 넷플릭스는 "더보기"(둘째 쪽)를 확인할 수 있게 한 쪽(24편)보다 많은 30편이다. 나머지 서비스는 한 쪽 안이다.
 */
import type { ExclusiveTitle } from '@/api/generated/model'
import { createMockPosterUrl } from '@/mocks/fixtures/posters'

// 독점을 계산한 데이터의 기준일. 모든 서비스가 같은 수집 뒤에 계산된 것으로 본다
export const MOCK_EXCLUSIVES_DATA_AS_OF = '2026-10-16'

// 이전: 독점을 판정하지 않는 서비스 ID(쿠팡플레이). 지금은 판정하지 않는 서비스가 없어 쓰지 않는다.
// 예전 응답(isJudged=false)을 목업으로 다시 보려면 이 값을 되살리고 핸들러의 isJudged 줄을 되돌린다
// export const MOCK_UNJUDGED_SERVICE_ID = 7

// 목업이 아는 서비스 ID 범위(1~7). 밖이면 핸들러가 404 OTT_SERVICE_NOT_FOUND를 돌려준다
export const MOCK_SERVICE_ID_MAX = 7

// 영화 한 편을 만든다. 영화는 쿠팡플레이를 빼고 다른 5곳과 비교한다
function movie(
  tmdbId: number,
  title: string,
  year: number,
  hue: number | null,
  unknown = 0,
): ExclusiveTitle {
  return {
    mediaType: 'movie',
    tmdbId,
    title,
    releaseYear: year,
    posterPath: hue === null ? null : createMockPosterUrl(hue),
    exclusiveSeasonNumbers: null,
    comparedServiceCount: 5,
    unknownElsewhereCount: unknown,
  }
}

// 드라마 한 편을 만든다. 드라마는 쿠팡플레이까지 다른 6곳과 비교한다
function series(
  tmdbId: number,
  title: string,
  year: number,
  hue: number,
  seasons: number[],
  unknown = 0,
): ExclusiveTitle {
  return {
    mediaType: 'tv',
    tmdbId,
    title,
    releaseYear: year,
    posterPath: createMockPosterUrl(hue),
    exclusiveSeasonNumbers: seasons,
    comparedServiceCount: 6,
    unknownElsewhereCount: unknown,
  }
}

// 넷플릭스에 덧붙이는 가상 작품 23편(둘째 쪽 확인용). 영화와 드라마를 번갈아 만들고 색을 돌려 카드를 구분한다
const NETFLIX_EXTRA_TITLES: ExclusiveTitle[] = Array.from({ length: 23 }, (_, index) => {
  const number = index + 8
  const hue = (number * 37) % 360
  return index % 2 === 0
    ? movie(
        920100 + number,
        `가상 독점 영화 ${number}`,
        2020 + (number % 7),
        hue,
        number % 3 === 0 ? 1 : 0,
      )
    : series(920100 + number, `가상 독점 드라마 ${number}`, 2020 + (number % 7), hue, [1])
})

// 서비스 ID → 독점 작품(인기순). 쿠팡플레이(7)는 판정하지 않아 없다
const EXCLUSIVES_BY_SERVICE = new Map<number, ExclusiveTitle[]>([
  [
    1,
    [
      series(920001, '심야 정비소', 2025, 12, [1, 2]),
      movie(920002, '마지막 열차의 승객들', 2026, 210),
      series(920003, '서울, 열한 번째 겨울', 2024, 330, [2], 1),
      movie(920004, '고요한 항구', 2025, 95),
      series(920005, '세 번째 거짓말을 하는 법: 어느 탐정 사무소의 기록', 2026, 160, [1]),
      movie(920006, '빛이 닿지 않는 방', 2023, null, 1),
      series(920007, '하숙집 사람들', 2025, 45, [1, 2, 3]),
      ...NETFLIX_EXTRA_TITLES,
    ],
  ],
  [
    2,
    [
      series(920101, '은하 수비대: 귀환', 2026, 250, [1]),
      movie(920102, '작은 용의 여름', 2025, 140),
      series(920103, '새벽 세 시의 카지노', 2024, 20, [1, 2], 1),
      movie(920104, '바다 아래 도서관', 2026, 190),
    ],
  ],
  [
    3,
    [
      series(920201, '궤도 위의 정원', 2025, 280, [1, 2]),
      series(920202, '침묵의 회의실', 2026, 300, [1]),
      movie(920203, '느린 우체국', 2024, 70),
    ],
  ],
  [
    4,
    [
      series(920301, '환승 연애 실험실', 2026, 340, [3]),
      series(920302, '경성 야간 경찰', 2025, 30, [1], 1),
      movie(920303, '파도 소리 영화관', 2024, 175),
    ],
  ],
  [
    5,
    [
      series(920401, '수요일의 법정', 2025, 220, [1, 2]),
      movie(920402, '두 번째 계절', 2023, 120, 2),
    ],
  ],
  [6, [movie(920501, '지도에 없는 마을', 2022, 60), movie(920502, '안개 낀 극장', 2021, 265)]],
  // 쿠팡플레이: 서버가 드라마 독점만 판정하게 바뀌어(영화는 TMDB 정보가 없어 판정 불가) 드라마만 몇 편 있다.
  // 짧은 줄(3편)이 화면에서 어색하지 않은지 보려는 값이다
  [
    7,
    [
      series(920601, '새벽 배송 탐정단', 2026, 15, [1, 2]),
      series(920602, '로켓 리그의 겨울', 2025, 200, [1], 1),
      series(920603, '한밤의 편의점 주인', 2024, 110, [3]),
    ],
  ],
])

// 한 서비스의 독점 작품(인기순)을 돌려준다. 목록이 없는 서비스는 빈 목록이다
export function getMockExclusiveTitles(ottServiceId: number): ExclusiveTitle[] {
  return EXCLUSIVES_BY_SERVICE.get(ottServiceId) ?? []
}
