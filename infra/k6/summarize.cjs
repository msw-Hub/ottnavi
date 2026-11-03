// k6 요약 JSON(--summary-export)과 docker stats 스트림 파일에서 보고용 한 줄 요약을 뽑는다(Task 100, Node로 실행).
// 사용: node infra/k6/summarize.cjs infra/k6/results/<접두사>   (접두사.json, 접두사_stats.txt, 접두사_warmup.json을 읽는다)
const fs = require('fs');

const prefix = process.argv[2];
const summary = JSON.parse(fs.readFileSync(`${prefix}.json`, 'utf8'));
const m = summary.metrics;
const fmt = (v) => (v === undefined ? '-' : Number(v).toFixed(1));

// 1. API 태그별(그리고 검색 글자 수 tlen·연속 구간 phase 같은 추가 태그별) 응답 시간·오류율·처리량
//    하위 지표 이름 {api:search,tlen:short}는 "search tlen:short"처럼 줄 앞에 붙인다
for (const key of Object.keys(m).filter((k) => k.startsWith('http_req_duration{api:')).sort()) {
  const filter = key.slice('http_req_duration{'.length, -1);
  const label = filter.replace(/^api:/, '').replace(/,/g, ' ');
  const d = m[key];
  const failed = m[`http_req_failed{${filter}}`];
  const reqs = m[`http_reqs{${filter}}`];
  console.log(`${label}: n=${d.count} avg=${fmt(d.avg)} med=${fmt(d.med)} p95=${fmt(d['p(95)'])} p99=${fmt(d['p(99)'])} max=${fmt(d.max)} `
    + `fail=${failed ? (failed.value * 100).toFixed(2) : '-'}% rate=${reqs ? fmt(reqs.rate) : '-'}/s`);
}

// 2. 서버 메모리 표본(sampler)
const srv = (name, stat) => (m[name] ? fmt(m[name][stat]) : '-');
console.log(`server: heapMax=${srv('srv_heap_used_mb', 'max')}MB heapCommittedMax=${srv('srv_heap_committed_mb', 'max')}MB `
  + `rssMax=${srv('srv_rss_mb', 'max')}MB cgroupMax=${srv('srv_cgroup_mb', 'max')}MB `
  + `gc=${m.srv_gc_count ? m.srv_gc_count.max - m.srv_gc_count.min : '-'}회/${m.srv_gc_time_ms ? m.srv_gc_time_ms.max - m.srv_gc_time_ms.min : '-'}ms `
  + `threadsMax=${m.srv_threads_live ? m.srv_threads_live.max : '-'}`);

// 3. docker stats 스트림(화면 지우기 제어 문자를 걷어 내고 CPU·메모리 최댓값)
if (fs.existsSync(`${prefix}_stats.txt`)) {
  const text = fs.readFileSync(`${prefix}_stats.txt`, 'utf8').replace(/\x1b\[[0-9;]*[A-Za-z]/g, '\n');
  const rows = [...text.matchAll(/([\d.]+)%;([\d.]+)(MiB|GiB)/g)].map((x) => ({ cpu: +x[1], mem: x[3] === 'GiB' ? +x[2] * 1024 : +x[2] }));
  if (rows.length) {
    const busy = rows.filter((r) => r.cpu > 5).map((r) => r.cpu).sort((a, b) => a - b);
    console.log(`docker: samples=${rows.length} cpuMax=${Math.max(...rows.map((r) => r.cpu))}% `
      + `cpuMedBusy=${busy.length ? busy[Math.floor(busy.length / 2)] : '-'}% memMax=${Math.max(...rows.map((r) => r.mem)).toFixed(1)}MiB`);
  }
}

// 4. 워밍업의 첫 요청 시간
if (fs.existsSync(`${prefix}_warmup.json`)) {
  const w = JSON.parse(fs.readFileSync(`${prefix}_warmup.json`, 'utf8')).metrics;
  const firsts = Object.keys(w).filter((k) => k.startsWith('first_request_ms{')).map((k) => `${k.slice(21, -1)}=${fmt(w[k].max)}`);
  console.log(`first: ${firsts.join(' ')}`);
}
