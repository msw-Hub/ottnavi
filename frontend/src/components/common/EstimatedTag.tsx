/**
 * 값이 실제 정보가 아니라 추정값임을 알리는 "추정" 태그다(예: TMDB에 러닝타임이 없어 회차 수로 추정한 총 시청 시간).
 *
 * frontend.md 숫자 표시 규칙: 추정 러닝타임에는 "추정" 표시를 붙인다. 사용자가 정확한 값으로 오해하지 않게 하기 위함이다.
 * 점선 테두리는 "확정되지 않은 값"이라는 뜻을 모양으로도 보여 준다(제공 상태 "모름"의 점선과 같은 발상, 색은 다르다).
 */
export function EstimatedTag() {
  return (
    <span className="inline-flex items-center rounded-lg border border-dashed border-current bg-estimated-bg px-1.5 align-[0.0625rem] text-xs leading-5 font-bold text-estimated">
      추정
    </span>
  )
}
