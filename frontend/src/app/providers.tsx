import { useState, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

interface ProvidersProps {
  children: ReactNode // 전역 Provider 안에 그릴 앱(지금은 RouterProvider)
}

/**
 * 앱 전체가 공유하는 Provider를 한곳에 모은다. 지금은 서버 상태용 TanStack Query 하나다.
 *
 * 모아 두는 이유: main.tsx와 테스트가 같은 Provider 조합을 쓰게 해, 테스트 환경이 실제 앱과 어긋나지 않게 하기 위함이다.
 * QueryClient 기본 옵션(재시도 횟수, 오류 처리 등)은 공통 인터셉터 Task 025에서 정한다. 지금은 라이브러리 기본값이다.
 */
export function Providers({ children }: ProvidersProps) {
  // useState의 초기화 함수로 QueryClient를 만드는 이유:
  // 컴포넌트 본문에서 바로 new QueryClient()를 하면 다시 그려질 때마다 새 캐시가 생겨 받아 둔 데이터가 사라진다.
  // 초기화 함수는 처음 한 번만 실행되므로 앱이 살아 있는 동안 같은 캐시를 쓴다.
  // 모듈 맨 위에 두지 않은 이유: 테스트마다 Providers를 새로 그리면 캐시도 새로 생겨 테스트끼리 데이터가 섞이지 않는다.
  const [queryClient] = useState(() => new QueryClient())

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
