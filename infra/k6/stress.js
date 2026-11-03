// 한계 찾기(Task 100, S6). 임계가 깨지면 abortOnFail로 스스로 멈추고, 멈춘 부하 단계를 한계로 기록한다.
//   MODE=read : 검색 4 · 상세 3(미스·히트 섞음) · 인기 순위 3 비율, 도착률 기준(ramping-arrival-rate, 열린 모델) 5→320 req/s
//   MODE=plan : 평상시 계산(캐시 미스), VU 기준(ramping-vus, 닫힌 모델) 1→32 VU, 쉼 없음
//   MODE=mixed: 읽기 90% + 계산 10%, 도착률 기준 5→320 req/s
//   MODE=worst: 최악 입력 계산(찜 20개)의 동시 실행, VU 1→2→4(추가 측정)
//   SMOKE=1   : 단계를 1분 남짓으로 줄인 동작 확인용
// 시계열은 실행 명령에서 --out csv=/scripts/results/<이름>.csv.gz로 남긴다.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import {
  BASE_URL, SUMMARY_TREND_STATS, REQUEST_TIMEOUT, searchTerms, detailMissKeys, detailHitKeys, typicalSettings, worstSettings,
  measureTags, perApiThresholds, searchLengthTag, searchLengthThresholds, TYPICAL_USER_MAX, WORST_MUST_USERS,
} from './lib/common.js';
import { signAccessToken } from './lib/auth.js';
import { samplerScenario, sampleServerMetrics } from './lib/sampler.js';

export { sampleServerMetrics };

const MODE = __ENV.MODE || 'read';
const SMOKE = __ENV.SMOKE === '1';
const MIXED_PLAN_EVERY = 10; // 혼합 모드에서 반복 10번 중 1번이 계산이다

// 도착률 단계(단계마다 10초 올리고 50초 유지): 초당 시작하는 반복(=요청) 수
function rateStages() {
  if (SMOKE) {
    return [{ target: 5, duration: '20s' }, { target: 20, duration: '20s' }, { target: 20, duration: '20s' }];
  }
  // RATES=80,100,120처럼 주면 그 목표들만 쓴다(한계 근처를 잘게 나눠 볼 때). 기본은 10→320, 합계 6분
  const targets = __ENV.RATES ? __ENV.RATES.split(',').map(Number) : [10, 20, 40, 80, 160, 320];
  const stages = [];
  for (const target of targets) {
    stages.push({ target: target, duration: '10s' }, { target: target, duration: '50s' });
  }
  return stages;
}

// VU 단계(단계마다 10초 올리고 50초 유지)
function vuStages(targets) {
  if (SMOKE) {
    return [{ target: 1, duration: '20s' }, { target: 2, duration: '20s' }, { target: 2, duration: '20s' }];
  }
  const stages = [];
  for (const target of targets) {
    stages.push({ target: target, duration: '10s' }, { target: target, duration: '50s' });
  }
  return stages;
}

function buildScenarios() {
  const samplerFor = (stages) => {
    const seconds = stages.reduce((sum, s) => sum + parseInt(s.duration, 10), 0) + 30;
    return samplerScenario(`${seconds}s`);
  };
  if (MODE === 'plan' || MODE === 'worst') {
    const stages = MODE === 'plan' ? vuStages([1, 2, 4, 8, 16, 32]) : vuStages([1, 2, 4]);
    return {
      stress: { executor: 'ramping-vus', exec: MODE === 'plan' ? 'planVu' : 'worstVu', startVUs: 0, stages: stages, gracefulRampDown: '30s' },
      sampler: samplerFor(stages),
    };
  }
  const stages = rateStages();
  return {
    stress: {
      executor: 'ramping-arrival-rate', exec: MODE === 'mixed' ? 'mixedOnce' : 'readOnce',
      startRate: 5, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 400, stages: stages,
    },
    sampler: samplerFor(stages),
  };
}

export const options = {
  scenarios: buildScenarios(),
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: Object.assign({
    // 1. 오류율 1% 초과면 멈춘다(처음 10초는 표본이 적어 평가를 미룬다)
    'http_req_failed{kind:measure}': [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' }],
    // 2. 읽기 p95 1초를 넘으면 멈춘다(초안 값)
    'http_req_duration{group_kind:read}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '20s' }],
    // 3. 평상시 계산 p95 2초를 넘으면 멈춘다(평상시 p95가 약 76ms라 2초는 사용자가 확실히 느끼는 지연. 최악 입력 모드는 기록만)
    'http_req_duration{api:plan-calc}': MODE === 'worst'
      ? ['max>=0']
      : [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '20s' }],
  }, perApiThresholds(['search', 'detail-miss', 'detail-hit', 'popularity', 'plan-calc', 'settings-put']), searchLengthThresholds('search')),
};

