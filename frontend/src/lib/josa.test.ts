import { describe, expect, it } from 'vitest'
import { josa, withJosa } from '@/lib/josa'

describe('josa', () => {
  it('받침이 있으면 앞쪽 조사를 고른다', () => {
    expect(josa('기생충', '을/를')).toBe('을')
    expect(josa('범죄도시', '은/는')).toBe('는')
    expect(josa('부산행', '이/가')).toBe('이')
    expect(josa('오징어 게임', '과/와')).toBe('과')
  })

  it('받침이 없으면 뒤쪽 조사를 고른다', () => {
    expect(josa('더 글로리', '을/를')).toBe('를')
    expect(josa('올드보이', '이/가')).toBe('가')
    expect(josa('파묘', '은/는')).toBe('는')
  })

  it('"으로/로"는 ㄹ 받침을 받침 없음처럼 취급한다', () => {
    expect(josa('서울', '으로/로')).toBe('로')
    expect(josa('집', '으로/로')).toBe('으로')
    expect(josa('나무', '으로/로')).toBe('로')
  })

  it('영문·기호로 끝나면 받침 없음으로 처리한다', () => {
    expect(josa('Netflix', '을/를')).toBe('를')
    expect(josa('Hello!', '은/는')).toBe('는')
  })

  it('숫자로 끝나면 한글 읽기 기준으로 받침을 판단한다', () => {
    // 받침 있음: 0 영, 1 일, 3 삼, 6 육, 7 칠, 8 팔
    for (const digit of ['0', '1', '3', '6', '7', '8']) {
      expect(josa(`시즌 ${digit}`, '을/를')).toBe('을')
      expect(josa(`시즌 ${digit}`, '이/가')).toBe('이')
    }
    // 받침 없음: 2 이, 4 사, 5 오, 9 구
    for (const digit of ['2', '4', '5', '9']) {
      expect(josa(`시즌 ${digit}`, '을/를')).toBe('를')
      expect(josa(`시즌 ${digit}`, '이/가')).toBe('가')
    }
    expect(withJosa('오징어 게임 시즌 1', '을/를')).toBe('오징어 게임 시즌 1을')
  })

  it('숫자 뒤 "으로/로"는 ㄹ 받침(1·7·8)만 "로"이고 나머지 받침은 "으로"다', () => {
    for (const digit of ['1', '7', '8', '2', '4', '5', '9']) {
      expect(josa(`시즌 ${digit}`, '으로/로')).toBe('로')
    }
    for (const digit of ['0', '3', '6']) {
      expect(josa(`시즌 ${digit}`, '으로/로')).toBe('으로')
    }
  })

  it('빈 문자열은 받침 없음으로 처리한다', () => {
    expect(josa('', '을/를')).toBe('를')
  })

  it('끝의 공백은 무시하고 마지막 글자로 판단한다', () => {
    expect(josa('기생충 ', '을/를')).toBe('을')
  })
})

describe('withJosa', () => {
  it('앞말에 조사를 붙여 돌려준다', () => {
    expect(withJosa('기생충', '을/를')).toBe('기생충을')
    expect(withJosa('오징어 게임 시즌 2', '을/를')).toBe('오징어 게임 시즌 2를')
  })
})
