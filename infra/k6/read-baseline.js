// 공개 조회 API의 평상시 응답 시간 측정(Task 100, S1·S2a·S2b·S3·S1b). 다섯 시나리오를 차례로 한 번에 돌린다(약 8분 40초).
// S1 검색(DB ILIKE, 2글자 이하·3글자 이상을 번갈아 보내고 tlen 태그로 나눠 집계), S2a 상세 캐시 미스(실행 전 redis-perf FLUSHALL,
// 반복마다 다른 작품), S2b 상세 캐시 히트(warmup이 채운 50개), S3 홈 인기 순위(합성 시드, Redis 60분 캐시라 대부분 히트),
// S1b 2글자 연속 구간(2글자 검색만 이어서 보낸다. 같은 DB 연결에서 같은 쿼리가 반복될 때 실행 계획이 바뀌는지 보는 구간).
// 실행: docker compose -f infra/docker-compose.yml --profile perf run --rm --no-deps k6 run --summary-export /scripts/results/<이름>.json /scripts/read-baseline.js
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import {
  BASE_URL, SUMMARY_TREND_STATS, searchTerms, shortSearchTerms, detailMissKeys, detailHitKeys, measureTags, searchTags,
  perApiThresholds, searchLengthThresholds, alwaysPassThresholds,
} from './lib/common.js';
import { samplerScenario, sampleServerMetrics } from './lib/sampler.js';

export { sampleServerMetrics };

const VUS = 3;          // 낮은 부하(동시 3명)
const THINK_SECONDS = 0.5; // 사용자 한 명이 요청 사이에 쉬는 시간

// S1b 2글자 연속 구간 설정
// - VU 5: DB 연결 풀 최대 5개와 같게 해 연결 5개가 모두 2글자 검색만 연달아 받게 한다(VU 하나는 한 번에 연결 하나만 붙잡는다)
// - 2분: 쉬는 시간 0.5초 기준 약 1,000건(연결당 약 200건, 목록·개수 쿼리 각각). S1이 앞에서 연결마다 쌓은 실행(약 130건)보다 많아
//   PostgreSQL이 사용자 정의 계획 비용의 누적 평균으로 일반 계획 전환을 판단해도 2글자만 쌓인 상태에 도달하게 한다
// - 처음 30초(early)와 나머지(late)를 phase 태그로 나눠, 구간 중간에 계획이 바뀌면 두 값이 갈라지게 한다
const SHORT_RUN_VUS = 5;
const SHORT_RUN_SECONDS = 120;
const SHORT_RUN_EARLY_MS = 30000;

const SMOKE = __ENV.SMOKE === '1'; // 1이면 시나리오마다 10초씩만 돌리는 동작 확인용

/** 시나리오를 순서대로 10초 간격을 두고 이어 붙인다(본 측정 시작 시각은 지난 측정과 같다: 0s, 2m10s, 3m20s, 4m30s) */
function buildScenarios() {
  const plan = [
    ['s1_search', 'search', VUS, 120],
    ['s2a_detail_miss', 'detailMiss', VUS, 60],
    ['s2b_detail_hit', 'detailHit', VUS, 60],
    ['s3_popularity', 'popularity', VUS, 120],
    // 검색 연결 상태를 바꾸는 구간이라 S1~S3 뒤에 둔다(앞 시나리오의 위치와 조건을 지난 측정과 같게 유지)
    ['s1b_search_short_run', 'searchShortRun', SHORT_RUN_VUS, SHORT_RUN_SECONDS],
  ];
  const scenarios = {};
  let start = 0;
  for (const [name, fn, vus, seconds] of plan) {
    const duration = SMOKE ? 10 : seconds;
    scenarios[name] = { executor: 'constant-vus', exec: fn, vus: vus, duration: `${duration}s`, startTime: `${start}s` };
    start += duration + 10;
  }
  scenarios.sampler = samplerScenario(`${start}s`);
  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: Object.assign({
    'http_req_failed{kind:measure}': ['rate<0.01'],
  },
  perApiThresholds(['search', 'detail-miss', 'detail-hit', 'popularity', 'search-short-run']),
  searchLengthThresholds('search'),
  alwaysPassThresholds('api:search-short-run,phase:early'),
  alwaysPassThresholds('api:search-short-run,phase:late')),
};

/** S1: 검색어 목록(2글자 이하·3글자 이상 번갈아)을 돌아가며 첫 페이지 20개를 검색한다 */
export function search() {
  const term = searchTerms[exec.scenario.iterationInTest % searchTerms.length];
  const res = http.get(`${BASE_URL}/api/public/titles?q=${encodeURIComponent(term)}&page=0&size=20`,
    { tags: Object.assign(searchTags('search', term), { term: term }) });
  check(res, { '검색 200': (r) => r.status === 200 });
  sleep(THINK_SECONDS);
}

/** S1b: 2글자 검색어만 돌아가며 이어서 검색한다(early·late 구간을 태그로 나눈다) */
export function searchShortRun() {
  const term = shortSearchTerms[exec.scenario.iterationInTest % shortSearchTerms.length];
  const phase = Date.now() - exec.scenario.startTime < SHORT_RUN_EARLY_MS ? 'early' : 'late';
  const res = http.get(`${BASE_URL}/api/public/titles?q=${encodeURIComponent(term)}&page=0&size=20`,
    { tags: Object.assign(searchTags('search-short-run', term), { term: term, phase: phase }) });
  check(res, { '2글자 연속 검색 200': (r) => r.status === 200 });
  sleep(THINK_SECONDS);
}

/** S2a: 반복마다 다른 작품의 상세를 조회한다(캐시 미스 → DB 경로) */
export function detailMiss() {
  const key = detailMissKeys[exec.scenario.iterationInTest % detailMissKeys.length];
  const res = http.get(`${BASE_URL}/api/public/titles/${key.mediaType}/${key.tmdbId}`, { tags: measureTags('detail-miss') });
  check(res, { '상세(미스) 200': (r) => r.status === 200 });
  sleep(THINK_SECONDS);
}

/** S2b: warmup이 미리 조회한 50개만 반복해 조회한다(캐시 히트 → Redis 경로) */
export function detailHit() {
  const key = detailHitKeys[exec.scenario.iterationInTest % detailHitKeys.length];
  const res = http.get(`${BASE_URL}/api/public/titles/${key.mediaType}/${key.tmdbId}`, { tags: measureTags('detail-hit') });
  check(res, { '상세(히트) 200': (r) => r.status === 200 });
  sleep(THINK_SECONDS);
}

/** S3: 기간(TODAY·WEEK) × 범위(전체·서비스 1~7) 16가지를 돌아가며 인기 순위 20개를 조회한다 */
export function popularity() {
  const i = exec.scenario.iterationInTest;
  const period = i % 2 === 0 ? 'TODAY' : 'WEEK';
  const serviceId = Math.floor(i / 2) % 8; // 0이면 전체
  const service = serviceId === 0 ? '' : `&ottServiceId=${serviceId}`;
  const res = http.get(`${BASE_URL}/api/public/popularity-rankings?period=${period}${service}&limit=20`, { tags: measureTags('popularity') });
  check(res, {
    '인기 순위 200': (r) => r.status === 200,
    '인기 순위가 비어 있지 않다': (r) => r.status === 200 && (r.json('data.items') || []).length > 0,
  });
  sleep(THINK_SECONDS);
}
