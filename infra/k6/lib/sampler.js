// 측정 중 서버 메모리를 5초마다 표본으로 남기는 시나리오(Task 100).
// /actuator는 health만 열려 있어 관리자 API /api/admin/system-metrics(ADMIN 토큰)로 힙·RSS·cgroup·GC를 읽는다.
// 요청은 kind=sampler 태그라 측정 임계값·요약(kind=measure)에 섞이지 않는다.
import http from 'k6/http';
import { Trend, Gauge } from 'k6/metrics';
import { BASE_URL } from './common.js';
import { signAccessToken, authParams } from './auth.js';

const heapUsedMb = new Trend('srv_heap_used_mb');       // 힙 사용량(MB). 요약의 max가 측정 중 최대
const heapCommittedMb = new Trend('srv_heap_committed_mb');
const rssMb = new Trend('srv_rss_mb');                   // 프로세스 RSS(MB)
const cgroupMb = new Trend('srv_cgroup_mb');             // 컨테이너(cgroup) 메모리 사용량(MB). OOM 판정 기준
const gcCount = new Gauge('srv_gc_count');               // 누적 GC 횟수. 요약의 max - min이 측정 중 GC 횟수
const gcTimeMs = new Gauge('srv_gc_time_ms');            // 누적 GC 시간(ms). max - min이 측정 중 GC 시간
const threadsLive = new Gauge('srv_threads_live');       // 살아 있는 스레드 수

const MB = 1024 * 1024;

/** 측정 시나리오와 같은 길이로 도는 표본 시나리오 설정을 만든다 */
export function samplerScenario(duration, startTime = '0s') {
  return {
    executor: 'constant-arrival-rate',
    exec: 'sampleServerMetrics',
    rate: 1,
    timeUnit: '5s',
    duration: duration,
    startTime: startTime,
    preAllocatedVUs: 1,
    maxVUs: 2,
  };
}

/** 메모리 지표를 한 번 읽어 사용자 정의 지표로 남긴다 */
export function sampleServerMetrics() {
  // 1. ADMIN 토큰(측정 DB의 사용자 1번)으로 지표를 읽는다
  const res = http.get(`${BASE_URL}/api/admin/system-metrics`,
    authParams(signAccessToken(1, 'ADMIN'), { kind: 'sampler', api: 'system-metrics' }));
  if (res.status !== 200) {
    return;
  }
  // 2. 필요한 값만 꺼내 MB 단위로 남긴다(리눅스 컨테이너 값이 없으면 건너뛴다)
  const data = res.json('data');
  heapUsedMb.add(data.jvm.heap.used / MB);
  heapCommittedMb.add(data.jvm.heap.committed / MB);
  if (data.process && data.process.rssBytes) {
    rssMb.add(data.process.rssBytes / MB);
  }
  if (data.container && data.container.memoryUsedBytes) {
    cgroupMb.add(data.container.memoryUsedBytes / MB);
  }
  gcCount.add(data.jvm.gc.collectionCount);
  gcTimeMs.add(data.jvm.gc.collectionTimeMillis);
  threadsLive.add(data.jvm.threads.live);
}
