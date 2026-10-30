/*
 * 개인정보처리방침에 공개하는 운영자 연락처와 시행일이다(사용자 확정, 2026-10-27).
 * 방침 화면 여러 곳(보호책임자, 문의, 권리 행사)에서 같은 값을 쓰므로 한 곳에만 둔다.
 * 연락처나 시행일이 바뀌면 이 파일만 고치고, 방침 내용이 바뀌었으면 PRIVACY_EFFECTIVE_DATE도 새 날짜로 바꾼다.
 */

export const PRIVACY_OPERATOR_NAME = '문선우' // 운영자이자 개인정보 보호책임자
export const PRIVACY_CONTACT_EMAIL = 'dalsw7@gmail.com' // 개인정보 문의·권리 행사 이메일
export const PRIVACY_EFFECTIVE_DATE = '2026-10-30' // 시행일(ISO 형식. <time dateTime>에 그대로 쓰고 화면에는 한국어 날짜로 바꿔 보인다)
export const TERMS_EFFECTIVE_DATE = '2026-10-27' // 이용약관 시행일(같은 형식. 약관 내용이 바뀌면 새 날짜로 바꾼다)

/*
 * 빈 자리 상수(2026-10-30, 방문 통계·서버 로그 보관 추가). 값을 확인하지 못해 '[확인 필요]'로 두었다.
 * IMPORTANT: 배포 전에 아래 값을 모두 실제 값으로 채운다. 채우지 않으면 방침 화면에 대괄호 문구가 그대로 보인다.
 * 값을 채울 때는 PRIVACY_EFFECTIVE_DATE(시행일)도 배포일에 맞춰 새 날짜로 바꾼다("시행 7일 전 공지" 약속).
 */
export const GA_RETENTION_NOTE = '이벤트 단위 데이터 2개월, 사용자 단위 데이터 14개월' // 방문 통계(Google Analytics) 데이터 보관 기간. GA 관리 화면 "데이터 보관"에서 정한 값(예: 2개월 또는 14개월)
export const GRAFANA_LOG_RETENTION_NOTE = '14일' // Grafana Cloud 로그 보관 기간. Grafana 설정에서 정한 값
export const GRAFANA_REGION_NOTE = '일본' // Grafana Cloud 서버(스택) 위치. Grafana 스택을 만든 지역
export const PRIVACY_NEXT_EFFECTIVE_DATE_NOTE = '[시행일 확정 필요]' // 방문 통계·서버 로그 보관을 추가한 방침의 시행일(배포일에 맞춰 정한다)
