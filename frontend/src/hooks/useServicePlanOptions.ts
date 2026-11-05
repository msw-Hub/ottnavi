import { useListMyServicePrices } from '@/api/generated/product/product'

// 서비스 ID별 요금제(단품 상품) 이름 목록. 키는 서비스 ID, 값은 중복 없는 이름 배열이다
export type ServicePlanOptions = Record<number, string[]>

// 요금 목록이 로딩 중이거나 실패했을 때 쓰는 빈 값. 매번 새 객체를 만들지 않도록 상수로 둔다
const NO_OPTIONS: ServicePlanOptions = {}

/**
 * 설정 화면의 "요금제 이름" 선택 상자에 넣을 서비스별 요금제 이름을 요금 목록 API(계약 listMyServicePrices)에서 만든다.
 *
 * 선택지에는 그 서비스 하나만 포함하는 단품 상품(productType SINGLE, ottServiceIds 길이 1)의 이름만 넣는다.
 * 번들은 서비스 하나의 "요금제 이름"이 아니므로 뺀다. 저가형(광고형·베이직)도 단품이면 넣는다.
 *
 * 왜 src/hooks인가: 요금 목록 훅은 features/pricing이 감싸 쓰지만, 설정 화면(features/onboarding)이 그 훅을 가져다 쓰면
 * 기능 폴더끼리 import하는 규칙 위반이다. 그래서 같은 생성 훅(useListMyServicePrices)을 여기서 따로 감싼다.
 * queryKey가 요금 화면과 같아 캐시를 나눠 쓰므로 요청이 겹치지 않는다.
 *
 * 로딩 중·조회 실패일 때는 빈 객체를 돌려준다. 화면은 선택지가 없으면 지금처럼 직접 입력 칸만 보이므로
 * 이 조회가 느리거나 실패해도 설정 저장은 막히지 않는다(선택지는 편의 기능일 뿐이다).
 * 로그인한 상태에서만 의미가 있다(설정 화면은 이미 로그인 필요 라우트). 선택한 이름은 저장할 때 글자(planName)로만 보내고
 * 상품과 연결하지 않는다(계약·백엔드 변경 없음, 사용자 결정 2026-11-03).
 */
export function useServicePlanOptions(): ServicePlanOptions {
  const { data } = useListMyServicePrices({
    query: {
      // 1. 단품만 골라 서비스 ID별로 모은다. select 결과는 입력이 같으면 TanStack Query가 참조를 유지해 다시 그려지지 않는다
      select: (res): ServicePlanOptions => {
        const options: ServicePlanOptions = {}
        for (const price of res.data.services) {
          if (price.productType !== 'SINGLE' || price.ottServiceIds.length !== 1) continue
          const serviceId = price.ottServiceIds[0]
          const names = (options[serviceId] ??= [])
          // 2. 같은 이름이 두 번 나와도 선택지는 한 번만 둔다
          if (!names.includes(price.productName)) names.push(price.productName)
        }
        return options
      },
    },
  })
  return data ?? NO_OPTIONS
}
