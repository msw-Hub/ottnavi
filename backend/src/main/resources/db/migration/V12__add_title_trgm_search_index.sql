-- V12: 작품 제목 검색용 trigram(3글자 조각) 인덱스 (Task 100 개선, K6_SEARCH_BOTTLENECK 5장)
-- 검색은 `title ilike '%키워드%' or original_title ilike '%키워드%'`라 앞쪽이 %이면 일반 인덱스를 못 쓰고 표 전체를 두 번 훑는다.
-- pg_trgm의 GIN 인덱스는 문자열을 3글자 조각으로 나눠 색인하므로 3글자 이상 검색부터 인덱스를 탄다(1~2글자는 효과가 없다).
-- 확장은 `extensions` 스키마에 둔다: 운영(Supabase)은 이미 이 스키마가 있고 검색 경로에 들어 있으며,
-- 로컬·Testcontainers DB에는 없으므로 먼저 만든다. 연산자 클래스도 `extensions.`를 붙여 어느 DB에서나 같은 문장이 동작하게 한다.

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

CREATE INDEX idx_title_title_trgm ON title USING gin (title extensions.gin_trgm_ops);
CREATE INDEX idx_title_original_title_trgm ON title USING gin (original_title extensions.gin_trgm_ops);
