import { GRAFANA_REGION_NOTE } from '@/features/legal/legalInfo'
import { cn } from '@/lib/utils'

// 처리 위탁 업체 한 곳의 표 한 줄
interface PrivacyProcessor {
  vendor: string // 업체(서비스) 이름. 행 머리(th scope="row")가 된다
  entity: string // 계약 법인과 국가, 서버 위치
  items: string // 그 업체에서 처리하는 정보
  purpose: string // 처리 목적
}

/*
 * 6절 "처리 위탁과 해외 이전"의 업체별 표 내용이다(사용자 승인 문구, 2026-11-02).
 * Grafana의 서버 위치는 legalInfo의 다른 고지와 같은 값을 쓰도록 GRAFANA_REGION_NOTE 상수에서 가져온다.
 * 렌더링마다 새 배열을 만들지 않도록 모듈 바깥에 둔다.
 */
const PRIVACY_PROCESSORS: readonly PrivacyProcessor[] = [
  {
    vendor: 'Supabase',
    entity: 'Supabase Pte. Ltd.(싱가포르), 미국 관계사가 운영 지원. 데이터베이스는 서울',
    items: '이메일, 이름, 회원 번호, 설정·찜·플랜·알림 기록',
    purpose: '개인정보 저장',
  },
  {
    vendor: 'Redis Cloud',
    entity: 'Redis 그룹(약관상 계약 법인은 미국 Redis Inc.). 서버는 서울',
    items: '로그인 유지 정보(원문이 아닌 해시), 요청 제한용 접속 IP(대개 1분 이내)',
    purpose: '로그인 유지, 요청 제한',
  },
  {
    vendor: 'Cloudtype',
    entity: '주식회사 클라우드타입(대한민국). 서버는 서울',
    items: '서비스 서버에서 처리되는 요청 전부',
    purpose: '서버 운영',
  },
  {
    vendor: 'Vercel',
    entity: 'Vercel Inc.(미국), 전 세계 엣지 네트워크',
    items: '화면 파일 전송, 서버로 가는 요청의 중계(접속 IP, 요청 주소, 로그인 쿠키)',
    purpose: '호스팅·요청 중계',
  },
  {
    vendor: 'Google',
    entity: 'Google LLC(미국)',
    items:
      '로그인 때 이메일·이름·계정 식별 값, 알림 메일을 받는 이메일 주소(Gmail), 방문 통계(별도 동의)',
    purpose: '로그인, 메일, 방문 통계',
  },
  {
    vendor: 'Grafana Labs',
    entity: `Raintank, Inc.(미국). 서버(스택)는 ${GRAFANA_REGION_NOTE}`,
    items: '서버 지표·로그(이메일·이름·IP·토큰은 남기지 않도록 함)',
    purpose: '운영 상태 확인',
  },
]

// 열 머리 이름. 넓은 화면의 표 머리(th)와 좁은 화면의 줄 라벨에 같은 말을 쓴다
const COLUMN_LABELS = {
  entity: '법인·국가',
  items: '처리하는 정보',
  purpose: '목적',
} as const

/**
 * 처리 위탁 업체 표(6절). 마크업은 table 하나만 두고 화면 폭에 따라 CSS로 모양만 바꾼다.
 *
 * 왜 이 방식인가:
 * - 단순한 방법은 4열 표를 그대로 두는 것이지만, 375px에서는 열이 너무 좁아 글자가 한두 자씩 끊기거나 가로 스크롤이 생긴다.
 * - 표와 카드 목록을 따로 두고 화면 폭에 따라 하나를 숨기면 같은 내용이 두 번 들어가 관리가 번거롭다.
 * - 그래서 table 하나에 좁은 화면(md 미만)에서는 행(tr)을 업체별 상자로 쌓고(업체명이 제목, 아래에 라벨 있는 줄 3개),
 *   md 이상에서는 원래 표 모양으로 되돌린다.
 *
 * 낭독기: display를 block/grid로 바꾸면 일부 브라우저(Safari 등)가 표의 의미를 잃으므로 role(table·row·columnheader·
 * rowheader·cell)을 명시해 "열 머리 - 값" 짝이 유지되게 한다. 좁은 화면의 줄 라벨은 눈으로 보기 위한 것이라 aria-hidden으로
 * 두고(낭독기에는 sr-only로 남아 있는 열 머리가 대신 읽힌다), 같은 말이 두 번 읽히지 않게 한다.
 * 구분은 선(border)과 굵기로만 해서 색만으로 나누지 않는다. 색은 디자인 토큰(border-border, text-muted-foreground)만 쓴다.
 */
export function PrivacyProcessorTable() {
  return (
    <table role="table" className="block w-full border-collapse text-left md:table">
      {/* 표 이름. 낭독기가 표에 들어갈 때 읽는다(화면에는 바로 위 문단이 같은 역할을 해서 숨긴다) */}
      <caption className="sr-only">처리 위탁 업체와 처리하는 정보</caption>
      {/* 열 머리: 좁은 화면에서는 눈에 보이지 않게만 숨기고(sr-only) 낭독기용으로 남긴다 */}
      <thead role="rowgroup" className="sr-only md:not-sr-only md:table-header-group">
        <tr role="row" className="md:border-b md:border-border">
          <th role="columnheader" scope="col" className="py-2 pr-3 font-semibold">
            업체
          </th>
          <th role="columnheader" scope="col" className="py-2 pr-3 font-semibold">
            {COLUMN_LABELS.entity}
          </th>
          <th role="columnheader" scope="col" className="py-2 pr-3 font-semibold">
            {COLUMN_LABELS.items}
          </th>
          <th role="columnheader" scope="col" className="py-2 font-semibold">
            {COLUMN_LABELS.purpose}
          </th>
        </tr>
      </thead>
      {/* 좁은 화면: 업체별 상자를 세로로 쌓는다(간격 gap-3은 PolicySection 안의 단락 간격과 같다) */}
      <tbody role="rowgroup" className="grid gap-3 md:table-row-group">
        {PRIVACY_PROCESSORS.map((processor) => (
          <tr
            key={processor.vendor}
            role="row"
            className="grid gap-1.5 rounded-md border border-border p-3 md:table-row md:rounded-none md:border-0 md:border-b md:p-0"
          >
            <th role="rowheader" scope="row" className="font-semibold md:py-2 md:pr-3 md:align-top">
              {processor.vendor}
            </th>
            <ProcessorCell label={COLUMN_LABELS.entity} value={processor.entity} />
            <ProcessorCell label={COLUMN_LABELS.items} value={processor.items} />
            <ProcessorCell label={COLUMN_LABELS.purpose} value={processor.purpose} isLast />
          </tr>
        ))}
      </tbody>
    </table>
  )
}

interface ProcessorCellProps {
  label: string // 좁은 화면에서 값 위에 보이는 라벨(열 머리와 같은 말)
  value: string // 칸의 값
  isLast?: boolean // 마지막 열이면 오른쪽 여백을 두지 않는다
}

// 표의 값 한 칸. 좁은 화면에서는 "라벨(작은 회색 글자) + 값" 두 줄, 넓은 화면에서는 값만 보인다
function ProcessorCell({ label, value, isLast = false }: ProcessorCellProps) {
  return (
    <td role="cell" className={cn('md:py-2 md:align-top', !isLast && 'md:pr-3')}>
      <span aria-hidden="true" className="block text-sm text-muted-foreground md:hidden">
        {label}
      </span>
      {value}
    </td>
  )
}
