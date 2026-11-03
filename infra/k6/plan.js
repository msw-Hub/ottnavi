// 플랜 계산(POST /api/me/plan-drafts) 응답 시간 측정(Task 100, S4·S5와 최악 입력 변형 스모크).
// 계산 결과는 Redis planCalc에 입력 해시로 24시간 캐시되고 키에 사용자 id가 없다. 그래서
//   CACHE=miss: 반복마다 설정의 예산을 몇십 원씩 바꿔(PUT) 해시를 바꾸고 엔진을 매번 돌린다
//   CACHE=hit : setup()에서 한 번 계산해 캐시를 채운 뒤 같은 입력으로 계산만 반복한다
// 실행 예:
//   MODE=typical CACHE=miss  → S4(평상시, 2 VU, 3분)
//   MODE=typical CACHE=hit   → S4 캐시 히트(2 VU, 1분)
//   MODE=worst VARIANT=W1    → S5(최악 입력, 1 VU × 5회)
//   MODE=variants            → 최악 입력 변형 W1~W4를 2회씩(스모크)
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import {
  BASE_URL, SUMMARY_TREND_STATS, REQUEST_TIMEOUT, typicalSettings, worstSettings, measureTags, perApiThresholds,
} from './lib/common.js';
import { signAccessToken } from './lib/auth.js';
import { samplerScenario, sampleServerMetrics } from './lib/sampler.js';

export { sampleServerMetrics };

const MODE = __ENV.MODE || 'typical';
const CACHE = __ENV.CACHE || 'miss';
const VARIANT = __ENV.VARIANT || 'W1';

// 최악 입력 변형: 찜 20개(7개 서비스를 모두 덮음)·7곳 미구독·예산 100,000원은 같고 우선순위·월 시청 시간만 다르다
const VARIANTS = {
  W1: { userId: 41, minutes: 44640, note: '모두 MUST, 시청 시간 최대' },
  W2: { userId: 42, minutes: 120, note: '모두 MUST, 시청 시간 최소' },
  W3: { userId: 45, minutes: 44640, note: '우선순위 섞음, 시청 시간 최대' },
  W4: { userId: 46, minutes: 1800, note: '우선순위 섞음, 월 30시간(HEAVY)' },
};

const calcVariantMs = {}; // 변형별 계산 응답 시간
for (const name of Object.keys(VARIANTS)) {
  calcVariantMs[name] = new Trend(`calc_ms_${name}`, true);
}

/** 모드에 맞는 측정 시나리오를 만든다 */
function buildScenarios() {
  if (MODE === 'typical') {
    const duration = CACHE === 'hit' ? '1m' : '3m';
    return {
      plan: { executor: 'constant-vus', exec: 'typicalCalc', vus: 2, duration: duration },
      sampler: samplerScenario(duration),
    };
  }
  if (MODE === 'worst') {
    return {
      plan: { executor: 'per-vu-iterations', exec: 'worstCalc', vus: 1, iterations: 5, maxDuration: '8m' },
      sampler: samplerScenario('3m'),
    };
  }
  return {
    plan: { executor: 'per-vu-iterations', exec: 'variantCalc', vus: 1, iterations: Object.keys(VARIANTS).length * 2, maxDuration: '8m' },
    sampler: samplerScenario('2m'),
  };
}

export const options = {
  scenarios: buildScenarios(),
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: Object.assign({
    'http_req_failed{kind:measure}': ['rate<0.01'],
  }, perApiThresholds(['plan-calc', 'settings-put'])),
};

function params(userId, tags) {
  return {
    headers: { Authorization: `Bearer ${signAccessToken(userId)}`, 'Content-Type': 'application/json' },
    tags: tags,
    timeout: REQUEST_TIMEOUT,
  };
}

function putSettings(userId, body) {
  const res = http.put(`${BASE_URL}/api/me/settings`, JSON.stringify(body), params(userId, { kind: 'measure', api: 'settings-put' }));
  check(res, { '설정 저장 200': (r) => r.status === 200 });
}

/** 계산을 한 번 부르고 결과(알고리즘)를 확인한다 */
function calculate(userId, label) {
  const res = http.post(`${BASE_URL}/api/me/plan-drafts`, null, params(userId, measureTags('plan-calc')));
  const ok = check(res, { '계산 201': (r) => r.status === 201 });
  if (ok && label) {
    const drafts = res.json('data.drafts') || res.json('data');
    const summary = Array.isArray(drafts)
      ? drafts.map((d) => `${d.planType}:${d.algorithm}:must=${d.mustCompleted}/${d.mustTotal}:score=${d.score}:cost=${d.totalCost}`).join(' ')
      : 'drafts 형식 확인 필요';
    console.log(`[${label}] user=${userId} ${res.timings.duration.toFixed(0)}ms ${summary}`);
  }
  return res;
}

/** 캐시 히트 모드면 VU별 사용자의 캐시를 미리 채운다 */
export function setup() {
  if (MODE === 'typical' && CACHE === 'hit') {
    for (let userId = 1; userId <= 2; userId++) {
      putSettings(userId, typicalSettings(userId));
      http.post(`${BASE_URL}/api/me/plan-drafts`, null, params(userId, { kind: 'setup' }));
    }
  }
}

/** S4: 평상시 계산. VU n번은 사용자 n번만 써서 같은 사용자의 초안이 동시에 덮어써지지 않는다 */
export function typicalCalc() {
  const userId = exec.vu.idInTest;
  // 1. 캐시 미스 모드면 예산을 반복마다 10원씩 바꿔 입력 해시를 바꾼다(결과에는 거의 영향이 없는 크기)
  if (CACHE === 'miss') {
    putSettings(userId, typicalSettings(userId, (exec.vu.iterationInScenario + 1) * 10));
  }
  // 2. 계산한다
  calculate(userId, exec.vu.iterationInScenario === 0 ? 'S4 첫 반복' : null);
  sleep(1);
}

/** S5: 최악 입력 계산. 반복마다 예산을 1원씩 바꿔 엔진을 매번 돌린다(예산이 요금 합계보다 커서 후보는 그대로다) */
export function worstCalc() {
  const variant = VARIANTS[VARIANT];
  putSettings(variant.userId, worstSettings(variant.minutes, exec.vu.iterationInScenario + 1));
  const res = calculate(variant.userId, `S5 ${VARIANT} #${exec.vu.iterationInScenario + 1}`);
  calcVariantMs[VARIANT].add(res.timings.duration);
}

/** 변형 스모크: W1~W4를 차례로 2회씩 잰다 */
export function variantCalc() {
  const names = Object.keys(VARIANTS);
  const i = exec.vu.iterationInScenario;
  const name = names[Math.floor(i / 2)];
  const variant = VARIANTS[name];
  putSettings(variant.userId, worstSettings(variant.minutes, (i % 2) + 1));
  const res = calculate(variant.userId, `변형 ${name}(${variant.note}) #${(i % 2) + 1}`);
  calcVariantMs[name].add(res.timings.duration);
}
