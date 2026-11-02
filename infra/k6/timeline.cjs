// k6 시계열(gzip CSV)에서 한 API 태그의 응답 시간을 일정 간격(기본 15초)으로 묶어 표로 낸다(Task 100, Node로 실행).
// 2글자 연속 구간(api=search-short-run)에서 실행 계획이 중간에 바뀌면 어느 시점부터 느려지는지 보려고 만들었다.
// 사용: node infra/k6/timeline.cjs infra/k6/results/<접두사> [api=search-short-run] [간격 초=15] [태그=값, 예: tlen=short]
const fs = require('fs');
const zlib = require('zlib');

const prefix = process.argv[2];
const api = process.argv[3] || 'search-short-run';
const bucketSec = Number(process.argv[4] || 15);
// 추가 태그 조건(예: tlen=short). 주면 그 태그 값인 요청만 본다
const [filterKey, filterValue] = (process.argv[5] || '').split('=');

// 1. CSV를 읽어 해당 API의 측정 응답 시간만 고른다
const lines = zlib.gunzipSync(fs.readFileSync(`${prefix}.csv.gz`)).toString('utf8').split('\n').filter(Boolean);
const header = lines[0].split(',');
const col = Object.fromEntries(header.map((h, i) => [h, i]));
const extra = (r) => Object.fromEntries((r[col.extra_tags] || '').split('&').filter(Boolean).map((kv) => kv.split('=')));
const rows = lines.slice(1).map((l) => l.split(','))
  .filter((r) => r[col.metric_name] === 'http_req_duration')
  .map((r) => ({ t: Number(r[col.timestamp]), v: Number(r[col.metric_value]), tags: extra(r) }))
  .filter((r) => r.tags.kind === 'measure' && r.tags.api === api && (!filterKey || r.tags[filterKey] === filterValue));
if (!rows.length) {
  console.log(`(표본 없음: api=${api})`);
  process.exit(0);
}

const pct = (arr, p) => {
  const s = [...arr].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.ceil((p / 100) * s.length) - 1)];
};

// 2. 구간 시작 시각 기준으로 간격마다 건수·평균·중앙값·p95·최대를 낸다
const t0 = rows.reduce((m, r) => Math.min(m, r.t), Infinity);
const buckets = new Map();
for (const r of rows) {
  const k = Math.floor((r.t - t0) / bucketSec);
  (buckets.get(k) || buckets.set(k, []).get(k)).push(r.v);
}
console.log(`| 구간(초) | 건수 | 평균 | 중앙값 | p95 | 최대 |`);
console.log('|---|---|---|---|---|---|');
for (const [k, v] of [...buckets.entries()].sort((a, b) => a[0] - b[0])) {
  const avg = v.reduce((s, x) => s + x, 0) / v.length;
  console.log(`| ${k * bucketSec}~${(k + 1) * bucketSec} | ${v.length} | ${avg.toFixed(1)} | ${pct(v, 50).toFixed(1)} | ${pct(v, 95).toFixed(1)} | ${Math.max(...v).toFixed(1)} |`);
}