function readTags(api) {
  return { tags: Object.assign(measureTags(api), { group_kind: 'read' }), timeout: REQUEST_TIMEOUT };
}

/** 읽기 요청 하나를 비율(검색 4 · 상세 3 · 인기 3)대로 고른다 */
export function readOnce() {
  const i = exec.scenario.iterationInTest;
  const r = i % 10;
  let res;
  if (r < 4) {
    // 검색 순번(반복 10번마다 4건)으로 고른다. i % 길이로 고르면 i % 10 < 4인 자리만 걸려 30개 중 12개만 쓰였다(2026-11-03 수정)
    const term = searchTerms[(Math.floor(i / 10) * 4 + r) % searchTerms.length];
    const params = readTags('search');
    params.tags.tlen = searchLengthTag(term);
    res = http.get(`${BASE_URL}/api/public/titles?q=${encodeURIComponent(term)}&page=0&size=20`, params);
  } else if (r < 7) {
    // 상세는 미스(매번 다른 작품) 2 : 히트(warmup이 채운 50개) 1
    const miss = r !== 6;
    const keys = miss ? detailMissKeys : detailHitKeys;
    const key = keys[i % keys.length];
    res = http.get(`${BASE_URL}/api/public/titles/${key.mediaType}/${key.tmdbId}`, readTags(miss ? 'detail-miss' : 'detail-hit'));
  } else {
    const service = i % 8 === 0 ? '' : `&ottServiceId=${i % 8}`;
    res = http.get(`${BASE_URL}/api/public/popularity-rankings?period=${i % 2 === 0 ? 'TODAY' : 'WEEK'}${service}&limit=20`, readTags('popularity'));
  }
  check(res, { '읽기 200': (x) => x.status === 200 });
}

/** 사용자 한 명의 설정 예산을 바꾸고(캐시 미스 강제) 계산한다 */
function calcOnce(userId, budgetDelta, settings) {
  const params = { headers: { Authorization: `Bearer ${signAccessToken(userId)}`, 'Content-Type': 'application/json' }, timeout: REQUEST_TIMEOUT };
  // 1. 예산을 바꿔 입력 해시를 바꾼다
  http.put(`${BASE_URL}/api/me/settings`, JSON.stringify(settings(userId, budgetDelta)),
    Object.assign({ tags: { kind: 'measure', api: 'settings-put' } }, params));
  // 2. 계산한다
  const res = http.post(`${BASE_URL}/api/me/plan-drafts`, null, Object.assign({ tags: measureTags('plan-calc') }, params));
  check(res, { '계산 201': (x) => x.status === 201 });
}

/**
 * 계산 모드(닫힌 모델): VU n번이 사용자 n번만 쓴다. VU는 최대 32라 사용자 1~40 안에서 겹치지 않는다.
 * 예산 변화는 0~999원 안에서 반복마다 1원씩 늘려, 입력 해시는 바뀌되 계산 난도(예산 안 부분집합)는 거의 그대로 둔다.
 */
export function planVu() {
  calcOnce(exec.vu.idInTest, exec.vu.iterationInScenario % 1000, typicalSettings);
}

/**
 * 혼합 모드(열린 모델)의 계산: VU 번호가 아니라 "계산 순번"으로 사용자 1~40을 차례로 돌려 쓴다.
 * 같은 사용자는 계산 40건마다 한 번 돌아오므로, 계산 1건이 그다음 계산 40건이 시작될 시간보다 오래 걸릴 때만 겹친다.
 * 예산 변화는 (순번 / 40) % 1000원이라 같은 사용자 안에서 4만 건까지 해시가 겹치지 않는다.
 */
function planBySequence(planSeq) {
  const userId = (planSeq % TYPICAL_USER_MAX) + 1;
  calcOnce(userId, Math.floor(planSeq / TYPICAL_USER_MAX) % 1000, typicalSettings);
}

/** 혼합: 반복 10번 중 1번은 계산, 나머지는 읽기 */
export function mixedOnce() {
  const i = exec.scenario.iterationInTest;
  if (i % MIXED_PLAN_EVERY === 0) {
    planBySequence(i / MIXED_PLAN_EVERY);
  } else {
    readOnce();
  }
}

/** 최악 입력 동시 계산: VU n번이 모두 MUST 사용자(41~44) 중 n번째를 쓴다. 예산은 VU·반복마다 달라 캐시를 맞지 않는다 */
export function worstVu() {
  const userId = WORST_MUST_USERS[(exec.vu.idInTest - 1) % WORST_MUST_USERS.length];
  calcOnce(userId, exec.vu.idInTest * 100 + exec.vu.iterationInScenario, (_, delta) => worstSettings(44640, delta));
}
