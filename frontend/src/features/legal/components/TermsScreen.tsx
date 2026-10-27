import { Link } from 'react-router'
import {
  ContactEmailLink,
  PolicyList,
  PolicySection,
} from '@/features/legal/components/PolicyParts'
import { PRIVACY_OPERATOR_NAME, TERMS_EFFECTIVE_DATE } from '@/features/legal/legalInfo'
import { formatDate } from '@/lib/format'

/**
 * 이용약관 화면(`/terms`, 공개)이다. 정적 화면이라 데이터를 불러오지 않는다(그래서 로딩·오류·빈 상태가 없다).
 *
 * 왜 있나: 서비스가 어떤 성격이고 어디까지 책임지는지(특히 가격·제공 여부는 확인된 범위일 뿐이라는 점)를 이용자에게 미리 알린다.
 * Google 동의 화면 게시의 필수 조건은 아니지만, 다른 서비스도 두는 문서라 함께 둔다.
 *
 * 내용 원칙: 지금 있는 기능만 쓴다(알림 메일, 게시물, 포인트, 광고는 없다). 기능이 늘면 이 화면과 시행일을 함께 고친다.
 * 법률 자문을 거친 문서가 아니다. 모양과 읽기 방식은 개인정보처리방침 화면과 같다(PolicyParts 공유).
 */
export function TermsScreen() {
  const effectiveDateText = formatDate(TERMS_EFFECTIVE_DATE) ?? TERMS_EFFECTIVE_DATE // "2026년 10월 27일"

  return (
    <article className="mx-auto grid max-w-3xl gap-6 text-[0.9375rem] leading-7 break-keep">
      <header className="grid gap-2">
        <h1 className="text-2xl font-semibold">이용약관</h1>
        <p className="text-sm text-muted-foreground">
          시행일: <time dateTime={TERMS_EFFECTIVE_DATE}>{effectiveDateText}</time>
        </p>
        <p>
          이 약관은 OTT내비(이하 &ldquo;서비스&rdquo;)를 이용하는 분(이하 &ldquo;이용자&rdquo;)과
          운영자 사이의 권리와 의무, 책임을 정합니다.
        </p>
      </header>

      <PolicySection id="terms-service" title="1. 서비스의 내용">
        <p>
          서비스는 이용자가 찜한 작품과 월 예산, 월 시청 시간을 바탕으로 국내 OTT 서비스 가운데 어느
          달에 어디를 구독하면 좋을지 계산해 보여 주는 개인 운영 비영리 정보 서비스입니다.
        </p>
        <PolicyList>
          <li>
            작품 검색과 상세, 서비스별 제공 작품과 순위 보기는 로그인하지 않아도 이용할 수 있습니다.
          </li>
          <li>
            찜 목록, 내 설정, 구독 플랜 계산·저장은 Google 계정으로 로그인해야 이용할 수 있습니다.
          </li>
          <li>서비스는 무료입니다.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="terms-data" title="2. 정보의 성격과 정확성">
        <p>
          작품과 제공처 정보는 TMDB(JustWatch 기반)에서 가져오고, 서비스 요금은 각 서비스가 공개한
          자료를 조사해 정리한 것입니다. 이 정보는 시간이 지나면서 실제와 달라질 수 있습니다.
        </p>
        <PolicyList>
          <li>
            화면의 제공 여부와 추천 결과는 확정이 아니라 &ldquo;확인된 범위&rdquo;입니다. 구독하기
            전에 해당 서비스에서 직접 확인해 주세요.
          </li>
          <li>
            서비스는 정보의 정확성, 완전성, 최신성을 보증하지 않으며, 이 정보를 믿고 구독하거나
            해지해서 생긴 손해에 책임지지 않습니다. 다만 운영자의 고의 또는 중대한 과실이 있는
            경우는 예외입니다.
          </li>
          <li>이 서비스는 TMDB의 API를 사용하지만 TMDB가 보증하거나 인증한 서비스는 아닙니다.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="terms-account" title="3. 계정과 탈퇴">
        <PolicyList>
          <li>
            이용자는 Google 계정으로 로그인합니다. 서비스는 비밀번호를 따로 받지 않으며, 계정을 다른
            사람이 쓰지 않도록 관리할 책임은 이용자에게 있습니다.
          </li>
          <li>
            이용자는 언제든지{' '}
            <Link to="/settings/withdraw" className="text-primary underline underline-offset-3">
              회원 탈퇴 화면
            </Link>
            에서 탈퇴할 수 있습니다. 탈퇴하면 계정과 그에 딸린 정보가 삭제되고 되살릴 수 없습니다.
          </li>
          <li>
            개인정보의 처리는{' '}
            <Link to="/privacy" className="text-primary underline underline-offset-3">
              개인정보처리방침
            </Link>
            을 따릅니다.
          </li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="terms-duty" title="4. 이용자가 하지 말아야 할 일">
        <PolicyList>
          <li>다른 사람의 계정이나 정보를 도용하는 일</li>
          <li>서비스를 자동화된 방법으로 대량 요청하거나 정보를 긁어 가는 일</li>
          <li>서비스나 서버의 정상 운영을 방해하거나, 보안을 우회하려는 일</li>
          <li>서비스의 정보를 운영자 허락 없이 영리 목적으로 쓰는 일</li>
          <li>그 밖에 법령을 어기는 일</li>
        </PolicyList>
        <p>
          위 행위가 확인되면 운영자는 이용을 제한하거나 계정을 삭제할 수 있습니다. 이의가 있으면
          아래 문의 주소로 알려 주세요.
        </p>
      </PolicySection>

      <PolicySection id="terms-change" title="5. 서비스의 변경과 중단">
        <PolicyList>
          <li>
            기능, 화면, 데이터 범위는 운영 상황에 따라 바뀌거나 중단될 수 있습니다. 이용자에게
            불리한 큰 변경은 서비스 화면에 미리 알립니다.
          </li>
          <li>
            점검, 외부 서비스(TMDB, Google, 호스팅 업체) 장애, 천재지변처럼 운영자가 통제하기 어려운
            사유로 서비스가 멈춘 경우 책임지지 않습니다.
          </li>
          <li>무료 서비스이므로 변경·중단에 대한 별도 보상은 없습니다.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="terms-ip" title="6. 저작권">
        <p>
          서비스의 화면과 계산 방식에 대한 권리는 운영자에게 있습니다. 작품 이미지와 정보의 권리는
          각 권리자와 TMDB에 있으며, 서비스는 이를 출처와 함께 보여 줄 뿐입니다.
        </p>
      </PolicySection>

      <PolicySection id="terms-law" title="7. 준거법과 관할">
        <p>
          이 약관에는 대한민국 법을 적용하며, 서비스와 이용자 사이에 분쟁이 생기면 민사소송법에 따른
          관할 법원에서 다룹니다.
        </p>
      </PolicySection>

      <PolicySection id="terms-revision" title="8. 약관의 변경과 문의">
        <p>
          약관이 바뀌면 시행 7일 전부터 이 화면에 바뀐 내용과 새 시행일을 알립니다. 이용자에게
          불리한 변경은 시행 전에 화면에 눈에 띄게 알립니다. 변경된 약관에 동의하지 않으면 탈퇴할 수
          있습니다.
        </p>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt className="text-muted-foreground">운영자</dt>
          <dd>{PRIVACY_OPERATOR_NAME}</dd>
          <dt className="text-muted-foreground">문의</dt>
          <dd className="min-w-0">
            <ContactEmailLink />
          </dd>
        </dl>
      </PolicySection>
    </article>
  )
}
