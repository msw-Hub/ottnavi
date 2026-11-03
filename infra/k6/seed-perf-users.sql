-- 성능 측정용 DB 복사본(ottnavi_perf)에서만 실행한다. 개발 DB·운영 DB에서 실행하면 사용자 데이터가 모두 지워진다.
-- prepare-perf-db.ps1이 `-v users=<명수>`를 넘겨 부른다.

-- 1. 복사해 온 개발 사용자와 그에 딸린 데이터(설정·찜·플랜)를 지우고 번호를 1부터 다시 센다
TRUNCATE users RESTART IDENTITY CASCADE;

-- 2. 측정용 사용자를 만든다(sub 번호 1~N). 이메일은 실제로 쓸 수 없는 형식이다
INSERT INTO users (email, name, role, oauth_provider, oauth_subject)
SELECT 'perf-user-' || g || '@example.invalid',
       'perf-user-' || g,
       'USER',
       'GOOGLE',
       'perf-subject-' || g
FROM generate_series(1, :users) AS g;

-- 3. 확인용 출력
SELECT count(*) AS perf_users FROM users;
