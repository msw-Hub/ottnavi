-- V10: 데모 계정 역할과 로그인 방식 추가(데모 로그인, ERD users)
-- 데모 계정은 서버 설정(DEMO_LOGIN_ID·DEMO_LOGIN_PASSWORD) 한 쌍으로만 로그인하는 공용 계정이다.
-- 회원 기능은 쓰고, 관리자 화면은 조회만 하며, 탈퇴·알림 설정 변경은 막는다(TECH 4절).
-- 데모 행은 마이그레이션이 만들지 않고 첫 데모 로그인 때 서버가 만든다(role = DEMO, oauth_provider = DEMO, oauth_subject = demo).
-- 데모 데이터를 비우려면 DELETE FROM users WHERE oauth_provider = 'DEMO' 를 실행한다(하위 행은 ON DELETE CASCADE로 함께 지워진다).

ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN', 'DEMO'));

ALTER TABLE users DROP CONSTRAINT ck_users_oauth_provider;
ALTER TABLE users ADD CONSTRAINT ck_users_oauth_provider CHECK (oauth_provider IN ('GOOGLE', 'DEMO'));
