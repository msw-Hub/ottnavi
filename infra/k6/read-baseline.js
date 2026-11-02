// 공개 조회 API의 평상시 응답 시간 측정(Task 100, S1·S2a·S2b·S3). 네 시나리오를 차례로 한 번에 돌린다(약 6분 40초).
// S1 검색(DB ILIKE), S2a 상세 캐시 미스(실행 전 redis-perf FLUSHALL, 반복마다 다른 작품), S2b 상세 캐시 히트(warmup이 채운 50개),
// S3 홈 인기 순위(합성 시드, Redis 60분 캐시라 대부분 히트).
// 실행: docker compose -f infra/docker-compose.yml --profile perf run --rm --no-deps k6 run --summary-export /scripts/results/<이름>.json /scripts/read-baseline.js
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import {
  BASE_URL, SUMMARY_TREND_STATS, searchTerms, detailMissKeys, detailHitKeys, measureTags, perApiThresholds,
} from './lib/common.js';
import { samplerScenario, sampleServerMetrics } from './lib/sampler.js';

export { sampleServerMetrics };

const VUS = 3;          // 낮은 부하(동시 3명)
const THINK_SECONDS = 0.5; // 사용자 한 명이 요청 사이에 쉬는 시간

export const options = {
  scenarios: {
    s1_search: { executor: 'constant-vus', exec: 'search', vus: VUS, duration: '2m', startTime: '0s' },
    s2a_detail_miss: { executor: 'constant-vus', exec: 'detailMiss', vus: VUS, duration: '1m', startTime: '2m10s' },
    s2b_detail_hit: { executor: 'constant-vus', exec: 'detailHit', vus: VUS, duration: '1m', startTime: '3m20s' },
    s3_popularity: { executor: 'constant-vus', exec: 'popularity', vus: VUS, duration: '2m', startTime: '4m30s' },
    sampler: samplerScenario('6m30s'),
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: Object.assign({
    'http_req_failed{kind:measure}': ['rate<0.01'],
  }, perApiThresholds(['search', 'detail-miss', 'detail-hit', 'popularity'])),
};

/** S1: 검색어 목록을 돌아가며 첫 페이지 20개를 검색한다 */
export function search() {
  const term = searchTerms[exec.scenario.iterationInTest % searchTerms.length];
  const res = http.get(`${BASE_URL}/api/public/titles?q=${encodeURIComponent(term)}&page=0&size=20`,
    { tags: Object.assign(measureTags('search'), { term: term }) });
  check(res, { '검색 200': (r) => r.status === 200 });
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
