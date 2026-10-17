/*
 * 플랜 변경 내역(SCR-11, Task 068) 1차안의 정적 샘플 데이터다. 서버·MSW 핸들러 없이 화면 모양만 확인하려고 둔다.
 *
 * - 작품 이름·상품 이름은 플랜·요금 목업(mocks/fixtures)과 같은 것을 써서 다른 화면과 어긋나 보이지 않게 했다.
 *   다만 "어느 달에 어디서 빠졌다/들어왔다"는 화면 확인용으로 지어낸 기록이다(실제 제공 이력이 아니다).
 * - 최신순이다. 8월에는 재계산 결과가 같아 기록이 없다(변경 없는 달은 기록하지 않는다, 사용자 결정 3).
 * - 이유 4종(가격 변경 / 서비스에서 빠짐 / 새로 들어옴 / 다 봤음 체크)과 작품 변화 4종(추가 / 제외 / 달 이동 / 서비스 변경),
 *   "구독 안 함"으로 바뀐 달, 새 플랜에만 있는 달(이전 비용 null)을 모두 한 번 이상 넣었다.
 * - 계약이 정해지면 이 파일은 지우고 API 응답으로 바꾼다. 화면 컴포넌트는 props로 데이터를 받으므로 이 파일만 바꾸면 된다.
 */
import type { PlanChangeHistoryEntry } from '@/features/plan/mockTypes'

// 서비스 ID → 이름. 실제 화면은 서비스 목록 API(useOttServices)를 쓰지만, 1차안은 API 없이 그리려고 7곳 이름만 둔다
export const HISTORY_SAMPLE_SERVICE_NAMES = new Map<number, string>([
  [1, '넷플릭스'],
  [2, '디즈니+'],
  [3, '애플 TV+'],
  [4, '티빙'],
  [5, '웨이브'],
  [6, '왓챠'],
  [7, '쿠팡플레이'],
])

// 변경 내역 샘플 3건(최신순)
export const PLAN_HISTORY_SAMPLE: PlanChangeHistoryEntry[] = [
  {
    changeId: 3,
    changedAt: '2026-10-01T04:10:00+09:00',
    planType: 'RECOMMENDED',
    summary: '10월 구독이 넷플릭스에서 디즈니+로 바뀌었어요',
    reasons: [
      {
        reasonType: 'TITLE_JOINED_SERVICE',
        ottServiceId: 2,
        title: '카지노',
        seasonNumber: 1,
        description: "'카지노 시즌 1'을 10월부터 디즈니+에서 볼 수 있는 것으로 확인됐어요",
      },
      {
        reasonType: 'MARKED_WATCHED',
        ottServiceId: null,
        title: '오징어 게임',
        seasonNumber: 1,
        description: "'오징어 게임 시즌 1'을 다 봤음으로 체크해서 계산에서 뺐어요",
      },
    ],
    monthChanges: [
      {
        month: '2026-10',
        isConfirmed: true,
        previousProducts: [{ productId: 101, ottServiceId: 1, productName: '넷플릭스 스탠다드' }],
        currentProducts: [{ productId: 102, ottServiceId: 2, productName: '디즈니+ 스탠다드' }],
        previousCost: 13500,
        currentCost: 9900,
      },
      // 새 플랜에만 있는 달(이전 플랜은 9~11월, 새 플랜은 10~12월). 이전 쪽은 상품 빈 배열 + 비용 null이다.
      // '더 글로리'가 10월에서 12월로 옮겨 오며 12월에 넷플릭스를 구독한다
      {
        month: '2026-12',
        isConfirmed: false,
        previousProducts: [],
        currentProducts: [{ productId: 101, ottServiceId: 1, productName: '넷플릭스 스탠다드' }],
        previousCost: null,
        currentCost: 13500,
      },
    ],
    titleChanges: [
      {
        changeType: 'ADDED',
        title: '카지노',
        seasonNumber: 1,
        previous: null,
        current: { month: '2026-10', ottServiceId: 2 },
      },
      {
        changeType: 'REMOVED',
        title: '오징어 게임',
        seasonNumber: 1,
        previous: { month: '2026-10', ottServiceId: 1 },
        current: null,
      },
      {
        changeType: 'MOVED_MONTH',
        title: '더 글로리',
        seasonNumber: 1,
        previous: { month: '2026-10', ottServiceId: 1 },
        current: { month: '2026-12', ottServiceId: 1 },
      },
    ],
    // 절감액 변화는 화면에서 뺐다(2026-10-17 사용자 결정, mockTypes.ts 참고)
    // previousSavings: 18000,
    // currentSavings: 21600,
    dataAsOf: '2026-09-30',
  },
  {
    changeId: 2,
    changedAt: '2026-09-01T04:12:00+09:00',
    planType: 'RECOMMENDED',
    summary: '9월 구독이 웨이브에서 왓챠로 바뀌었어요',
    reasons: [
      {
        reasonType: 'PRICE_CHANGED',
        ottServiceId: 5,
        title: null,
        seasonNumber: null,
        description: '웨이브 스탠다드 요금이 월 9,900원에서 10,900원으로 오른 것으로 확인됐어요',
      },
      {
        reasonType: 'TITLE_LEFT_SERVICE',
        ottServiceId: 5,
        title: '버닝',
        seasonNumber: null,
        description: "'버닝'을 9월부터 웨이브에서 볼 수 있는지 확인되지 않아요",
      },
    ],
    monthChanges: [
      {
        month: '2026-09',
        isConfirmed: true,
        previousProducts: [{ productId: 105, ottServiceId: 5, productName: '웨이브 스탠다드' }],
        currentProducts: [{ productId: 106, ottServiceId: 6, productName: '왓챠 베이직' }],
        previousCost: 9900,
        currentCost: 7900,
      },
    ],
    titleChanges: [
      {
        changeType: 'CHANGED_SERVICE',
        title: '버닝',
        seasonNumber: null,
        previous: { month: '2026-09', ottServiceId: 5 },
        current: { month: '2026-09', ottServiceId: 6 },
      },
      {
        changeType: 'CHANGED_SERVICE',
        title: '헌트',
        seasonNumber: null,
        previous: { month: '2026-09', ottServiceId: 5 },
        current: { month: '2026-09', ottServiceId: 6 },
      },
    ],
    // previousSavings: 15000,
    // currentSavings: 17000,
    dataAsOf: '2026-08-31',
  },
  {
    changeId: 1,
    changedAt: '2026-07-01T04:08:00+09:00',
    planType: 'RECOMMENDED',
    summary: '8월에는 새로 구독하지 않아도 되도록 바뀌었어요',
    reasons: [
      {
        reasonType: 'TITLE_LEFT_SERVICE',
        ottServiceId: 4,
        title: '파묘',
        seasonNumber: null,
        description: "'파묘'를 8월부터 티빙에서 볼 수 있는지 확인되지 않아요",
      },
    ],
    monthChanges: [
      {
        month: '2026-08',
        isConfirmed: false,
        previousProducts: [{ productId: 104, ottServiceId: 4, productName: '티빙 스탠다드' }],
        currentProducts: [],
        previousCost: 13500,
        currentCost: 0,
      },
    ],
    titleChanges: [
      {
        changeType: 'REMOVED',
        title: '파묘',
        seasonNumber: null,
        previous: { month: '2026-08', ottServiceId: 4 },
        current: null,
      },
    ],
    // previousSavings: 9000,
    // currentSavings: 22500,
    dataAsOf: '2026-06-30',
  },
]
