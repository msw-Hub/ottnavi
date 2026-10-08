import { DatabaseIcon } from 'lucide-react'

// JustWatch 공식 사이트 주소(출처 링크)
const JUSTWATCH_URL = 'https://www.justwatch.com/'

/**
 * 제공처(어느 OTT에서 볼 수 있는지) 정보의 출처를 밝힌다. 제공처 정보가 보이는 화면에는 반드시 둔다(frontend.md 출처 표기).
 *
 * 왜 필요한가: 제공처 데이터는 TMDB가 JustWatch에서 받아 제공한다. TMDB Watch Providers API 문서는 이 데이터를 쓸 때
 * JustWatch 출처를 표기하라고 요구하고, 지키지 않으면 API 접근을 취소할 수 있다고 적었다(docs/TASK003_POLICY_PRICING.md).
 * 화면마다 직접 쓰면 빠뜨리기 쉬워 컴포넌트로 만들었다.
 * TMDB 로고와 고지문은 About 화면(Task 027)이 따로 맡는다.
 *
 * 외부 링크에 rel="noopener noreferrer"를 붙이는 이유: 새 탭에서 열린 외부 페이지가 window.opener로 이 페이지를
 * 조작하지 못하게 하고(noopener), 어느 주소에서 왔는지(Referer)도 넘기지 않는다(noreferrer).
 */
export function SourceAttribution() {
  return (
    <p className="flex items-start gap-1.75 text-sm text-muted-foreground">
      <DatabaseIcon aria-hidden="true" className="mt-0.75 size-4 shrink-0" />
      <span>
        제공처 정보 출처:{' '}
        <a
          href={JUSTWATCH_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary underline underline-offset-3"
        >
          JustWatch
          {/* 새 탭으로 열린다는 사실을 화면 낭독기 사용자에게도 알린다 */}
          <span className="sr-only">(새 탭에서 열림)</span>
        </a>{' '}
        (TMDB를 통해 제공)
      </span>
    </p>
  )
}
