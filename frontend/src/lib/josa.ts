// 한글 조사(이/가, 을/를, 은/는 등)를 앞말의 받침에 맞게 고르는 순수 함수 모음이다.
// "기생충을(를)"처럼 둘 다 적는 대신 "기생충을" / "오징어 게임을"로 자연스럽게 보이기 위해 쓴다.

// 조사 쌍: [받침 있을 때, 받침 없을 때]
export type JosaPair = '이/가' | '을/를' | '은/는' | '과/와' | '으로/로'

const HANGUL_BASE = 0xac00 // '가'
const HANGUL_LAST = 0xd7a3 // '힣'
const RIEUL_FINAL_INDEX = 8 // 종성 'ㄹ'의 순서(받침 번호). "으로/로"는 ㄹ 받침을 받침 없음처럼 취급한다

// 숫자 하나를 한글로 읽었을 때의 종성 번호. 0 영(ㅇ) / 1 일(ㄹ) / 3 삼(ㅁ) / 6 육(ㄱ) / 7 칠(ㄹ) / 8 팔(ㄹ)은 받침이 있고,
// 2 이 / 4 사 / 5 오 / 9 구는 받침이 없다(0). 마지막 자리만 보면 되는 이유: 조사는 맨 끝 소리에 붙기 때문이다
const DIGIT_FINAL_INDEX: Record<string, number> = {
  '0': 21, // ㅇ
  '1': RIEUL_FINAL_INDEX, // ㄹ
  '2': 0,
  '3': 16, // ㅁ
  '4': 0,
  '5': 0,
  '6': 1, // ㄱ
  '7': RIEUL_FINAL_INDEX, // ㄹ
  '8': RIEUL_FINAL_INDEX, // ㄹ
  '9': 0,
}

/**
 * 마지막 글자의 종성(받침) 번호를 돌려준다. 0이면 받침 없음, -1이면 한글이 아니라 판단할 수 없음이다.
 * 한글 음절은 유니코드에서 (초성 × 21 + 중성) × 28 + 종성 + 0xAC00 규칙으로 배열돼 있어, 28로 나눈 나머지가 종성 번호다.
 */
function getFinalConsonantIndex(word: string): number {
  const lastChar = word.trimEnd().slice(-1)
  if (lastChar === '') return -1
  // 숫자는 한글 읽기 기준으로 판단한다("시즌 1"은 "일"로 읽어 받침이 있다)
  if (lastChar in DIGIT_FINAL_INDEX) return DIGIT_FINAL_INDEX[lastChar]
  const code = lastChar.charCodeAt(0)
  if (code < HANGUL_BASE || code > HANGUL_LAST) return -1
  return (code - HANGUL_BASE) % 28
}

/**
 * 앞말에 맞는 조사만 골라 돌려준다(앞말은 붙이지 않는다).
 *
 * - 한글로 끝나면 받침 유무로 고른다. "으로/로"는 ㄹ 받침("서울로")도 받침 없음처럼 "로"를 쓴다.
 * - 숫자로 끝나면 한글로 읽는 소리로 판단한다("시즌 1을", "시즌 2를", "시즌 8로").
 * - 그 밖의 한글이 아닌 글자(영문·기호)나 빈 문자열은 발음을 알 수 없어 받침이 없는 쪽으로 처리한다.
 *   이 경우 일부 단어(예: "Netflix" → "넷플릭스 를")는 어색할 수 있어, 조사를 쓰는 문장은 한글 이름 위주로 쓴다.
 */
export function josa(word: string, pair: JosaPair): string {
  const [withFinal, withoutFinal] = pair.split('/')
  const finalIndex = getFinalConsonantIndex(word)
  if (finalIndex <= 0) return withoutFinal
  if (pair === '으로/로' && finalIndex === RIEUL_FINAL_INDEX) return withoutFinal
  return withFinal
}

// 앞말 + 조사를 붙인 문자열이다(예: withJosa('기생충', '을/를') → '기생충을')
export function withJosa(word: string, pair: JosaPair): string {
  return `${word}${josa(word, pair)}`
}
