import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  DEFAULT_ERROR_MESSAGE,
  ERROR_MESSAGES,
  getErrorMessage,
  isKnownErrorCode,
} from '@/lib/errorMessages'

/*
 * docs/api/error-codes.md의 표 행(`| \`CODE\` | 상태 | 의미 | 화면 메시지 | ...`)에서 코드와 화면 메시지를 꺼낸다.
 * 백엔드 ErrorCodeDocumentTest와 같은 발상이다: 문서가 기준이고, 코드가 문서와 다르면 테스트가 실패한다.
 * 문서의 표 형식(열 순서)을 바꾸면 이 파싱도 함께 고친다.
 */
function parseDocumentedMessages(markdown: string): Map<string, string> {
  const rowPattern = /^\|\s*`([A-Z][A-Z0-9_]*)`\s*\|/
  const documented = new Map<string, string>()
  for (const line of markdown.split(/\r?\n/)) {
    if (!rowPattern.test(line)) continue
    // '| `CODE` | 상태 | 의미 | 화면 메시지 |' 를 |로 나누면 ['', 코드, 상태, 의미, 화면 메시지, ...] 이다
    const cells = line.split('|').map((cell) => cell.trim())
    const code = cells[1].replaceAll('`', '')
    documented.set(code, cells[4])
  }
  return documented
}

// 테스트를 실행할 때마다 현재 문서를 읽으므로 문서가 바뀌면 바로 비교 결과에 반영된다.
// Vite의 `?raw` import를 쓰지 않는 이유: 문서가 frontend 폴더 밖에 있어 Vite의 파일 접근 제한(server.fs.allow)에 막힌다.
// 제한을 넓히면 개발 서버도 frontend 밖 파일을 내주게 되므로, 테스트(Node)에서 파일을 직접 읽는다.
// 경로는 이 테스트 파일 위치 기준이다(src/lib → frontend → 저장소 루트 → docs/api).
// new URL(..., import.meta.url)을 쓰지 않는 이유: Vitest에서는 import.meta.url이 file:// 주소가 아니라 읽을 수 없다.
// import.meta.dirname은 항상 실제 폴더 경로다
const ERROR_CODES_DOCUMENT_PATH = resolve(import.meta.dirname, '../../../docs/api/error-codes.md')
const documentedMessages = parseDocumentedMessages(readFileSync(ERROR_CODES_DOCUMENT_PATH, 'utf-8'))

describe('errorMessages와 docs/api/error-codes.md 일치', () => {
  it('문서에서 오류 코드를 읽어 온다(공통 10개 + TECH 신규 4개 이상)', () => {
    // 파싱이 조용히 0건이 되면 아래 비교가 의미 없이 통과하므로 먼저 막는다
    expect(documentedMessages.size).toBeGreaterThanOrEqual(14)
  })

  it('문서의 모든 오류 코드가 표에 있고, 표에 문서에 없는 코드가 없다', () => {
    expect(Object.keys(ERROR_MESSAGES).sort()).toEqual([...documentedMessages.keys()].sort())
  })

  it('화면 메시지가 문서의 "화면 메시지" 열과 같다', () => {
    expect(ERROR_MESSAGES).toEqual(Object.fromEntries(documentedMessages))
  })
})

describe('getErrorMessage', () => {
  it('아는 코드는 표의 메시지를 돌려준다', () => {
    expect(getErrorMessage('TITLE_NOT_FOUND')).toBe('작품을 찾을 수 없습니다.')
    expect(getErrorMessage('RATE_LIMIT_EXCEEDED')).toBe(
      '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
    )
  })

  it.each(['SOMETHING_NEW', '', null, undefined, 'toString', 'constructor'])(
    '모르는 코드(%s)는 기본 메시지를 돌려준다',
    (errorCode) => {
      expect(getErrorMessage(errorCode)).toBe(DEFAULT_ERROR_MESSAGE)
    },
  )

  it('객체가 물려받은 속성 이름은 아는 코드로 보지 않는다', () => {
    expect(isKnownErrorCode('toString')).toBe(false)
    expect(isKnownErrorCode('VALIDATION_FAILED')).toBe(true)
  })
})
