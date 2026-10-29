import { Button } from '@/components/ui/button'

interface OverviewPagerProps {
  page: number // 현재 쪽(0부터)
  totalPages: number // 전체 쪽 수
  label: string // 어떤 목록의 쪽 넘김인지(낭독기용. 예: "번역 후보")
  onPageChange: (page: number) => void
}

/** 목록 아래의 쪽 넘김이다. 쪽이 하나뿐이면 아무것도 그리지 않는다. */
export function OverviewPager({ page, totalPages, label, onPageChange }: OverviewPagerProps) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label={`${label} 쪽 넘김`} className="flex items-center justify-center gap-3 pt-1">
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        이전
      </Button>
      <span className="text-sm text-muted-foreground tabular-nums">
        {page + 1} / {totalPages}쪽
      </span>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        다음
      </Button>
    </nav>
  )
}
