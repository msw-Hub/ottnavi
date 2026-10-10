import { PricingScreen } from '@/features/pricing/components/PricingScreen'

/**
 * SCR-09 요금 확인·수정 화면(`/pricing`, 로그인 필요).
 * 계산 시작과 결과 화면 이동까지 PricingScreen이 맡는다(계산 훅이 src/hooks에 있어 직접 쓸 수 있다). 페이지는 연결만 한다.
 */
export function PricingPage() {
  return <PricingScreen />
}
