/*
 * 개인정보처리방침에 공개하는 운영자 연락처와 시행일이다(사용자 확정, 2026-10-27).
 * 방침 화면 여러 곳(보호책임자, 문의, 권리 행사)에서 같은 값을 쓰므로 한 곳에만 둔다.
 * 연락처나 시행일이 바뀌면 이 파일만 고치고, 방침 내용이 바뀌었으면 PRIVACY_EFFECTIVE_DATE도 새 날짜로 바꾼다.
 */

export const PRIVACY_OPERATOR_NAME = '문선우' // 운영자이자 개인정보 보호책임자
export const PRIVACY_CONTACT_EMAIL = 'dalsw7@gmail.com' // 개인정보 문의·권리 행사 이메일
export const PRIVACY_EFFECTIVE_DATE = '2026-10-31' // 시행일(ISO 형식. <time dateTime>에 그대로 쓰고 화면에는 한국어 날짜로 바꿔 보인다)
export const PRIVACY_PREVIOUS_EFFECTIVE_DATE = '2026-10-30' // 바로 앞 방침(알림 메일 제공 반영)의 시행일. 10절 변경 이력의 첫 줄에 쓴다
export const TERMS_EFFECTIVE_DATE = '2026-10-27' // 이용약관 시행일(같은 형식. 약관 내용이 바뀌면 새 날짜로 바꾼다)

/*
 * 방문 통계·서버 로그 보관(2026-10-30 추가) 관련 값이다. 보관 기간과 서버 위치는 확정했고 시행일도 배포일인
 * 2026-10-31로 확정했다. 아래 값이 바뀌면 방침 화면에 그대로 보이므로 실제 설정과 맞게 유지한다.
 * 방침 화면은 "앞으로 방침을 바꿀 때는 시행 7일 전부터 알린다"고 약속한다. 다만 이번 변경은 가입자가
 * 운영자 본인뿐이라 사전 공지 대상이 없어 배포일에 바로 시행한다.
 */
export const GA_RETENTION_NOTE = '이벤트 단위 데이터 2개월, 사용자 단위 데이터 14개월' // 방문 통계(Google Analytics) 데이터 보관 기간. GA 관리 화면 "데이터 보관"에서 정한 값(예: 2개월 또는 14개월)
export const GRAFANA_LOG_RETENTION_NOTE = '14일' // Grafana Cloud 로그 보관 기간. Grafana 설정에서 정한 값
export const GRAFANA_REGION_NOTE = '일본' // Grafana Cloud 서버(스택) 위치. Grafana 스택을 만든 지역
