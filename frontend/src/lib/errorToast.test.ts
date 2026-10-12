import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { toastApiError } from './errorToast'

vi.mock('sonner', () => ({ toast: { error: vi.fn(), warning: vi.fn() } }))

describe('toastApiError', () => {
  beforeEach(() => {
    vi.mocked(toast.error).mockClear()
    vi.mocked(toast.warning).mockClear()
  })

  it('찜 20개 초과(WISHLIST_LIMIT_EXCEEDED)는 경고 토스트로 알린다', () => {
    toastApiError('WISHLIST_LIMIT_EXCEEDED', '찜은 20개까지 담을 수 있어요.')
    expect(toast.warning).toHaveBeenCalledWith('찜은 20개까지 담을 수 있어요.')
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('추가 옵션(버튼)은 그대로 sonner에 넘긴다', () => {
    const options = { action: { label: '찜 목록 가기', onClick: vi.fn() } }
    toastApiError('WISHLIST_LIMIT_EXCEEDED', '문구', options)
    expect(toast.warning).toHaveBeenCalledWith('문구', options)
  })

  it.each(['INTERNAL_ERROR', 'SOMETHING_NEW', null, undefined])(
    '그 밖의 오류 코드(%s)는 오류 토스트로 알린다',
    (errorCode) => {
      toastApiError(errorCode, '문구')
      expect(toast.error).toHaveBeenCalledWith('문구')
      expect(toast.warning).not.toHaveBeenCalled()
    },
  )
})
