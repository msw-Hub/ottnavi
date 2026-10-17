/*
 * 서비스별 독점 목록(SCR-03, Task 075) 1차안의 정적 샘플 데이터다. 서버·MSW 핸들러 없이 화면 모양만 확인하려고 둔다.
 *
 * - 작품 제목은 모두 **가상 작품**이다. 실제 작품명에 "독점"을 붙이면 사실처럼 보일 수 있어 피했다(홈 1차안과 같은 방침).
 *   tmdbId도 임의 값(9200xx)이라 카드를 누르면 상세 화면이 "찾을 수 없음"으로 나온다(1차안 한계).
 * - 포스터는 외부(TMDB) 요청 없이 목업 포스터 도우미의 data URI를 쓴다. 한 작품은 포스터 없음(null)으로 둬 대체 표시도 본다.
 * - 쿠팡플레이(7번)는 데이터 부족 서비스라 목록이 없다(화면은 목록 대신 안내를 보인다, PRD SCR-03).
 * - 영화는 쿠팡플레이를 비교에서 빼므로(구조적 공백) 비교한 다른 서비스가 5곳, 드라마는 6곳이다.
 * - 계약이 정해지면 이 파일은 지우고 API 응답으로 바꾼다. 화면 컴포넌트는 props로 데이터를 받으므로 이 파일만 바꾸면 된다.
 */
import type { ExclusiveService, ExclusiveTitle } from '@/features/exclusives/mockTypes'
import { createMockPosterUrl } from '@/mocks/fixtures/posters'

// 데이터 기준일(독점을 계산한 날). 모든 서비스가 같은 수집 뒤에 계산된 것으로 본다
export const EXCLUSIVES_DATA_AS_OF = '2026-10-16'

// 국내 OTT 7곳. 순서가 탭 순서다(서비스 목록 API의 displayOrder와 같은 순서)
export const EXCLUSIVE_SERVICES: ExclusiveService[] = [
  { ottServiceId: 1, code: 'NETFLIX', name: '넷플릭스', dataQuality: 'NORMAL' },
  { ottServiceId: 2, code: 'DISNEY_PLUS', name: '디즈니+', dataQuality: 'NORMAL' },
  { ottServiceId: 3, code: 'APPLE_TV_PLUS', name: '애플 TV+', dataQuality: 'NORMAL' },
  { ottServiceId: 4, code: 'TVING', name: '티빙', dataQuality: 'NORMAL' },
  { ottServiceId: 5, code: 'WAVVE', name: '웨이브', dataQuality: 'NORMAL' },
  { ottServiceId: 6, code: 'WATCHA', name: '왓챠', dataQuality: 'NORMAL' },
  { ottServiceId: 7, code: 'COUPANG_PLAY', name: '쿠팡플레이', dataQuality: 'INSUFFICIENT' },
]

// 영화 샘플 한 편을 만든다. 영화는 쿠팡플레이를 빼고 다른 5곳과 비교한다
function movie(tmdbId: number, title: string, year: number, hue: number | null, unknown = 0) {
  return {
    mediaType: 'movie',
    tmdbId,
    title,
    releaseYear: year,
    posterUrl: hue === null ? null : createMockPosterUrl(hue),
    exclusiveSeasonNumbers: null,
    comparedServiceCount: 5,
    unknownElsewhereCount: unknown,
  } satisfies ExclusiveTitle
}

// 드라마 샘플 한 편을 만든다. 드라마는 쿠팡플레이까지 다른 6곳과 비교한다
function series(
  tmdbId: number,
  title: string,
  year: number,
  hue: number,
  seasons: number[],
  unknown = 0,
) {
  return {
    mediaType: 'tv',
    tmdbId,
    title,
    releaseYear: year,
    posterUrl: createMockPosterUrl(hue),
    exclusiveSeasonNumbers: seasons,
    comparedServiceCount: 6,
    unknownElsewhereCount: unknown,
  } satisfies ExclusiveTitle
}

// 서비스 ID → 독점 작품(인기순). 쿠팡플레이(7)는 데이터 부족이라 없다
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
])

// 한 서비스의 독점 작품(인기순)을 돌려준다. 없는 서비스면 빈 목록
export function getSampleExclusiveTitles(ottServiceId: number): ExclusiveTitle[] {
  return EXCLUSIVES_BY_SERVICE.get(ottServiceId) ?? []
}
