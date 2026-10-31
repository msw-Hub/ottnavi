import type { ReactNode } from 'react'
import { UsersIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/ErrorState'
import {
  StatsCardFrame,
  StatsCardSkeleton,
  StatTile,
} from '@/features/admin/components/monitoring/StatsCardFrame'
import { formatCountWithUnit } from '@/lib/format'
import type { AdminUserStats } from '@/api/generated/model'

export interface UserStatsCardProps {
  status: 'pending' | 'error' | 'success' // 조회 상태(TanStack Query의 status를 그대로 넘긴다)
  stats?: AdminUserStats // status가 'success'일 때의 회원 현황(GET /api/admin/user-stats 계약의 data)
  error?: unknown // status가 'error'일 때의 원인. ErrorState가 안내 문장을 꺼낸다
  onRetry?: () => void // 오류 상태의 [다시 시도](보통 refetch)
}

/**
 * 관리자 모니터링(SCR-17) "운영 현황"의 회원 현황 카드다. 데이터는 받지 않고 그리기만 한다.
 *
 * 정보 위계: 가장 먼저 알고 싶은 "지금 회원이 몇 명인가"를 큰 숫자 하나로 맨 위에 두고,
 * 그 아래 신규(오늘·이번 달)와 실제 사용 정도(활성 플랜·알림 켬)를 같은 크기의 작은 칸 4개로 나란히 둔다.
 * 세는 기준(관리자·데모 제외, 탈퇴 회원 제외)은 숫자를 잘못 읽지 않도록 제목 바로 아래에 늘 보인다.
 */
export function UserStatsCard({ status, stats, error, onRetry }: UserStatsCardProps) {
  let body: ReactNode
  if (status === 'pending') {
    body = <StatsCardSkeleton label="회원 현황을 불러오는 중입니다" tileCount={4} />
  } else if (status === 'error' || !stats) {
    // 성공인데 값이 없는 경우는 계약상 없지만, 빈 카드가 남지 않게 오류와 같은 모양으로 보인다
    body = <ErrorState error={error} onRetry={onRetry} />
  } else {
    body = (
      <div className="grid gap-3">
        {/* 대표 숫자. 큰 글자(30px)로 카드에서 가장 먼저 눈에 들어오게 한다 */}
        <dl className="grid gap-0.5">
          <dt className="text-muted-foreground">가입 회원</dt>
          <dd className="text-3xl font-semibold tabular-nums">
            {formatCountWithUnit(stats.totalUserCount, '명')}
          </dd>
        </dl>

        {/* 회원이 0명이어도 숫자 칸은 그대로 둔다(0은 실제로 센 값). 대신 비어 있는 이유를 한 줄로 알린다 */}
        {stats.totalUserCount === 0 && (
          <p className="break-keep text-muted-foreground">아직 가입한 회원이 없습니다.</p>
        )}

        <dl className="grid grid-cols-2 gap-2">
          <StatTile label="오늘 신규" value={formatCountWithUnit(stats.todayNewUserCount, '명')} />
          <StatTile
            label="이번 달 신규"
            value={formatCountWithUnit(stats.monthNewUserCount, '명')}
          />
          <StatTile
            label="활성 플랜 보유"
            value={formatCountWithUnit(stats.activePlanUserCount, '명')}
          />
          <StatTile
            label="알림 켠 회원"
            value={formatCountWithUnit(stats.notificationEnabledUserCount, '명')}
          />
        </dl>
      </div>
    )
  }

  return (
    <StatsCardFrame
      title="회원 현황"
      icon={UsersIcon}
      description="관리자·데모 계정은 빼고, 지금 남아 있는 회원만 셉니다. 탈퇴한 회원은 신규 수에서도 빠집니다."
      measuredAt={status === 'success' ? stats?.measuredAt : null}
    >
      {body}
    </StatsCardFrame>
  )
}
