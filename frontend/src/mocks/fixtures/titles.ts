/*
 * 작품 검색·상세 목업 데이터다(Task 027). 실제 작품명을 쓰지만 "제공 현황·러닝타임·TMDB ID는 화면 확인용 예시이며
 * 실제와 다를 수 있다"(일부 TMDB ID는 임의 값). 화면에 보일 때는 항상 "확인된 범위"로 표현한다.
 *
 * 제목에 "(예시)"류 설명이 붙은 "예시 드라마 A", "예시 장편 드라마"는 **실제 작품이 아닌 가상 작품**이다(tmdbId 9000xx).
 * 실제 작품명에 임의의 제공 현황을 붙이지 않으려고 따로 만들었다. 단 "슬기로운 의사생활"의 넷플릭스 제공(시즌 1·2)은
 * 사용자가 실제로 확인한 사실이다.
 *
 * 이 데이터가 화면에서 확인하게 해 주는 경우:
 * - 제공 상태 3가지: 있음(AVAILABLE) / 없음(NOT_AVAILABLE) / 모름(UNKNOWN)
 * - 쿠팡플레이(7번)는 서비스 목록 API가 dataQuality=INSUFFICIENT로 내려주는 "데이터 부족" 서비스다
 * - 동명 작품: "카지노"는 영화(1995)와 드라마(2022)가 따로 있다 → mediaType으로 구분
 * - 시즌별 제공처가 다른 드라마(가상): "예시 드라마 A"(시즌 1은 넷플릭스·티빙, 시즌 2는 티빙·웨이브)
 * - 시즌이 많은 드라마(가상): "예시 장편 드라마 (12시즌)" → 처음 5개만 보이고 "시즌 더 보기"로 나머지를 연다
 * - 영어 대체 줄거리: "버닝"(한국어 줄거리 없음 → overviewLang = 'en')
 * - 추정 러닝타임: "헌트"(영화), "오징어 게임" 시즌 3(시즌)
 * - 포스터 없음: "안나", "헌트", "카지노"(영화) → posterUrl = null
 * - 출연진 10명: "오징어 게임", "슬기로운 의사생활", "더 글로리", "기생충", "파묘"
 *
 * 서비스 ID는 서비스 목록 API(계약 example)의 ottServiceId와 같다. 값이 어긋나면 서비스 이름이 맞지 않으니 함께 고친다.
 */
import type { AvailabilityStatus } from '@/components/common/displayTypes'
import type {
  CastMember,
  ServiceAvailability,
  TitleDetail,
  TitleSeason,
} from '@/features/title-detail/mockTypes'
import { createMockPosterUrl } from '@/mocks/fixtures/posters'

// 서비스 ID(서비스 목록 API의 ottServiceId와 같다)
const NETFLIX = 1
const DISNEY_PLUS = 2
const APPLE_TV_PLUS = 3
const TVING = 4
const WAVVE = 5
const WATCHA = 6
const COUPANG_PLAY = 7

// 7개 서비스 전부의 상태를 만든다. 적지 않은 서비스는 "모름"이다(쿠팡플레이처럼 데이터가 부족한 서비스의 기본 상태)
function availabilities(
  statuses: Partial<Record<number, AvailabilityStatus>>,
): ServiceAvailability[] {
  return [NETFLIX, DISNEY_PLUS, APPLE_TV_PLUS, TVING, WAVVE, WATCHA, COUPANG_PLAY].map((id) => ({
    ottServiceId: id,
    status: statuses[id] ?? 'UNKNOWN',
  }))
}

// 이름 목록에서 출연진을 만든다(배역은 일부만 적는다)
function castOf(names: string[]): CastMember[] {
  return names.map((name) => ({ name, character: null }))
}

// 데이터 기준일. 모든 목업 작품이 같은 날 수집된 것으로 본다
export const MOCK_DATA_AS_OF = '2026-10-08'

