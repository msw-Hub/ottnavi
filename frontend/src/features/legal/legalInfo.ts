/*
 * 개인정보처리방침에 공개하는 운영자 연락처와 시행일이다(사용자 확정, 2026-10-27).
 * 방침 화면 여러 곳(보호책임자, 문의, 권리 행사)에서 같은 값을 쓰므로 한 곳에만 둔다.
 * 연락처가 바뀌면 이 파일만 고친다. 방침 내용이 바뀌면 아래 PRIVACY_REVISIONS의 맨 뒤에 항목 하나를 더한다
 * (현재 시행일 PRIVACY_EFFECTIVE_DATE는 그 맨 뒤 항목에서 나오므로 날짜 상수를 따로 고치지 않는다).
 */

export const PRIVACY_OPERATOR_NAME = '문선우' // 운영자이자 개인정보 보호책임자
export const PRIVACY_CONTACT_EMAIL = 'dalsw7@gmail.com' // 개인정보 문의·권리 행사 이메일
export const TERMS_EFFECTIVE_DATE = '2026-10-27' // 이용약관 시행일(같은 형식. 약관 내용이 바뀌면 새 날짜로 바꾼다)

// 방침 변경 이력 한 건
export interface PrivacyRevision {
  effectiveDate: string // 이 변경의 시행일(ISO 형식. <time dateTime>에 그대로 쓰고 화면에는 한국어 날짜로 바꿔 보인다)
  summary: string // 무엇이 바뀌었는지 한두 문장(10절에 "시행일 변경: …" 형태로 보인다)
  previousPolicyPath?: string // 이 변경 "전" 방침 전문을 보는 화면 경로(예: /privacy/versions/2026-10-31). 없으면 링크를 보이지 않는다
}

/*
 * 방침 변경 이력이다(오래된 것이 앞, 최신이 맨 뒤). 방침 내용을 바꿀 때는 맨 뒤에 항목 하나만 더하면 되고,
 * 같은 날 두 번 바꿔도 항목을 따로 둔다. 지금 시행일은 맨 뒤 항목의 날짜다.
 * previousPolicyPath는 "이전 방침 보기" 링크를 위한 예약 칸이다. 이전 방침 전문을 남기고 싶을 때는 방침을 바꾸기 전에
 * 현재 화면의 고정 사본을 만들고 그 경로를 새 항목에 적는다. 지금은 이용자가 운영자 본인뿐이고 방침을 자주 고치는 중이라
 * 쓰지 않는다(2026-11-01 사용자 결정).
 * 방침 화면은 "앞으로 방침을 바꿀 때는 시행 7일 전부터 알린다"고 약속한다. 다만 지금은 가입자가 운영자 본인뿐이라
 * 사전 공지 대상이 없어 배포일에 바로 시행해 왔다(2026-10-31, 2026-11-01).
 */
export const PRIVACY_REVISIONS: readonly PrivacyRevision[] = [
  {
    effectiveDate: '2026-10-30',
    summary:
      '알림 메일 제공에 맞춰 알림 메일의 이용 목적과 기록, 처리 위탁 내용을 추가하고 고쳤습니다.',
  },
  {
    effectiveDate: '2026-10-31',
    summary: '방문 통계(Google Analytics)와 서버 로그 보관(Grafana Cloud)을 추가했습니다.',
  },
  {
    effectiveDate: '2026-11-01',
    summary:
      '방문 통계 수집을 동의를 받은 경우에만 하도록 바꾸고, 동의 철회·변경 방법과 거부 시 6개월 뒤 다시 묻는다는 내용을 추가했습니다.',
  },
]

// 지금 시행 중인 방침의 시행일(이력의 맨 뒤 항목)
export const PRIVACY_EFFECTIVE_DATE: string =
  PRIVACY_REVISIONS[PRIVACY_REVISIONS.length - 1]?.effectiveDate ?? ''

/*
 * 방문 통계·서버 로그 보관(2026-10-30 추가) 관련 값이다. 보관 기간과 서버 위치는 확정했다.
 * 아래 값이 바뀌면 방침 화면에 그대로 보이므로 실제 설정과 맞게 유지한다.
 */
export const GA_RETENTION_NOTE = '이벤트 단위 데이터 2개월, 사용자 단위 데이터 14개월' // 방문 통계(Google Analytics) 데이터 보관 기간. GA 관리 화면 "데이터 보관"에서 정한 값(예: 2개월 또는 14개월)
export const GRAFANA_LOG_RETENTION_NOTE = '14일' // Grafana Cloud 로그 보관 기간. Grafana 설정에서 정한 값
export const GRAFANA_REGION_NOTE = '일본' // Grafana Cloud 서버(스택) 위치. Grafana 스택을 만든 지역
