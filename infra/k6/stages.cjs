// 한계 찾기(S6)의 k6 시계열(gzip CSV)과 docker stats 기록을 부하 단계별 표로 묶는다(Task 100, Node로 실행).
// 사용: node infra/k6/stages.cjs infra/k6/results/<접두사> <단계 목표 목록, 예: 10,20,40,80,160,320> [단계 길이 초=60] [올림 초=10]
// 단계 k의 "유지 구간"(올림 10초를 뺀 50초)만 집계한다. 시각 기준은 시계열의 첫 표본 시각이다.
const fs = require('fs');
const zlib = require('zlib');

const prefix = process.argv[2];
const targets = process.argv[3].split(',').map(Number);
const stageSec = Number(process.argv[4] || 60);
const rampSec = Number(process.argv[5] || 10);
const LIMIT_MIB = 1024; // 컨테이너 메모리 상한(C1 1GiB)

// 1. CSV를 읽어 열 이름으로 접근할 수 있게 한다(k6 CSV는 값에 쉼표가 없다고 보고 단순 분리한다)
const lines = zlib.gunzipSync(fs.readFileSync(`${prefix}.csv.gz`)).toString('utf8').split('\n').filter(Boolean);
const header = lines[0].split(',');
const col = Object.fromEntries(header.map((h, i) => [h, i]));
const rows = lines.slice(1).map((l) => l.split(','));
const t0 = rows.reduce((m, r) => Math.min(m, Number(r[col.timestamp])), Infinity);
const extra = (r) => Object.fromEntries((r[col.extra_tags] || '').split('&').filter(Boolean).map((kv) => kv.split('=')));

// 2. docker stats 기록(줄 앞 유닉스 초)을 읽는다
const stats = [];
if (fs.existsSync(`${prefix}_stats.txt`)) {
  for (const line of fs.readFileSync(`${prefix}_stats.txt`, 'utf8').split('\n')) {
    const m = line.match(/^(\d+) .*?([\d.]+)%;([\d.]+)(MiB|GiB)/);
    if (m) stats.push({ t: Number(m[1]), cpu: Number(m[2]), mem: m[4] === 'GiB' ? Number(m[3]) * 1024 : Number(m[3]) });
  }
}

const pct = (arr, p) => {
  if (!arr.length) return NaN;
  const s = [...arr].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.ceil((p / 100) * s.length) - 1)];
};
const max = (arr) => arr.reduce((m, v) => Math.max(m, v), -Infinity);
const min = (arr) => arr.reduce((m, v) => Math.min(m, v), Infinity);
const f = (v, d = 1) => (Number.isFinite(v) ? v.toFixed(d) : '-');

// 3. 단계마다 유지 구간을 집계해 표 한 줄을 만든다
console.log('| 목표 | 구간(초) | 처리량 req/s | 오류율 | 비정상 상태 코드 | p50 | p95 | p99 | max | API별 p95 | dropped | VU 최대 '
  + '| CPU 최대/중앙 % | 컨테이너 메모리 최대 MiB | 1GiB까지 여유 MiB | cgroup 최대 MB | RSS 최대 MB | 힙 used 최대 MB | 힙 committed 최대 MB '
  + '| GC 횟수/ms(시간 비중) | 스레드 최대 |');
console.log('|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|');
targets.forEach((target, k) => {
  const from = t0 + k * stageSec + rampSec;
  const to = t0 + (k + 1) * stageSec;
  const inWin = rows.filter((r) => { const t = Number(r[col.timestamp]); return t >= from && t < to; });
  if (!inWin.length) {
    console.log(`| ${target} | ${from - t0}~${to - t0} | (표본 없음: 시험이 이 단계 전에 멈춤) |`);
    return;
  }
  const measure = (name) => inWin.filter((r) => r[col.metric_name] === name && extra(r).kind === 'measure');
  const valuesOf = (name) => inWin.filter((r) => r[col.metric_name] === name).map((r) => Number(r[col.metric_value]));
  const durations = measure('http_req_duration');
  const reqs = measure('http_reqs');
  const failed = measure('http_req_failed');
  const values = durations.map((r) => Number(r[col.metric_value]));
  const failRate = failed.length ? failed.reduce((s, r) => s + Number(r[col.metric_value]), 0) / failed.length : NaN;
  // 상태 코드별 비정상 응답 수(0은 연결 실패·타임아웃)
  const bad = {};
  for (const r of durations) {
    const code = r[col.status] || '0';
    if (!/^2/.test(code)) bad[code] = (bad[code] || 0) + 1;
  }
  const perApi = {};
  for (const r of durations) {
    // 검색은 글자 수 태그(tlen)가 있으면 search/short·search/long으로 나눠 본다
    const tags = extra(r);
    const api = tags.tlen ? `${tags.api}/${tags.tlen}` : tags.api;
    (perApi[api] = perApi[api] || []).push(Number(r[col.metric_value]));
  }
  const apiP95 = Object.entries(perApi).map(([a, v]) => `${a}=${f(pct(v, 95), 0)}`).join(' ');
  const dropped = valuesOf('dropped_iterations').reduce((s, v) => s + v, 0);
  // GC 시간 비중 = 구간 안 첫·마지막 표본 사이에 늘어난 GC 시간 / 그 사이 경과 시간
  const gcTime = inWin.filter((r) => r[col.metric_name] === 'srv_gc_time_ms')
    .map((r) => [Number(r[col.timestamp]), Number(r[col.metric_value])]).sort((a, b) => a[0] - b[0]);
  const gcMs = gcTime.length > 1 ? gcTime[gcTime.length - 1][1] - gcTime[0][1] : NaN;
  const gcSpanMs = gcTime.length > 1 ? (gcTime[gcTime.length - 1][0] - gcTime[0][0]) * 1000 : NaN;
  const gcCount = valuesOf('srv_gc_count');
  const st = stats.filter((s) => s.t >= from && s.t < to);
  const cpus = st.map((s) => s.cpu);
  const memMax = max(st.map((s) => s.mem));
  console.log(`| ${target} | ${from - t0}~${to - t0} | ${f(reqs.length / (to - from))} | ${f(failRate * 100, 2)}% | ${JSON.stringify(bad)} `
    + `| ${f(pct(values, 50))} | ${f(pct(values, 95))} | ${f(pct(values, 99))} | ${f(max(values))} | ${apiP95} | ${dropped} `
    + `| ${f(max(valuesOf('vus')), 0)} | ${f(max(cpus))}/${f(pct(cpus, 50))} | ${f(memMax)} | ${f(LIMIT_MIB - memMax)} `
    + `| ${f(max(valuesOf('srv_cgroup_mb')))} | ${f(max(valuesOf('srv_rss_mb')))} | ${f(max(valuesOf('srv_heap_used_mb')))} `
    + `| ${f(max(valuesOf('srv_heap_committed_mb')))} | ${f(max(gcCount) - min(gcCount), 0)}/${f(gcMs, 0)}(${f((gcMs / gcSpanMs) * 100)}%) `
    + `| ${f(max(valuesOf('srv_threads_live')), 0)} |`);
});
const last = rows.reduce((m, r) => Math.max(m, Number(r[col.timestamp])), -Infinity);
console.log(`시계열 시작(UTC): ${new Date(t0 * 1000).toISOString()}, 마지막 표본: +${last - t0}초, 행 수 ${rows.length}`);