const squidGameSeasons: TitleSeason[] = [
  {
    seasonNumber: 1,
    name: '시즌 1',
    episodeCount: 9,
    airYear: 2021,
    totalRuntimeMin: 500,
    isRuntimeEstimated: false,
    availabilities: availabilities({
      [NETFLIX]: 'AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
      [APPLE_TV_PLUS]: 'NOT_AVAILABLE',
      [TVING]: 'NOT_AVAILABLE',
      [WAVVE]: 'NOT_AVAILABLE',
    }),
  },
  {
    seasonNumber: 2,
    name: '시즌 2',
    episodeCount: 7,
    airYear: 2024,
    totalRuntimeMin: 425,
    isRuntimeEstimated: false,
    availabilities: availabilities({
      [NETFLIX]: 'AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
      [APPLE_TV_PLUS]: 'NOT_AVAILABLE',
      [TVING]: 'NOT_AVAILABLE',
      [WAVVE]: 'NOT_AVAILABLE',
    }),
  },
  {
    // 러닝타임이 TMDB에 비어 있어 회차 수 × 45분으로 추정한 시즌 → "추정" 표시
    seasonNumber: 3,
    name: '시즌 3',
    episodeCount: 6,
    airYear: 2025,
    totalRuntimeMin: 270,
    isRuntimeEstimated: true,
    availabilities: availabilities({
      [NETFLIX]: 'AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
      [APPLE_TV_PLUS]: 'NOT_AVAILABLE',
      [TVING]: 'NOT_AVAILABLE',
      [WAVVE]: 'NOT_AVAILABLE',
    }),
  },
]

// 슬기로운 의사생활: 사용자가 실제로 확인한 사실에 따라 시즌 1·2 모두 넷플릭스 "있음"이다(나머지 서비스 상태는 예시)
const hospitalPlaylistAvailabilities = availabilities({
  [NETFLIX]: 'AVAILABLE',
  [DISNEY_PLUS]: 'NOT_AVAILABLE',
  [APPLE_TV_PLUS]: 'NOT_AVAILABLE',
  [TVING]: 'NOT_AVAILABLE',
  [WAVVE]: 'NOT_AVAILABLE',
  // 왓챠·쿠팡플레이는 적지 않아 "모름"
})
const hospitalPlaylistSeasons: TitleSeason[] = [
  {
    seasonNumber: 1,
    name: '시즌 1',
    episodeCount: 12,
    airYear: 2020,
    totalRuntimeMin: 1140,
    isRuntimeEstimated: false,
    availabilities: hospitalPlaylistAvailabilities,
  },
  {
    seasonNumber: 2,
    name: '시즌 2',
    episodeCount: 12,
    airYear: 2021,
    totalRuntimeMin: 1170,
    isRuntimeEstimated: false,
    availabilities: hospitalPlaylistAvailabilities,
  },
]

// [가상 작품] "예시 드라마 A": 시즌마다 제공처가 다른 경우를 확인하기 위한 가상 작품이다. 실제 작품이 아니다.
// 시즌 1은 넷플릭스·티빙, 시즌 2는 티빙·웨이브(넷플릭스 없음)
const exampleDramaSeasons: TitleSeason[] = [
  {
    seasonNumber: 1,
    name: '시즌 1',
    episodeCount: 12,
    airYear: 2020,
    totalRuntimeMin: 1140,
    isRuntimeEstimated: false,
    availabilities: availabilities({
      [NETFLIX]: 'AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
      [APPLE_TV_PLUS]: 'NOT_AVAILABLE',
      [TVING]: 'AVAILABLE',
      [WAVVE]: 'NOT_AVAILABLE',
      [WATCHA]: 'NOT_AVAILABLE',
    }),
  },
  {
    seasonNumber: 2,
    name: '시즌 2',
    episodeCount: 12,
    airYear: 2021,
    totalRuntimeMin: 1170,
    isRuntimeEstimated: false,
    availabilities: availabilities({
      [NETFLIX]: 'NOT_AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
      [APPLE_TV_PLUS]: 'NOT_AVAILABLE',
      [TVING]: 'AVAILABLE',
      [WAVVE]: 'AVAILABLE',
      [WATCHA]: 'NOT_AVAILABLE',
    }),
  },
]

