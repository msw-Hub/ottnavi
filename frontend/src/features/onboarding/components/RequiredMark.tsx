/**
 * 필수 입력 칸의 이름 옆에 붙이는 "필수" 표시다.
 *
 * 색만으로 알리지 않으려고 "필수"라는 글자를 그대로 적고 테두리를 둘렀다. 입력 칸에는 aria-required를 따로 붙이므로
 * 화면 낭독기는 이 글자를 건너뛰어도(aria-hidden) 필수임을 읽어 준다. 낭독기가 라벨에서 두 번 읽지 않게 하려는 것이다.
 */
export function RequiredMark() {
  return (
    <span
      aria-hidden="true"
      className="ml-1.5 rounded-sm border border-destructive px-1 py-px align-middle text-xs font-semibold text-destructive"
    >
      필수
    </span>
  )
}
