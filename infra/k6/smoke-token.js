// 토큰 검증 스모크: k6가 서명한 JWT를 서버가 받는지(200), 잘못된 서명은 거부하는지(401) 확인한다(Task 100).
// 부하가 아니라 1회 실행이다. 실행: docker compose -f infra/docker-compose.yml --profile perf run --rm --no-deps k6 run /scripts/smoke-token.js
import http from 'k6/http';
import { check } from 'k6';
import { signAccessToken, authParams } from './lib/auth.js';

export const options = {
  vus: 1,
  iterations: 1,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'], // 기본 요약에는 p(99)가 없다
  thresholds: {
    checks: ['rate==1.0'],
  },
};

const BASE_URL = __ENV.BASE_URL;

export default function () {
  // 1. 시스템 환경 변수가 __ENV로 들어왔는지 확인한다(값은 찍지 않고 있는지만 본다)
  check(null, {
    'BASE_URL이 __ENV로 읽힌다': () => typeof BASE_URL === 'string' && BASE_URL.length > 0,
    'JWT_SECRET이 __ENV로 읽힌다': () => typeof __ENV.JWT_SECRET === 'string' && __ENV.JWT_SECRET.length > 0,
  });
  console.log(`BASE_URL=${BASE_URL}`);

  // 2. 공개 API로 서버 연결을 확인한다
  const services = http.get(`${BASE_URL}/api/public/ott-services`, { tags: { api: 'ott-services' } });
  check(services, { '서비스 목록 200': (r) => r.status === 200 });

  // 3. 사용자 1번 토큰으로 내 설정을 조회한다(설정이 없어도 200, isOnboardingDone=false)
  const token = signAccessToken(1);
  const settings = http.get(`${BASE_URL}/api/me/settings`, authParams(token, { api: 'settings-get' }));
  check(settings, {
    '서명한 토큰으로 내 설정 200': (r) => r.status === 200,
    '응답이 data 래퍼다': (r) => r.json('data') !== undefined,
  });
  console.log(`내 설정 응답: status=${settings.status} body=${settings.body}`);

  // 4. 서명을 망가뜨린 토큰은 401이어야 한다(서버가 서명을 실제로 검사하는지)
  const broken = token.slice(0, -2) + (token.endsWith('AA') ? 'BB' : 'AA');
  const rejected = http.get(`${BASE_URL}/api/me/settings`, authParams(broken, { api: 'settings-get-broken' }));
  check(rejected, { '서명이 틀린 토큰은 401': (r) => r.status === 401 });

  // 5. 설정·찜이 없는 사용자의 플랜 계산 응답(400 VALIDATION_FAILED 예상)을 확인한다. 초안을 만들지 않는다
  const plan = http.post(`${BASE_URL}/api/me/plan-drafts`, null, authParams(signAccessToken(50), { api: 'plan-empty' }));
  check(plan, { '설정 없는 사용자의 계산은 400': (r) => r.status === 400 });
  console.log(`설정 없는 계산 응답: status=${plan.status} body=${plan.body}`);

  // 6. 관리자 메모리 지표 API를 ADMIN 토큰으로 읽을 수 있는지 본다(측정 중 힙 표본용 후보, 측정 DB의 사용자 id만 쓴다)
  const metrics = http.get(`${BASE_URL}/api/admin/system-metrics`, authParams(signAccessToken(1, 'ADMIN'), { api: 'system-metrics' }));
  check(metrics, { 'ADMIN 토큰으로 메모리 지표 200': (r) => r.status === 200 });
  console.log(`메모리 지표 응답: status=${metrics.status} body=${metrics.body}`);
}