// [가상 작품] "예시 장편 드라마 (12시즌)": 시즌이 많을 때 접이식 목록과 "시즌 더 보기"를 확인하기 위한 가상 작품이다.
// 시즌마다 회차 수·시간·제공처를 다르게 만든다(3시즌 주기로 넷플릭스 / 티빙+웨이브 / 디즈니+, 일부는 모름·추정 시간)
const exampleLongDramaSeasons: TitleSeason[] = Array.from({ length: 12 }, (_, index) => {
  const seasonNumber = index + 1
  const episodeCount = 6 + (index % 5) * 2
  const cycle = index % 3
  return {
    seasonNumber,
    name: `시즌 ${seasonNumber}`,
    episodeCount,
    airYear: 2010 + index,
    totalRuntimeMin: episodeCount * (index % 4 === 3 ? 45 : 55),
    isRuntimeEstimated: index % 4 === 3, // 4의 배수 번째 시즌은 러닝타임 추정
    availabilities: availabilities({
      [NETFLIX]: cycle === 0 ? 'AVAILABLE' : 'NOT_AVAILABLE',
      [TVING]: cycle === 1 ? 'AVAILABLE' : 'NOT_AVAILABLE',
      [WAVVE]: cycle === 1 ? 'AVAILABLE' : 'NOT_AVAILABLE',
      [DISNEY_PLUS]: cycle === 2 ? 'AVAILABLE' : 'NOT_AVAILABLE',
      // 일부 시즌은 애플 TV+·왓챠를 적지 않아 "모름", 마지막 시즌은 모두 모름에 가깝다
      [APPLE_TV_PLUS]: index % 2 === 0 ? 'NOT_AVAILABLE' : 'UNKNOWN',
      [WATCHA]: index === 11 ? 'UNKNOWN' : 'NOT_AVAILABLE',
    }),
  }
})

// 시즌별 상태를 합쳐 작품 전체 상태를 만든다: 어느 시즌이든 "있음"이면 있음, 아니면 모두 없음일 때만 없음, 그 외 모름
function mergeSeasonAvailabilities(seasons: TitleSeason[]): ServiceAvailability[] {
  return [NETFLIX, DISNEY_PLUS, APPLE_TV_PLUS, TVING, WAVVE, WATCHA, COUPANG_PLAY].map((id) => {
    const statuses = seasons.map((s) => s.availabilities.find((a) => a.ottServiceId === id)?.status)
    const status: AvailabilityStatus = statuses.includes('AVAILABLE')
      ? 'AVAILABLE'
      : statuses.every((s) => s === 'NOT_AVAILABLE')
        ? 'NOT_AVAILABLE'
        : 'UNKNOWN'
    return { ottServiceId: id, status }
  })
}

const gloryAvailabilities = availabilities({
  [NETFLIX]: 'AVAILABLE',
  [DISNEY_PLUS]: 'NOT_AVAILABLE',
  [APPLE_TV_PLUS]: 'NOT_AVAILABLE',
  [TVING]: 'NOT_AVAILABLE',
  [WAVVE]: 'NOT_AVAILABLE',
  [WATCHA]: 'NOT_AVAILABLE',
})

// 더 글로리의 두 시즌이 공유하는 기본값. 시즌 번호·이름·일부 값만 바꿔 쓴다
const gloryBaseSeason: TitleSeason = {
  seasonNumber: 0,
  name: '',
  episodeCount: 8,
  airYear: 2022,
  totalRuntimeMin: 440,
  isRuntimeEstimated: false,
  availabilities: gloryAvailabilities,
}

