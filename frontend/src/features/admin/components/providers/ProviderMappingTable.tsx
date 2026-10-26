import {
  Table,
  TableBody,
  TableCaption,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  ProviderMappingRow,
  type ServiceChoice,
} from '@/features/admin/components/providers/ProviderMappingRow'
import type { AdminProvider } from '@/api/generated/model'

interface ProviderMappingTableProps {
  providers: AdminProvider[] // 보여 줄 제공처(필터·정렬이 끝난 목록)
  services: ServiceChoice[] // 매핑 서비스 select의 서비스 선택지
}

// 부모가 행에 주는 key. 저장된 값이 바뀌면 행이 새로 만들어져 입력값이 저장된 값으로 맞춰진다(ProviderMappingRow 주석)
function rowKey(provider: AdminProvider): string {
  return `${provider.providerId}:${provider.mappingState}:${provider.ottServiceId}:${provider.tier}`
}

/**
 * 제공처 매핑 목록이다(SCR-15). 넓은 화면(md 이상)은 표, 좁은 화면은 카드 목록으로 바뀐다.
 *
 * 표와 카드를 둘 다 그리고 CSS(hidden / md:hidden)로 하나만 보이게 한다. 375px에서 7열 표는 가로로 넘치고,
 * 가로 스크롤은 뒤쪽 열(저장 버튼)을 숨기므로 쓰지 않는다. display:none인 쪽은 화면 낭독기에도 읽히지 않는다.
 */
export function ProviderMappingTable({ providers, services }: ProviderMappingTableProps) {
  return (
    <>
      <div className="hidden rounded-xl border border-border md:block">
        <Table>
          <TableCaption className="sr-only">TMDB 제공처 매핑 목록</TableCaption>
          <TableHeader>
            <TableRow>
              <TableHead>상태</TableHead>
              <TableHead>provider_id</TableHead>
              <TableHead>TMDB 원본 라벨</TableHead>
              <TableHead>매핑 서비스</TableHead>
              <TableHead>등급</TableHead>
              <TableHead>최초 발견</TableHead>
              <TableHead>
                <span className="sr-only">동작</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {providers.map((provider) => (
              <ProviderMappingRow
                key={rowKey(provider)}
                provider={provider}
                services={services}
                variant="table"
              />
            ))}
          </TableBody>
        </Table>
      </div>

      <ul aria-label="TMDB 제공처 매핑 목록" className="grid gap-3 md:hidden">
        {providers.map((provider) => (
          <ProviderMappingRow
            key={rowKey(provider)}
            provider={provider}
            services={services}
            variant="card"
          />
        ))}
      </ul>
    </>
  )
}
