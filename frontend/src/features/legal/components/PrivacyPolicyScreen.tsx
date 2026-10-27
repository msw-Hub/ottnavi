import { Link } from 'react-router'
import {
  ContactEmailLink,
  PolicyList,
  PolicySection,
} from '@/features/legal/components/PolicyParts'
import { PRIVACY_EFFECTIVE_DATE, PRIVACY_OPERATOR_NAME } from '@/features/legal/legalInfo'
import { formatDate } from '@/lib/format'

/**
 * 개인정보처리방침 화면(`/privacy`, 공개)이다. 정적 화면이라 데이터를 불러오지 않는다(그래서 로딩·오류·빈 상태가 없다).
 *
 * 왜 있나: Google 로그인 동의 화면을 프로덕션으로 게시하려면 공개된 개인정보처리방침 주소가 필요하다.
 *
 * 내용 원칙: 코드에서 확인한 사실만 쓴다. 확인하지 못한 것(호스팅 제공사의 처리 지역, 제공사 접속 기록 등)은
 * "…일 수 있습니다"로 단정하지 않는다. 근거 파일은 PR 보고서의 표에 정리했다.
 * 서비스 기능이 바뀌면(알림 메일 제공 등) 이 화면과 시행일(features/legal/legalInfo.ts)을 함께 고친다.
 *
 * 모양: 375px에서도 읽기 좋은 한 열. 줄 길이가 너무 길지 않게 max-w-3xl, 본문은 15px·줄 간격 1.75로 About보다 조금 넉넉히 둔다.
 * 제목 계층은 h1(화면 제목) → h2(구역)만 써서 목차 없이도 위에서 아래로 훑을 수 있게 한다. 색은 디자인 토큰만 쓴다.
 */