export const TITLE_FIXTURES: TitleDetail[] = [
  {
    mediaType: 'tv',
    tmdbId: 93405,
    title: '오징어 게임',
    originalTitle: '오징어 게임',
    posterUrl: createMockPosterUrl(15),
    overview:
      '거액의 상금이 걸린 의문의 서바이벌에 참가한 사람들이 목숨을 걸고 어린 시절의 놀이에 도전하는 이야기다.',
    overviewLang: 'ko',
    genres: ['드라마', '액션&어드벤처', '미스터리'],
    releaseDate: '2021-09-17',
    runtimeMin: null,
    isRuntimeEstimated: false,
    cast: castOf([
      '이정재',
      '박해수',
      '위하준',
      '정호연',
      '오영수',
      '허성태',
      '아누팜 트리파티',
      '김주령',
      '이유미',
      '공유',
    ]),
    availabilities: squidGameSeasons[0].availabilities,
    seasons: squidGameSeasons,
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    mediaType: 'tv',
    tmdbId: 96578,
    title: '슬기로운 의사생활',
    originalTitle: '슬기로운 의사생활',
    posterUrl: createMockPosterUrl(150),
    overview:
      '같은 병원에서 일하는 오랜 친구 다섯 명이 환자를 돌보고 밴드 연습을 하며 함께 지내는 일상을 그린 드라마다.',
    overviewLang: 'ko',
    genres: ['드라마', '코미디'],
    releaseDate: '2020-03-12',
    runtimeMin: null,
    isRuntimeEstimated: false,
    cast: castOf([
      '조정석',
      '유연석',
      '정경호',
      '김대명',
      '전미도',
      '신현빈',
      '정문성',
      '김준한',
      '안은진',
      '곽선영',
    ]),
    // 작품 전체: 어느 시즌이든 제공되면 있음
    availabilities: hospitalPlaylistAvailabilities,
    seasons: hospitalPlaylistSeasons,
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    // [가상 작품] 실제 작품이 아니다. 시즌별 제공처가 다른 경우를 확인하는 용도
    mediaType: 'tv',
    tmdbId: 900001,
    title: '예시 드라마 A (시즌별 제공처 다름)',
    originalTitle: '예시 드라마 A (시즌별 제공처 다름)',
    posterUrl: createMockPosterUrl(30),
    overview: '화면 확인을 위해 만든 가상의 드라마다. 시즌에 따라 볼 수 있는 서비스가 다르다.',
    overviewLang: 'ko',
    genres: ['드라마'],
    releaseDate: '2020-03-12',
    runtimeMin: null,
    isRuntimeEstimated: false,
    cast: castOf(['가상 배우 1', '가상 배우 2', '가상 배우 3']),
    availabilities: mergeSeasonAvailabilities(exampleDramaSeasons),
    seasons: exampleDramaSeasons,
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    // [가상 작품] 실제 작품이 아니다. 시즌이 12개인 경우(접이식 목록·"시즌 더 보기")를 확인하는 용도
    mediaType: 'tv',
    tmdbId: 900002,
    title: '예시 장편 드라마 (12시즌)',
    originalTitle: '예시 장편 드라마 (12시즌)',
    posterUrl: createMockPosterUrl(60),
    overview:
      '화면 확인을 위해 만든 가상의 드라마다. 시즌이 12개라 시즌 목록이 길어지는 경우를 보여 준다.',
    overviewLang: 'ko',
    genres: ['드라마', '코미디'],
    releaseDate: '2010-01-01',
    runtimeMin: null,
    isRuntimeEstimated: false,
    cast: castOf(['가상 배우 1', '가상 배우 2', '가상 배우 3', '가상 배우 4']),
    availabilities: mergeSeasonAvailabilities(exampleLongDramaSeasons),
    seasons: exampleLongDramaSeasons,
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    mediaType: 'tv',
    tmdbId: 136283,
    title: '더 글로리',
    originalTitle: '더 글로리',
    posterUrl: createMockPosterUrl(265),
    overview:
      '학창 시절 큰 상처를 입은 주인공이 오랜 시간 준비한 계획으로 가해자들을 찾아가는 복수극이다.',
    overviewLang: 'ko',
    genres: ['드라마', '미스터리'],
    releaseDate: '2022-12-30',
    runtimeMin: null,
    isRuntimeEstimated: false,
    cast: castOf([
      '송혜교',
      '이도현',
      '임지연',
      '염혜란',
      '박성훈',
      '정성일',
      '김히어라',
      '차주영',
      '김건우',
      '정지소',
    ]),
    availabilities: gloryAvailabilities,
    seasons: [
      { ...gloryBaseSeason, seasonNumber: 1, name: '시즌 1' },
      { ...gloryBaseSeason, seasonNumber: 2, name: '시즌 2', airYear: 2023, totalRuntimeMin: 450 },
    ],
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    // 쿠팡플레이 오리지널. 쿠팡플레이는 "데이터 부족" 서비스라 다른 서비스 상태가 "모름"으로 남기도 한다
    mediaType: 'tv',
    tmdbId: 118243,
    title: '안나',
    originalTitle: '안나',
    posterUrl: null,
    overview: '거짓된 삶을 살게 된 한 여성의 이야기를 그린 심리 스릴러 드라마다.',
    overviewLang: 'ko',
    genres: ['드라마', '미스터리'],
    releaseDate: '2022-06-24',
    runtimeMin: null,
    isRuntimeEstimated: false,
    cast: castOf(['수지', '정은채', '김준한', '박예영']),
    availabilities: availabilities({ [COUPANG_PLAY]: 'AVAILABLE', [NETFLIX]: 'NOT_AVAILABLE' }),
    seasons: [
      {
        seasonNumber: 1,
        name: '시즌 1',
        episodeCount: 8,
        airYear: 2022,
        totalRuntimeMin: 480,
        isRuntimeEstimated: false,
        availabilities: availabilities({
          [COUPANG_PLAY]: 'AVAILABLE',
          [NETFLIX]: 'NOT_AVAILABLE',
        }),
      },
    ],
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    // 동명 작품 ①: 드라마 "카지노"(2022)
    mediaType: 'tv',
    tmdbId: 205123,
    title: '카지노',
    originalTitle: '카지노',
    posterUrl: createMockPosterUrl(45),
    overview: '필리핀의 카지노 업계에서 성공한 한국인 사업가가 위기에 빠지는 과정을 그린 드라마다.',
    overviewLang: 'ko',
    genres: ['드라마', '범죄'],
    releaseDate: '2022-12-21',
    runtimeMin: null,
    isRuntimeEstimated: false,
    cast: castOf(['최민식', '손석구', '이동휘']),
    availabilities: availabilities({
      [DISNEY_PLUS]: 'AVAILABLE',
      [NETFLIX]: 'NOT_AVAILABLE',
      [TVING]: 'NOT_AVAILABLE',
    }),
    seasons: [
      {
        seasonNumber: 1,
        name: '시즌 1',
        episodeCount: 9,
        airYear: 2022,
        totalRuntimeMin: 540,
        isRuntimeEstimated: false,
        availabilities: availabilities({
          [DISNEY_PLUS]: 'AVAILABLE',
          [NETFLIX]: 'NOT_AVAILABLE',
          [TVING]: 'NOT_AVAILABLE',
        }),
      },
      {
        seasonNumber: 2,
        name: '시즌 2',
        episodeCount: 6,
        airYear: 2023,
        totalRuntimeMin: 360,
        isRuntimeEstimated: false,
        availabilities: availabilities({
          [DISNEY_PLUS]: 'AVAILABLE',
          [NETFLIX]: 'NOT_AVAILABLE',
          [TVING]: 'NOT_AVAILABLE',
        }),
      },
    ],
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    // 동명 작품 ②: 영화 "카지노"(1995). 오래된 영화라 포스터가 없는 경우를 함께 확인한다
    mediaType: 'movie',
    tmdbId: 524,
    title: '카지노',
    originalTitle: 'Casino',
    posterUrl: null,
    overview:
      '1970년대 라스베이거스 카지노를 맡은 도박 전문가와 그의 오랜 친구, 아내가 얽히며 몰락해 가는 과정을 그린 범죄 영화다.',
    overviewLang: 'ko',
    genres: ['범죄', '드라마'],
    releaseDate: '1995-11-22',
    runtimeMin: 178,
    isRuntimeEstimated: false,
    cast: castOf(['로버트 드 니로', '샤론 스톤', '조 페시', '제임스 우즈']),
    availabilities: availabilities({
      [WATCHA]: 'AVAILABLE',
      [NETFLIX]: 'NOT_AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
    }),
    seasons: [],
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    mediaType: 'movie',
    tmdbId: 496243,
    title: '기생충',
    originalTitle: '기생충',
    posterUrl: createMockPosterUrl(100),
    overview:
      '반지하에 사는 가족이 부유한 가족의 집에 하나둘 들어가면서 벌어지는 일을 그린 영화다.',
    overviewLang: 'ko',
    genres: ['코미디', '스릴러', '드라마'],
    releaseDate: '2019-05-30',
    runtimeMin: 133,
    isRuntimeEstimated: false,
    cast: castOf([
      '송강호',
      '이선균',
      '조여정',
      '최우식',
      '박소담',
      '이정은',
      '장혜진',
      '박명훈',
      '정지소',
      '정현준',
    ]),
    availabilities: availabilities({
      [NETFLIX]: 'NOT_AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
      [TVING]: 'AVAILABLE',
      [WAVVE]: 'AVAILABLE',
      [WATCHA]: 'AVAILABLE',
    }),
    seasons: [],
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    mediaType: 'movie',
    tmdbId: 838209,
    title: '파묘',
    originalTitle: '파묘',
    posterUrl: createMockPosterUrl(330),
    overview:
      '거액의 의뢰를 받은 무당과 풍수사, 장의사가 수상한 묘를 옮기며 겪는 일을 그린 영화다.',
    overviewLang: 'ko',
    genres: ['미스터리', '공포'],
    releaseDate: '2024-02-22',
    runtimeMin: 134,
    isRuntimeEstimated: false,
    cast: castOf([
      '최민식',
      '김고은',
      '유해진',
      '이도현',
      '김재철',
      '김병오',
      '전진기',
      '박지연',
      '이지원',
      '정윤하',
    ]),
    availabilities: availabilities({
      [NETFLIX]: 'NOT_AVAILABLE',
      [DISNEY_PLUS]: 'NOT_AVAILABLE',
      [TVING]: 'AVAILABLE',
      [WAVVE]: 'NOT_AVAILABLE',
      [WATCHA]: 'NOT_AVAILABLE',
    }),
    seasons: [],
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    // 러닝타임이 비어 있어 기본값 120분으로 추정한 영화 → "추정" 표시. 제공 정보도 대부분 "모름"이다
    mediaType: 'movie',
    tmdbId: 837799,
    title: '헌트',
    originalTitle: '헌트',
    posterUrl: null,
    overview: '조직 안에 숨어든 스파이를 찾는 두 요원의 대립을 그린 첩보 액션 영화다.',
    overviewLang: 'ko',
    genres: ['액션', '스릴러'],
    releaseDate: '2022-08-10',
    runtimeMin: 120,
    isRuntimeEstimated: true,
    cast: castOf(['이정재', '정우성', '전혜진', '허성태', '고윤정']),
    availabilities: availabilities({ [NETFLIX]: 'AVAILABLE' }),
    seasons: [],
    dataAsOf: MOCK_DATA_AS_OF,
  },
  {
    // 한국어 줄거리가 없어 영어 줄거리로 대체하는 작품 → "영어 원문" 표시
    mediaType: 'movie',
    tmdbId: 491584,
    title: '버닝',
    originalTitle: '버닝',
    posterUrl: createMockPosterUrl(200),
    overview:
      'A young man who works odd jobs meets a girl from his old neighborhood, and her return from a trip with a mysterious new friend draws him into a quiet obsession.',
    overviewLang: 'en',
    genres: ['미스터리', '드라마'],
    releaseDate: '2018-05-17',
    runtimeMin: 148,
    isRuntimeEstimated: false,
    cast: castOf(['유아인', '스티븐 연', '전종서']),
    availabilities: availabilities({
      [WATCHA]: 'AVAILABLE',
      [TVING]: 'NOT_AVAILABLE',
      [NETFLIX]: 'NOT_AVAILABLE',
    }),
    seasons: [],
    dataAsOf: MOCK_DATA_AS_OF,
  },
]
