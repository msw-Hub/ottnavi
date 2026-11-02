// k6 측정 스크립트가 함께 쓰는 설정·데이터·입력 만들기(Task 100).
// 요청은 항상 컴포즈 내부 주소(BASE_URL=http://backend-perf:8080)로만 보낸다.
import { SharedArray } from 'k6/data';

export const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL || !BASE_URL.startsWith('http://backend-perf:')) {
  // 측정 컨테이너 밖(개발 서버·운영 주소)으로 요청이 나가지 않게 막는다
  throw new Error('BASE_URL이 측정 컨테이너(http://backend-perf:8080)가 아니다. 컴포즈 k6 서비스로 실행할 것');
}

export const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count']; // 기본 요약에는 p(99)가 없다
export const REQUEST_TIMEOUT = '120s'; // 최악 입력 계산이 기본 60초를 넘어도 끊기지 않게 한다

// 측정 데이터(init 컨텍스트에서 한 번 읽어 VU끼리 공유한다)
export const wishlistPool = new SharedArray('wishlistPool', () => JSON.parse(open('/scripts/data/wishlist-pool.json')));
export const worstWishlist = new SharedArray('worstWishlist', () => JSON.parse(open('/scripts/data/worst-wishlist.json')));
export const searchTerms = new SharedArray('searchTerms', () => JSON.parse(open('/scripts/data/search-terms.json')));
export const detailMissKeys = new SharedArray('detailMissKeys', () => JSON.parse(open('/scripts/data/detail-miss-keys.json')));
export const detailHitKeys = new SharedArray('detailHitKeys', () => JSON.parse(open('/scripts/data/detail-hit-keys.json')));

// 사용자 번호 구간: 1~40 평상시 계산, 41~44 최악 입력(찜 20개 모두 MUST), 45~48 최악 입력(우선순위 섞음), 49~50 예비
export const TYPICAL_USER_MAX = 40;
export const WORST_MUST_USERS = [41, 42, 43, 44];
export const WORST_MIXED_USERS = [45, 46, 47, 48];

const ALL_SERVICE_IDS = [1, 2, 3, 4, 5, 6, 7];
const TYPICAL_PRIORITIES = ['MUST', 'WANT', 'MAYBE', 'WANT', 'MUST', 'MAYBE', 'WANT', 'MUST', 'MAYBE', 'WANT']; // 찜 10개 구성(MUST 3·WANT 4·MAYBE 3)
const MIXED_PRIORITIES = ['MUST', 'WANT', 'MAYBE']; // 최악 입력 섞음형은 이 순서를 반복한다

/** 평상시 사용자의 설정 본문(1곳 구독, 예산 2~3만 원대, 월 15시간). budgetDelta로 입력 해시를 바꿔 계산 캐시를 피한다 */
export function typicalSettings(userId, budgetDelta = 0) {
  const subscribedServiceId = ((userId - 1) % 7) + 1;
  return {
    subscriptions: [{ ottServiceId: subscribedServiceId, status: 'SUBSCRIBED', planName: '스탠다드', billingDay: 15 }],
    monthlyBudget: 20000 + (userId % 5) * 2500 + budgetDelta,
    monthlyWatchMinutes: 900,
    watchPreset: 'NORMAL',
    isLowestPriceMode: false,
  };
}

/** 최악 입력의 설정 본문(7곳 모두 미구독, 예산은 요금 합계 70,090원보다 커서 모든 부분집합이 예산 안) */
export function worstSettings(monthlyWatchMinutes, budgetDelta = 0) {
  return {
    subscriptions: ALL_SERVICE_IDS.map((id) => ({ ottServiceId: id, status: 'NOT_SUBSCRIBED', planName: null, billingDay: null })),
    monthlyBudget: 100000 + budgetDelta,
    monthlyWatchMinutes: monthlyWatchMinutes,
    watchPreset: 'CUSTOM',
    isLowestPriceMode: false,
  };
}

/** 사용자 번호에 맞는 찜 목록(요청 본문 배열)을 만든다 */
export function wishlistFor(userId) {
  // 1. 최악 입력 사용자는 고정된 20개를 쓴다(7개 서비스를 모두 덮는다)
  if (WORST_MUST_USERS.includes(userId) || WORST_MIXED_USERS.includes(userId)) {
    const mixed = WORST_MIXED_USERS.includes(userId);
    return worstWishlist.map((unit, i) => toWishlistBody(unit, mixed ? MIXED_PRIORITIES[i % 3] : 'MUST'));
  }
  // 2. 평상시 사용자는 후보 풀에서 사용자마다 다른 구간 10개를 잘라 쓴다(입력 해시가 사용자마다 달라진다)
  const items = [];
  const start = ((userId - 1) * 7) % wishlistPool.length;
  for (let i = 0; i < TYPICAL_PRIORITIES.length; i++) {
    items.push(toWishlistBody(wishlistPool[(start + i) % wishlistPool.length], TYPICAL_PRIORITIES[i]));
  }
  return items;
}

function toWishlistBody(unit, priority) {
  return {
    mediaType: unit.mediaType,
    tmdbId: unit.tmdbId,
    seasonNumber: unit.mediaType === 'tv' ? unit.seasonNumber : null,
    priority: priority,
  };
}

/** 측정 요청의 태그 묶음. kind=measure만 임계값·요약 대상으로 삼고 준비·지표 요청과 구분한다 */
export function measureTags(api) {
  return { kind: 'measure', api: api };
}

/** API 태그마다 요약에 하위 지표가 나오게 하는 임계값(항상 통과)을 만든다 */
export function perApiThresholds(apis) {
  const thresholds = {};
  for (const api of apis) {
    thresholds[`http_req_duration{api:${api}}`] = ['max>=0'];
    thresholds[`http_req_failed{api:${api}}`] = ['rate>=0'];
    thresholds[`http_reqs{api:${api}}`] = ['count>=0'];
  }
  return thresholds;
}