export function PrivacyPolicyScreen() {
  const effectiveDateText = formatDate(PRIVACY_EFFECTIVE_DATE) ?? PRIVACY_EFFECTIVE_DATE // "2026년 10월 27일"

  return (
    <article className="mx-auto grid max-w-3xl gap-6 text-[0.9375rem] leading-7 break-keep">
      <header className="grid gap-2">
        <h1 className="text-2xl font-semibold">개인정보처리방침</h1>
        {/* 시행일은 제목 바로 아래에 둬서 "언제 기준의 방침인지"를 먼저 보이게 한다 */}
        <p className="text-sm text-muted-foreground">
          시행일: <time dateTime={PRIVACY_EFFECTIVE_DATE}>{effectiveDateText}</time>
        </p>
        <p>
          OTT내비(이하 &ldquo;서비스&rdquo;)는 개인이 운영하는 비영리 정보 서비스입니다. 서비스는
          이용자의 개인정보를 꼭 필요한 만큼만 처리하며, 어떤 정보를 왜 처리하고 언제 지우는지 이
          방침으로 알려 드립니다.
        </p>
      </header>

      <PolicySection id="privacy-items" title="1. 처리하는 개인정보">
        <p>Google 계정으로 로그인할 때 Google에서 다음 정보를 받아 저장합니다.</p>
        <PolicyList>
          <li>이메일 주소</li>
          <li>표시 이름</li>
          <li>Google 계정의 고유 번호(로그인한 사람을 구별하는 값)</li>
        </PolicyList>
        <p>서비스를 이용하면서 이용자가 직접 입력한 다음 정보를 저장합니다.</p>
        <PolicyList>
          <li>
            내 설정: 이용 중인 OTT 서비스(요금제 이름과 결제일을 적은 경우 포함), 월 예산, 월 시청
            시간과 그 시간을 고른 방식, 요금 계산 방식, 알림 수신 여부
          </li>
          <li>찜 목록: 찜한 작품, 보고 싶은 정도, &ldquo;다 봤음&rdquo; 표시</li>
          <li>저장한 구독 플랜과 계산 결과</li>
          <li>직접 고친 서비스 요금</li>
        </PolicyList>
        <p>
          위 정보에는 처음 만든 시각과 마지막으로 바꾼 시각이 함께 기록됩니다. 서비스는
          주민등록번호, 결제 정보 같은 민감한 정보를 받지 않습니다.
        </p>
      </PolicySection>

      <PolicySection id="privacy-auto" title="2. 이용 과정에서 자동으로 생기는 정보">
        <PolicyList>
          <li>
            접속 IP 주소: 과도한 요청을 막기 위해(요청 제한) 로그인하지 않은 화면의 요청마다 IP
            주소를 확인합니다. 이를 위해 IP 주소를 Redis Cloud에 짧은 시간(대개 1분 이내) 보관하며,
            시간이 지나면 자동으로 지워집니다.
          </li>
          <li>
            서버와 호스팅 제공사의 접속 기록에 IP 주소, 접속 시각, 요청한 주소가 남을 수 있습니다.
            이 기록은 서비스 운영과 오류 대응에만 씁니다.
          </li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="privacy-purpose" title="3. 개인정보를 이용하는 목적">
        <PolicyList>
          <li>로그인과 본인 확인</li>
          <li>찜한 작품, 월 예산, 월 시청 시간을 바탕으로 한 구독 플랜 계산과 계산 결과 저장</li>
          <li>서비스 운영, 과도한 요청 방지, 오류 확인과 대응</li>
        </PolicyList>
        <p className="text-muted-foreground">
          서비스는 아직 알림 메일을 보내지 않습니다. 알림 메일을 제공하게 되면 이 방침을 먼저 고친
          뒤 알려 드립니다.
        </p>
      </PolicySection>

      <PolicySection id="privacy-cookies" title="4. 쿠키와 브라우저 저장소">
        <PolicyList>
          <li>
            로그인 유지 쿠키: 로그인 상태를 이어 가기 위한 값을 HttpOnly 쿠키(화면의 스크립트가 읽을
            수 없는 쿠키)로 보관합니다. 로그인 관련 주소(/api/auth)에만 전송되며 최대 14일 동안
            유효하고, 로그아웃하거나 탈퇴하면 지워집니다.
          </li>
          <li>
            로그인 진행 쿠키: Google 로그인을 진행하는 동안에만 로그인 요청을 확인하는 쿠키를
            잠깐(최대 3분) 씁니다.
          </li>
          <li>
            로그인 중 접속 정보(Access Token)는 브라우저 메모리에만 두고 브라우저 저장소에 쓰지
            않습니다. 탭을 닫으면 사라집니다.
          </li>
          <li>
            로그인한 뒤 원래 보던 화면으로 돌아가기 위해, 그 화면 주소를 브라우저의 세션 저장소에
            잠깐 적어 두고 로그인이 끝나면 지웁니다.
          </li>
        </PolicyList>
        <p>서비스는 별도의 광고·행동 추적 도구를 사용하지 않습니다.</p>
        <p>
          브라우저 설정(예: 사이트 권한 또는 개인정보 설정의 쿠키 항목)에서 쿠키를 막을 수 있습니다.
          다만 쿠키를 막으면 로그인이 필요한 기능을 이용하기 어렵습니다.
        </p>
      </PolicySection>

      <PolicySection id="privacy-retention" title="5. 보관 기간과 파기">
        <PolicyList>
          <li>개인정보는 회원 탈퇴 전까지 보관합니다.</li>
          <li>
            회원 탈퇴를 하면 계정과 그에 딸린 정보(내 설정, 찜 목록, 플랜, 직접 고친 요금)를 즉시
            영구 삭제하고, 로그인 유지 정보도 지웁니다. 지운 정보는 되살릴 수 없습니다.
          </li>
          <li>
            다만 아래 처리 위탁 업체의 백업에는 각 업체의 정책에 따라 일정 기간 남아 있을 수
            있습니다.
          </li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="privacy-processors" title="6. 처리 위탁과 해외 이전">
        <p>서비스를 운영하기 위해 다음 업체의 서비스를 이용합니다.</p>
        <PolicyList>
          <li>Supabase: 개인정보를 저장하는 데이터베이스(PostgreSQL)</li>
          <li>Redis Cloud: 로그인 유지 정보와 요청 제한 정보 보관</li>
          <li>Cloudtype: 서비스 서버 운영</li>
          <li>Vercel: 서비스 화면 호스팅</li>
          <li>Google: 로그인(계정 인증)</li>
        </PolicyList>
        <p>
          일부 서비스는 해외 서버를 거칠 수 있습니다. 작품과 제공처 정보는 TMDB(JustWatch 기반)에서
          가져오며, 이때 이용자의 개인정보는 TMDB로 보내지 않습니다.
        </p>
      </PolicySection>

      <PolicySection id="privacy-third-party" title="7. 제3자 제공">
        <p>
          서비스는 이용자의 개인정보를 다른 곳에 제공하지 않습니다. 다만 법령에 근거가 있는 경우는
          예외입니다.
        </p>
      </PolicySection>

      <PolicySection id="privacy-rights" title="8. 이용자의 권리와 행사 방법">
        <PolicyList>
          <li>
            언제든지{' '}
            <Link to="/settings" className="text-primary underline underline-offset-3">
              내 설정
            </Link>
            에서 입력한 정보를 고칠 수 있습니다.
          </li>
          <li>
            <Link to="/settings/withdraw" className="text-primary underline underline-offset-3">
              회원 탈퇴 화면
            </Link>
            에서 계정과 모든 정보를 직접 삭제할 수 있습니다.
          </li>
          <li>
            그 밖에 열람, 정정, 삭제, 처리 정지를 원하면 <ContactEmailLink />로 연락해 주세요.
            요청을 받으면 지체 없이 처리합니다.
          </li>
          <li>대리인을 통해서도 같은 방법으로 요청할 수 있습니다.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="privacy-contact" title="9. 개인정보 보호책임자">
        {/* 정의 목록(dl): "항목 - 값" 짝이라 낭독기가 짝으로 읽는다 */}
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt className="text-muted-foreground">이름</dt>
          <dd>{PRIVACY_OPERATOR_NAME} (운영자)</dd>
          <dt className="text-muted-foreground">이메일</dt>
          <dd className="min-w-0">
            <ContactEmailLink />
          </dd>
        </dl>
      </PolicySection>

      <PolicySection id="privacy-changes" title="10. 방침의 변경">
        <p>
          이 방침이 바뀌면 시행 7일 전부터 이 화면에 바뀐 내용과 새 시행일을 알립니다. 지금 방침의
          시행일은 {effectiveDateText}입니다.
        </p>
        <p>
          서비스 이용에 관한 약속은{' '}
          <Link to="/terms" className="text-primary underline underline-offset-3">
            이용약관
          </Link>
          에서 볼 수 있습니다.
        </p>
      </PolicySection>
    </article>
  )
}
