import { describe, expect, it } from 'vitest'
import { injectBaseTarget } from '@/features/plan/mailHtml'

describe('injectBaseTarget', () => {
  it('<head> 바로 뒤에 <base target="_blank">를 끼운다', () => {
    const result = injectBaseTarget('<html><head><title>t</title></head><body>x</body></html>')
    expect(result).toBe(
      '<html><head><base target="_blank"><title>t</title></head><body>x</body></html>',
    )
  })

  it('속성이 붙은 대문자 <HEAD>도 찾는다', () => {
    expect(injectBaseTarget('<HEAD data-a="1"><meta charset="utf-8"></HEAD>')).toContain(
      '<HEAD data-a="1"><base target="_blank">',
    )
  })

  it('이미 target이 있는 <base>가 있으면 그대로 둔다', () => {
    const html = '<head><base href="/" target="_self"></head>'
    expect(injectBaseTarget(html)).toBe(html)
  })

  it('<head>가 없고 <html>만 있으면 <head>를 새로 만든다', () => {
    expect(injectBaseTarget('<html lang="ko"><body>x</body></html>')).toBe(
      '<html lang="ko"><head><base target="_blank"></head><body>x</body></html>',
    )
  })

  it('둘 다 없는 조각이면 맨 앞에 붙인다', () => {
    expect(injectBaseTarget('<p>x</p>')).toBe('<base target="_blank"><p>x</p>')
  })
})
