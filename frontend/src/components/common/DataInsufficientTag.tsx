import { DatabaseIcon } from 'lucide-react'

/**
 * "데이터 부족" 표시. 쿠팡플레이처럼 data_quality = INSUFFICIENT인 서비스 이름 옆에 붙인다(PRD 5.6).
 *
 * 왜 따로 표시하나: 이런 서비스는 TMDB(JustWatch)에 제공 정보가 적어 "모름"이 많이 나온다.
 * 사용자가 "없음"과 "정보가 부족해 모름"을 헷갈리지 않게, 서비스 단위로 데이터가 부족하다는 사실을 글자로 알린다.
 * ServiceStatusRow 말고도 온보딩의 서비스 목록 등에서 다시 쓰려고 컴포넌트로 분리했다.
 */
export function DataInsufficientTag() {
  return (
    <span className="inline-flex items-center gap-1 rounded-lg bg-quality-insufficient-bg px-1.75 py-px align-[0.0625rem] text-xs font-bold whitespace-nowrap text-quality-insufficient">
      {/* 아이콘은 글자와 같은 뜻이라 화면 낭독기에는 숨긴다 */}
      <DatabaseIcon aria-hidden="true" className="size-3.5 shrink-0" />
      데이터 부족
    </span>
  )
}
