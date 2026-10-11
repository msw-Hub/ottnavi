/*
 * 관리자 화면(SCR-14~16)의 "화면 표시용" 타입(Task 029).
 *
 * API 응답 타입이 아니다. API 응답 타입은 목업 단계에서는 mockTypes.ts, 계약 반영 뒤에는 orval 생성 타입을 쓴다.
 * 여기 타입은 배지가 "어떤 모양으로 그릴지"만 정한다. API 값(예: Spring Batch의 STARTED)을 이 값으로 바꾸는 일은
 * 데이터를 다루는 훅(frontend-dev 담당)이 맡는다. 그래서 백엔드 enum 이름이 정해지기 전에도 배지를 먼저 만들 수 있다.
 */

/**
 * 수집 실행 상태 4가지(화면 표시용).
 * - RUNNING: 실행 중
 * - COMPLETED: 성공
 * - FAILED: 실패(복구한 실행도 여기로 바뀐다)
 * - STOPPED: 중단됨. 서버가 갑자기 멈춰 기록만 "실행 중"으로 남은 실행이다. 판정은 서버가 해서 내려준다(사용자 결정 2026-10-11)
 */
export type RunDisplayStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'STOPPED'

/**
 * TMDB 제공처의 매핑 상태 3가지(화면 표시용).
 * - UNMAPPED: 미매핑. 아직 운영자가 확인하지 않은 제공처라 강조한다
 * - MAPPED: 7개 서비스 중 하나에 연결됨
 * - IGNORED: 관련 없음. 확인했고 쓰지 않기로 한 제공처(예: 대여·구매 전용 스토어). ERD 반영은 Task 032 안건(사용자 결정 2026-10-11)
 */
export type MappingDisplayState = 'UNMAPPED' | 'MAPPED' | 'IGNORED'
