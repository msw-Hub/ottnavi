// 재기동 직후 워밍업(Task 100). API마다 몇 번씩 불러 JIT·DB 연결·Redis 연결을 데우고, S2b·S3가 쓸 캐시를 채운다.
// 각 API의 첫 요청 시간은 first_request_ms{api:…}로 따로 남긴다(재기동 직후 첫 요청 지연 기록).
// 실행: docker compose -f infra/docker-compose.yml --profile perf run --rm --no-deps k6 run --summary-export /scripts/results/<이름>.json /scripts/warmup.js
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import {
  BASE_URL, SUMMARY_TREND_STATS, REQUEST_TIMEOUT, searchTerms, detailHitKeys, typicalSettings, worstSettings,
} from './lib/common.js';
import { signAccessToken } from './lib/auth.js';

const firstRequestMs = new Trend('first_request_ms', true); // API별 재기동 뒤 첫 요청 시간

export const options = {
  vus: 1,
  iterations: 1,
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    checks: ['rate==1.0'],
    'first_request_ms{api:ott-services}': ['max>=0'],
    'first_request_ms{api:search}': ['max>=0'],
    'first_request_ms{api:detail}': ['max>=0'],
    'first_request_ms{api:popularity}': ['max>=0'],
    'first_request_ms{api:plan-calc}': ['max>=0'],
  },
};

const WARMUP_TYPICAL_USER = 40;  // 측정(S4)이 쓰지 않는 사용자
const WARMUP_WORST_USER = 47;    // 섞음형 최악 입력 사용자(S5는 45번)
const WARMUP_BUDGET_DELTA = 500; // S5의 예산 변화(1~5원)와 겹치지 않게 해 S5가 캐시를 맞지 않게 한다

function auth(userId) {
  return { headers: { Authorization: `Bearer ${signAccessToken(userId)}`, 'Content-Type': 'application/json' }, timeout: REQUEST_TIMEOUT, tags: { kind: 'warmup' } };
}

/** 요청을 보내고, API의 첫 요청이면 시간을 따로 남긴다 */
function timed(api, seen, res) {
  if (!seen[api]) {
    seen[api] = true;
    firstRequestMs.add(res.timings.duration, { api: api });
    console.log(`첫 요청 ${api}: ${res.timings.duration.toFixed(1)}ms status=${res.status}`);
  }
  check(res, { [`${api} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
}

export default function () {
  const seen = {};
  const tags = { tags: { kind: 'warmup' } };
  // 1. 서비스 목록과 검색(검색어 10개 × 2회)
  for (let i = 0; i < 3; i++) {
    timed('ott-services', seen, http.get(`${BASE_URL}/api/public/ott-services`, tags));
  }
  for (let round = 0; round < 2; round++) {
    for (const term of searchTerms.slice(0, 10)) {
      timed('search', seen, http.get(`${BASE_URL}/api/public/titles?q=${encodeURIComponent(term)}&page=0&size=20`, tags));
    }
  }
  // 2. 상세: S2b가 쓸 50개를 조회해 캐시를 채운다
  for (const key of detailHitKeys) {
    timed('detail', seen, http.get(`${BASE_URL}/api/public/titles/${key.mediaType}/${key.tmdbId}`, tags));
  }
  // 3. 인기 순위 16가지(기간 2 × 범위 8)를 조회해 캐시를 채운다
  for (let serviceId = 0; serviceId <= 7; serviceId++) {
    for (const period of ['TODAY', 'WEEK']) {
      const service = serviceId === 0 ? '' : `&ottServiceId=${serviceId}`;
      timed('popularity', seen, http.get(`${BASE_URL}/api/public/popularity-rankings?period=${period}${service}&limit=20`, tags));
    }
  }
  // 4. 계산 엔진을 데운다: 평상시 2회, 최악 입력 1회(측정과 겹치지 않는 사용자·예산)
  for (let i = 0; i < 2; i++) {
    http.put(`${BASE_URL}/api/me/settings`, JSON.stringify(typicalSettings(WARMUP_TYPICAL_USER, WARMUP_BUDGET_DELTA + i)), auth(WARMUP_TYPICAL_USER));
    timed('plan-calc', seen, http.post(`${BASE_URL}/api/me/plan-drafts`, null, auth(WARMUP_TYPICAL_USER)));
  }
  http.put(`${BASE_URL}/api/me/settings`, JSON.stringify(worstSettings(1800, WARMUP_BUDGET_DELTA)), auth(WARMUP_WORST_USER));
  const worst = http.post(`${BASE_URL}/api/me/plan-drafts`, null, auth(WARMUP_WORST_USER));
  check(worst, { '최악 입력 워밍업 201': (r) => r.status === 201 });
  console.log(`최악 입력 워밍업: ${worst.timings.duration.toFixed(0)}ms`);
}
