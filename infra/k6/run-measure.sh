#!/usr/bin/env bash
# 측정 1회를 같은 순서로 실행한다(Task 100): backend-perf 재기동 → 준비 완료 대기(compose --wait) → redis-perf 비우기 → 워밍업 → 본 측정.
# 저장소 루트에서 Git Bash로 실행: bash infra/k6/run-measure.sh <스크립트.js> <결과 이름> <docker stats 기록 초> [k6 -e 인자...]
# 예: bash infra/k6/run-measure.sh read-baseline.js read_C1_r1 420
# 환경 변수 K6_CSV=1이면 k6 시계열을 <접두사>.csv.gz로 남긴다.
# 결과: infra/k6/results/<시각>_<결과 이름>.json(k6 요약), _warmup.json, _stats.txt(docker stats 스트림), _docker.txt(전·후 스냅샷)
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash가 /scripts 경로를 Windows 경로로 바꾸지 않게 한다

SCRIPT="$1"; NAME="$2"; STATS_SECONDS="$3"; shift 3
COMPOSE=(docker compose -f infra/docker-compose.yml --profile perf)
RESULTS=infra/k6/results
TS=$(date +%Y%m%d-%H%M%S)
PREFIX="${TS}_${NAME}"
BACKEND=ottnavi-backend-perf-1

snapshot() {
  docker stats --no-stream --format "$1 {{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}" "$BACKEND" ottnavi-postgres-1 ottnavi-redis-perf-1 >> "$RESULTS/${PREFIX}_docker.txt"
  echo "$1 oom_restart=$(docker inspect -f '{{.State.OOMKilled}} {{.RestartCount}}' "$BACKEND")" >> "$RESULTS/${PREFIX}_docker.txt"
}

# 1. 측정 컨테이너를 같은 설정으로 재기동하고, 헬스체크가 healthy가 될 때까지 compose가 기다린다(직접 반복문을 쓰지 않는다)
"${COMPOSE[@]}" restart backend-perf
"${COMPOSE[@]}" up -d --no-deps --wait backend-perf

# 2. 측정 전용 Redis만 비운다(상세·인기·계산 캐시를 처음 상태로)
"${COMPOSE[@]}" exec -T redis-perf redis-cli FLUSHALL

# 3. 워밍업(첫 요청 지연 기록, S2b·S3 캐시 채우기)
snapshot "before-warmup"
"${COMPOSE[@]}" run --rm --no-deps k6 run -q --summary-export "/scripts/results/${PREFIX}_warmup.json" /scripts/warmup.js 2>&1 | grep -E "첫 요청|워밍업|✗" || true

# 4. 본 측정. docker stats는 백그라운드 스트림으로 기록하고 timeout(상한 초)으로 반드시 끝나게 한다
snapshot "before"
# 줄마다 유닉스 시각(초)을 붙여 단계별 CPU를 나눌 수 있게 한다. $!는 timeout 프로세스라 kill하면 docker stats도 함께 끝난다
timeout "$STATS_SECONDS" docker stats --format '{{.CPUPerc}};{{.MemUsage}}' "$BACKEND" > >(awk '{ print systime() " " $0; fflush() }' > "$RESULTS/${PREFIX}_stats.txt") 2>&1 &
STATS_PID=$!
# K6_CSV=1이면 시계열을 gzip CSV로 남긴다
OUT_ARGS=()
if [ "${K6_CSV:-0}" = "1" ]; then OUT_ARGS=(--out "csv=/scripts/results/${PREFIX}.csv.gz"); fi
"${COMPOSE[@]}" run --rm --no-deps "$@" k6 run -q "${OUT_ARGS[@]}" --summary-export "/scripts/results/${PREFIX}.json" "/scripts/${SCRIPT}" > "$RESULTS/${PREFIX}_k6.log" 2>&1 || echo "k6 종료 코드 $? (임계값 실패 포함)"
snapshot "after"
# 5. 측정이 끝났으면 스트림 기록을 바로 멈춘다(정해진 초가 먼저 지나면 timeout이 이미 끝냈다)
kill "$STATS_PID" 2>/dev/null || true
wait "$STATS_PID" 2>/dev/null || true
echo "$PREFIX"
