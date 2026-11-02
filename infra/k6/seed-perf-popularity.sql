-- 성능 측정 DB(ottnavi_perf)에만 넣는 "합성" 인기 순위 스냅샷이다(Task 100). 실제 TMDB 인기 순위가 아니다.
-- 측정 DB에는 인기 수집(TMDB 호출)을 돌릴 수 없어 popularity_snapshot이 비어 있으므로, title.popularity로 비슷한 모양을 만든다.
-- 실행(Git Bash): docker compose -f infra/docker-compose.yml exec -T postgres psql -U ottnavi -d ottnavi_perf -v ON_ERROR_STOP=1 -f - < infra/k6/seed-perf-popularity.sql
-- 실행(PowerShell): cmd /c "docker compose -f infra/docker-compose.yml exec -T postgres psql -U ottnavi -d ottnavi_perf -v ON_ERROR_STOP=1 -f - < infra\k6\seed-perf-popularity.sql"
-- 전체 순위는 서비스 1~7의 행을 합쳐 계산되므로(PopularityRankingService) "전체"용 행은 따로 넣지 않는다.

-- 1. 이전에 넣은 합성 시드를 지운다(측정 DB 전용이라 표 전체를 비운다)
TRUNCATE popularity_snapshot RESTART IDENTITY;

-- 2. 오늘(KST)부터 7일 전까지 8일 × 서비스 7곳 × 매체 2종마다,
--    그 서비스에서 볼 수 있는(AVAILABLE) 작품 중 인기도 상위 15개를 넣는다. 날마다 인기도를 ±10% 흔들어 WEEK 평균이 TODAY와 다르게 한다
WITH days AS (
	SELECT ((now() AT TIME ZONE 'Asia/Seoul')::date - d) AS snapshot_date, d
	FROM generate_series(0, 7) AS d
),
service_titles AS (
	SELECT DISTINCT a.ott_service_id, t.id AS title_id, t.media_type, t.tmdb_id, t.title, t.poster_path,
		t.release_date, t.popularity
	FROM availability a
	JOIN watch_unit w ON w.id = a.watch_unit_id
	JOIN title t ON t.id = w.title_id
	WHERE a.status = 'AVAILABLE' AND t.popularity IS NOT NULL
),
ranked AS (
	SELECT st.*, row_number() OVER (PARTITION BY st.ott_service_id, st.media_type ORDER BY st.popularity DESC, st.tmdb_id) AS rn
	FROM service_titles st
)
INSERT INTO popularity_snapshot (snapshot_date, ott_service_id, media_type, tmdb_id, title, poster_path, release_date, popularity)
SELECT days.snapshot_date, r.ott_service_id, r.media_type, r.tmdb_id, left(r.title, 300), r.poster_path, r.release_date,
	round((r.popularity * (1 + 0.1 * sin(days.d + r.tmdb_id)))::numeric, 3)
FROM ranked r
CROSS JOIN days
WHERE r.rn <= 15
ON CONFLICT ON CONSTRAINT uk_popularity_snapshot DO NOTHING;

-- 3. 확인용 출력: 전체 행 수와 날짜별 행 수
SELECT count(*) AS seeded_rows FROM popularity_snapshot;
SELECT snapshot_date, count(*) AS rows_per_day FROM popularity_snapshot GROUP BY snapshot_date ORDER BY snapshot_date;
