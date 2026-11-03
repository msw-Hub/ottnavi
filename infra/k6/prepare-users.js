// 측정용 사용자 1~50의 설정·찜을 측정 DB(ottnavi_perf)에 넣는다(Task 100). 측정 전에 한 번만 실행한다.
// 실행: docker compose -f infra/docker-compose.yml --profile perf run --rm --no-deps k6 run /scripts/prepare-users.js
// 찜 추가는 이미 있으면 200이라 다시 돌려도 중복되지 않는다(단, 찜 구성을 바꾼 뒤 다시 돌리면 이전 찜이 남는다. 그때는 측정 DB를 다시 만든다).
import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { BASE_URL, typicalSettings, worstSettings, wishlistFor, WORST_MUST_USERS, WORST_MIXED_USERS } from './lib/common.js';
import { signAccessToken, authParams } from './lib/auth.js';

const USER_COUNT = 50;

export const options = {
  scenarios: {
    prepare: { executor: 'shared-iterations', vus: 5, iterations: USER_COUNT, maxDuration: '5m' },
  },
  thresholds: { checks: ['rate==1.0'] },
};

export default function () {
  // 1. 반복 번호로 사용자를 고른다(1~50, 겹치지 않는다)
  const userId = exec.scenario.iterationInTest + 1;
  const params = authParams(signAccessToken(userId), { kind: 'prepare' });

  // 2. 설정을 저장한다. 최악 입력 사용자는 7곳 미구독·예산 100,000원으로 둔다(시청 시간은 측정 때 바꾼다)
  const isWorst = WORST_MUST_USERS.includes(userId) || WORST_MIXED_USERS.includes(userId);
  const settings = isWorst ? worstSettings(44640) : typicalSettings(userId);
  const put = http.put(`${BASE_URL}/api/me/settings`, JSON.stringify(settings), params);
  if (!check(put, { '설정 저장 200': (r) => r.status === 200 })) {
    fail(`사용자 ${userId} 설정 저장 실패: status=${put.status} body=${put.body}`);
  }

  // 3. 찜을 하나씩 넣는다(새로 만들면 201, 이미 있으면 200)
  for (const item of wishlistFor(userId)) {
    const res = http.post(`${BASE_URL}/api/me/wishlist-items`, JSON.stringify(item), params);
    if (!check(res, { '찜 추가 200·201': (r) => r.status === 200 || r.status === 201 })) {
      console.error(`사용자 ${userId} 찜 추가 실패: ${JSON.stringify(item)} status=${res.status} body=${res.body}`);
    }
  }
}
