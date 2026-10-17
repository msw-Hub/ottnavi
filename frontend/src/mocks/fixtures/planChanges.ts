/*
 * 플랜 변경 내역(SCR-11, Task 070) 목업 데이터다. 계약의 PlanChangeHistoryEntry 모양 그대로이고 GET /api/me/plan-changes가 돌려준다.
 * (Task 068 1차안의 정적 샘플을 생성 타입에 맞춰 옮겼다.)
 *
 * - 작품 이름·상품 이름은 플랜·요금 목업(mocks/fixtures)과 같은 것을 써서 다른 화면과 어긋나 보이지 않게 했다.
 *   다만 "어느 달에 어디서 빠졌다/들어왔다"는 화면 확인용으로 지어낸 기록이다(실제 제공 이력이 아니다).
 * - 최신순이다. 8월에는 재계산 결과가 같아 기록이 없다(변경 없는 달은 기록하지 않는다, 사용자 결정 3).
 * - 이유 4종과 작품 변화 4종(추가 / 제외 / 달 이동 / 서비스 변경), "구독 안 함"으로 바뀐 달, 새 플랜에만 있는 달(이전 비용 null)을 한 번 이상 넣었다.
 * - 상품의 ottServiceIds는 계약상 배열이다(번들이면 여러 개). 첫 건의 12월 상품은 번들 표시를 확인하려고 두 서비스로 두었다.
 */
import type { PlanChangeHistoryEntry } from '@/api/generated/model'

// 변경 내역 3건(최신순)
export const MOCK_PLAN_CHANGES: PlanChangeHistoryEntry[] = [
  {
    changeId: 3,
    changedAt: '2026-10-01T04:10:00+09:00',
    planType: 'RECOMMENDED',
    summary: '10월 구독이 넷플릭스에서 디즈니+로 바뀌었어요',
    reasons: [
      {
        reasonType: 'TITLE_JOINED_SERVICE',
        description: "'카지노 시즌 1'을 10월부터 디즈니+에서 볼 수 있는 것으로 확인됐어요",
      },
      {
        reasonType: 'MARKED_WATCHED',
        description: "'오징어 게임 시즌 1'을 다 봤음으로 체크해서 계산에서 뺐어요",
      },
    ],
    monthChanges: [
      {
        month: '2026-10',
        isConfirmed: true,
        previousProducts: [
          { productId: 101, ottServiceIds: [1], productName: '넷플릭스 스탠다드' },
        ],
        currentProducts: [{ productId: 102, ottServiceIds: [2], productName: '디즈니+ 스탠다드' }],
        previousCost: 13500,
        currentCost: 9900,
      },
      // 새 플랜에만 있는 달(이전 플랜은 9~11월, 새 플랜은 10~12월). 이전 쪽은 상품 빈 배열 + 비용 null이다
      {
        month: '2026-12',
        isConfirmed: false,
        previousProducts: [],
        currentProducts: [
          { productId: 201, ottServiceIds: [1, 4], productName: '넷플릭스·티빙 번들' },
        ],
        previousCost: null,
        currentCost: 17000,
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
        description: '웨이브 스탠다드 요금이 월 9,900원에서 10,900원으로 오른 것으로 확인됐어요',
      },
      {
        reasonType: 'TITLE_LEFT_SERVICE',
        description: "'버닝'을 9월부터 웨이브에서 볼 수 있는지 확인되지 않아요",
      },
    ],
    monthChanges: [
      {
        month: '2026-09',
        isConfirmed: true,
        previousProducts: [{ productId: 105, ottServiceIds: [5], productName: '웨이브 스탠다드' }],
        currentProducts: [{ productId: 106, ottServiceIds: [6], productName: '왓챠 베이직' }],
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
        description: "'파묘'를 8월부터 티빙에서 볼 수 있는지 확인되지 않아요",
      },
    ],
    monthChanges: [
      {
        month: '2026-08',
        isConfirmed: false,
        previousProducts: [{ productId: 104, ottServiceIds: [4], productName: '티빙 스탠다드' }],
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
    dataAsOf: '2026-06-30',
  },
]
