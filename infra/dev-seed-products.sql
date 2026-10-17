-- 로컬 개발용 OTT 구독상품·월 구독요금 입력값(운영에는 쓰지 않는다).
--
-- 왜 마이그레이션이 아닌가: 요금은 바뀌는 값이라 Flyway에 넣지 않고 운영자가 관리자 화면(SCR-14)에서 입력하기로 했다(V3 주석).
-- 그래서 새로 만든 로컬 DB는 상품이 비어 있고, 요금 확인·플랜 계산을 써 보려면 이 파일로 한 번 채운다.
-- 값은 docs/ROADMAP.md Task 004 가격표(조사일 2026-10-05)와 같다. 쿠팡플레이(7,890원)는 비공식 자료다.
-- 여러 번 실행해도 같은 이름의 상품이 이미 있으면 건너뛴다.
--
-- 실행(PowerShell, 저장소 루트에서):
--   Get-Content infra/dev-seed-products.sql -Raw | docker exec -i ottnavi-postgres-1 psql -U ottnavi -d ottnavi

DO $$
DECLARE
	seed RECORD;
	new_product_id BIGINT;
BEGIN
	-- 1. 서비스 코드, 상품 이름, 월 가격(원)
	FOR seed IN
		SELECT * FROM (VALUES
			('NETFLIX',       '넷플릭스 스탠다드',                  13500),
			('DISNEY_PLUS',   '디즈니+ 스탠다드',                    9900),
			('APPLE_TV_PLUS', '애플 TV+',                            6500),
			('TVING',         '티빙 스탠다드',                      13500),
			('WAVVE',         '웨이브 스탠다드',                    10900),
			('WATCHA',        '왓챠 베이직',                         7900),
			('COUPANG_PLAY',  '쿠팡플레이 와우 멤버십(광고 포함)',   7890)
		) AS v (service_code, product_name, monthly_price)
	LOOP
		-- 2. 같은 이름의 상품이 이미 있으면 건너뛴다
		IF EXISTS (SELECT 1 FROM subscription_product WHERE name = seed.product_name) THEN
			CONTINUE;
		END IF;

		-- 3. 단품 상품을 만든다
		INSERT INTO subscription_product (name, product_type, active)
		VALUES (seed.product_name, 'SINGLE', TRUE)
		RETURNING id INTO new_product_id;

		-- 4. 구성 서비스(STANDARD)를 연결한다
		INSERT INTO product_component (product_id, ott_service_id, tier)
		SELECT new_product_id, id, 'STANDARD' FROM ott_service WHERE code = seed.service_code;
		-- 서비스 코드가 없으면 구성 서비스 0행인 상품이 남지 않도록 중단한다(전체가 한 트랜잭션이라 되돌려진다)
		IF NOT FOUND THEN
			RAISE EXCEPTION '서비스 코드를 찾을 수 없다: %', seed.service_code;
		END IF;

		-- 5. 첫 가격 행: 오늘(한국 날짜)부터 적용, 확인 시각은 지금
		INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at)
		VALUES (new_product_id, seed.monthly_price, (now() AT TIME ZONE 'Asia/Seoul')::date, now());
	END LOOP;
END $$;

SELECT p.id, p.name, pr.monthly_price, pr.effective_from
FROM subscription_product p
JOIN product_price pr ON pr.product_id = p.id
ORDER BY p.id;
