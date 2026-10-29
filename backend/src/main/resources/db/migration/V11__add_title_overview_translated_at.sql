-- V11: 관리자가 반영한 한국어 번역 줄거리 표시 (영어 줄거리 한국어 번역 기능, ERD title)
-- overview_translated_at이 NULL이면 번역이 아니다(TMDB가 준 줄거리이거나 줄거리가 없음). 값이 있으면 관리자가 반영한 번역이며 반영 시각이다.
-- overview_lang = 'ko'만으로는 번역과 TMDB가 원래 준 한국어 줄거리를 구분하지 못해 컬럼을 하나 더 둔다.
-- 수집 보호 규칙·영어로 되돌리기·번역분 대량 회수가 이 컬럼으로 번역만 골라낸다.
-- 기존 행은 모두 NULL로 둔다(운영에는 번역분이 없다). 번역분은 소량이라 인덱스는 만들지 않는다.

ALTER TABLE title ADD COLUMN overview_translated_at TIMESTAMPTZ;
