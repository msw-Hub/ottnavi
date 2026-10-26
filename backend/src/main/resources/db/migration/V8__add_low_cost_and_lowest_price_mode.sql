-- V8: 번들·저가형 요금제와 "가장 저렴한 요금제로 계산" 모드 (Task 076)
-- 번들 구성은 기존 product_component를 그대로 쓰고(구성 서비스 ID 목록만 사용), 상품 종류 CHECK(SINGLE/BUNDLE)도 V3에 이미 있다.

-- 저가형(광고형·베이직 등) 요금 분류. false면 표준. 가격 선택에만 쓰고 시청 가능 범위 판정에는 쓰지 않는다
ALTER TABLE subscription_product
	ADD COLUMN low_cost BOOLEAN NOT NULL DEFAULT FALSE;

-- 내 설정의 "가장 저렴한 요금제로 계산할래요" 체크. 기본 꺼짐
ALTER TABLE user_setting
	ADD COLUMN lowest_price_mode BOOLEAN NOT NULL DEFAULT FALSE;
