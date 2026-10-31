import type { ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ContactEmailLink,
  PolicyList,
  PolicySection,
} from '@/features/legal/components/PolicyParts'
import {
  GA_RETENTION_NOTE,
  GRAFANA_LOG_RETENTION_NOTE,
  GRAFANA_REGION_NOTE,
  PRIVACY_EFFECTIVE_DATE,
  PRIVACY_OPERATOR_NAME,
  PRIVACY_PREVIOUS_EFFECTIVE_DATE,
} from '@/features/legal/legalInfo'
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
interface PrivacyPolicyScreenProps {
  // 4절에 끼워 넣을 "방문 통계 설정" 카드. 동의 상태는 features/consent가 맡고(기능 폴더끼리 import 금지) pages가 조립해 넘긴다.
  // 측정 ID가 없는 환경(목업·로컬)에서는 넘기지 않아 카드 없이 문구만 보인다
  analyticsSettingsSlot?: ReactNode
}

export function PrivacyPolicyScreen({ analyticsSettingsSlot }: PrivacyPolicyScreenProps) {
  const effectiveDateText = formatDate(PRIVACY_EFFECTIVE_DATE) ?? PRIVACY_EFFECTIVE_DATE // "2026년 10월 31일"
  // 변경 이력 첫 줄(알림 메일 변경)에 쓰는 바로 앞 방침의 시행일
  const previousDateText =
    formatDate(PRIVACY_PREVIOUS_EFFECTIVE_DATE) ?? PRIVACY_PREVIOUS_EFFECTIVE_DATE

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
          <li>
            서비스 운영과 오류 확인을 위해 서버 로그를 남기고 Grafana Cloud(Grafana Labs)에
            보관합니다. Grafana Cloud로 보내는 로그에는 이메일, 이름, 접속 IP, 로그인 토큰을 남기지
            않도록 하며, 실수로 섞인 이메일 모양의 글자는 가려서 보냅니다. 문제를 추적하기 위해
            서비스 내부에서 사용자를 구별하는 번호(회원 번호)는 로그에 남을 수 있습니다. 오류의 상세
            호출 기록은 외부로 보내지 않습니다.
          </li>
          {/* 방문 통계(Google Analytics 4). 수집 항목·IP 처리는 Google Analytics 도움말(2026-10-30 확인)에 있는 것만 썼다 */}
          {/* 이전: "방문 통계(Google Analytics): 방문자 수와 이용 현황을 파악하기 위해 Google Analytics를 씁니다. 방문한 화면 주소(…" — 동의를 받은 경우에만 수집하도록 바꿔(2026-10-31) 아래 문구로 고쳤다 */}
          <li>
            방문 통계(Google Analytics): 방문자 수와 이용 현황을 파악하기 위해 Google Analytics를
            쓰며, 방문 통계 수집에 동의한 경우에만 수집합니다. 동의하기 전에는 Google Analytics를
            불러오지 않고 쿠키도 저장하지 않으며, 거부해도 서비스 이용에 제한이 없습니다. 동의하면
            방문한 화면 주소(검색어처럼 주소 뒤에 붙는 값은 보내지 않습니다), 방문 횟수 같은 이용
            통계, 대략적인 지역, 브라우저·기기 정보, 어느 주소를 거쳐 들어왔는지(유입 경로)가
            Google로 전송됩니다. Google Analytics는 접속 IP 주소를 대략적인 지역을 알아내는 데 쓰고
            저장하기 전에 버립니다. 이 정보는 로그인 정보(이메일, 이름, 회원 번호)와 연결하지 않으며
            광고 목적으로 쓰지 않습니다. 관리자 화면과 로그인 확인 화면의 이용은 집계에서 뺍니다.
          </li>
          <li>
            알림 기록: 알림을 켠 경우 보낼 메일의 종류, 메일 내용을 만드는 데 쓰는 값(표시 이름,
            플랜 요약), 발송 상태와 시각, 실패 원인이 저장됩니다.
          </li>
        </PolicyList>
      </PolicySection>

      <PolicySection id="privacy-purpose" title="3. 개인정보를 이용하는 목적">
        <PolicyList>
          <li>로그인과 본인 확인</li>
          <li>찜한 작품, 월 예산, 월 시청 시간을 바탕으로 한 구독 플랜 계산과 계산 결과 저장</li>
          <li>서비스 운영, 과도한 요청 방지, 오류 확인과 대응</li>
          <li>방문자 수와 이용 현황 파악(방문 통계)</li>
        </PolicyList>
        {/* 이전: "서비스는 아직 알림 메일을 보내지 않습니다. 알림 메일을 제공하게 되면 이 방침을 먼저 고친 뒤 알려 드립니다." — 알림 메일 제공(2026-10-30 시행)에 맞춰 바꿨다 */}
        <p>
          <span className="font-semibold">알림 메일:</span> 이용자가 알림 설정에서 직접 켠 경우에만
          가입한 Google 계정의 이메일 주소로 구독 플랜 안내 메일(플랜을 저장했을 때의 구독 안내,
          매월 안내, 확정한 플랜의 작품이 배정된 서비스에서 빠졌을 때의 변경 안내)을 보냅니다.
          알림은 기본적으로 꺼져 있고, 알림 설정에서 언제든 끌 수 있습니다. 알림을 켜는 것을 메일
          수신에 동의하는 것으로 봅니다. 광고나 홍보 메일은 보내지 않습니다.
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
            방문 통계 쿠키(Google Analytics): 방문 통계에 동의한 경우에만 방문자를 구별하는
            쿠키(_ga)와 방문 상태를 이어 가는 쿠키(_ga_로 시작하는 쿠키)를 이 서비스 주소에
            저장합니다. 기본 보관 기간은 2년입니다. 로그인 정보와는 연결하지 않습니다.
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
        {/* 이전: "서비스는 별도의 광고·행동 추적 도구를 사용하지 않습니다." — 방문 통계(Google Analytics) 도입(2026-10-30)으로 사실과 맞게 바꿨다 */}
        <p>
          서비스는 광고나 맞춤 광고를 위한 도구를 사용하지 않습니다. 방문 통계 목적으로만 Google
          Analytics를 씁니다.
        </p>
        <p>
          브라우저 설정(예: 사이트 권한 또는 개인정보 설정의 쿠키 항목)에서 쿠키를 막을 수 있습니다.
          다만 쿠키를 막으면 로그인이 필요한 기능을 이용하기 어렵습니다.
        </p>
        {/* 이전: 이 문단 앞에 동의 절차 안내가 없었다. 동의 방식 도입(2026-10-31)으로 아래 문단을 더했다 */}
        {/* 이전: "…고를 수 있고, 거부하면 6개월 뒤에 한 번 다시 여쭙니다. 동의한 뒤에도 …" — 동의를 철회해도 거부와 같이 저장되어(consentStore.writeConsent, 선택 시각 갱신) 철회한 시점부터 6개월이라 문구를 맞췄다 */}
        <p>
          방문 통계는 동의를 받은 경우에만 수집합니다. 처음 방문하면 화면 아래 안내에서 동의 또는
          거부를 고를 수 있습니다. 거부하거나 동의를 철회하면 그 시점부터 6개월 뒤에 한 번 다시
          여쭙니다(동의를 유지하는 동안에는 다시 묻지 않습니다). 동의한 뒤에도 아래 &ldquo;방문 통계
          설정&rdquo;이나 화면 맨 아래의 &ldquo;방문 통계 설정&rdquo; 링크에서 언제든 철회하거나
          바꿀 수 있고, 철회하면 방문 통계 쿠키를 지웁니다. 방문 통계 동의 여부는 이 브라우저의
          저장소에만 기록하며 서버로 보내지 않습니다.
        </p>
        {analyticsSettingsSlot}
        <p>
          방문 통계만 막고 싶다면 Google이 제공하는 &ldquo;Google Analytics 차단 브라우저
          부가기능(opt-out add-on)&rdquo;을 설치할 수 있습니다. 이 부가기능은 웹사이트의 Google
          Analytics 스크립트가 방문 활동을 Google Analytics로 보내지 못하게 합니다.
        </p>
      </PolicySection>

      <PolicySection id="privacy-retention" title="5. 보관 기간과 파기">
        <PolicyList>
          <li>개인정보는 회원 탈퇴 전까지 보관합니다.</li>
          <li>
            회원 탈퇴를 하면 계정과 그에 딸린 정보(내 설정, 찜 목록, 플랜, 직접 고친 요금, 알림
            기록)를 즉시 영구 삭제하고, 로그인 유지 정보도 지웁니다. 지운 정보는 되살릴 수 없습니다.
          </li>
          <li>
            다만 아래 처리 위탁 업체의 백업에는 각 업체의 정책에 따라 일정 기간 남아 있을 수
            있습니다.
          </li>
          <li>
            서버 로그에 남은 회원 번호는 탈퇴해도 바로 지워지지 않고, 로그 보관 기간(
            {GRAFANA_LOG_RETENTION_NOTE})이 지나면 자동으로 삭제됩니다.
          </li>
          <li>
            방문 통계 데이터는 Google Analytics의 보관 설정에 따라 보관합니다(보관 기간:{' '}
            {GA_RETENTION_NOTE}). 개별 이용자에 묶이지 않고 합산된 통계는 이 기간이 지나도 남을 수
            있습니다. 이 데이터는 회원 정보와 연결하지 않으므로 탈퇴할 때 따로 지우지 않습니다.
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
          <li>
            Google: 로그인(계정 인증), 알림 메일 발송(Gmail), 방문 통계(Google Analytics). 알림을 켠
            이용자의 이메일 주소가 Gmail을 거쳐 전달되며, 방문 통계에는 이메일 주소를 보내지
            않습니다.
          </li>
          <li>
            Grafana Labs(Grafana Cloud): 서비스 운영 상태를 보기 위한 지표(숫자)와 서버 로그 보관.
            서버 위치: {GRAFANA_REGION_NOTE}
          </li>
        </PolicyList>
        <p>
          일부 서비스는 해외 서버를 거칠 수 있습니다. 작품과 제공처 정보는 TMDB(JustWatch 기반)에서
          가져오며, 이때 이용자의 개인정보는 TMDB로 보내지 않습니다.
        </p>
        {/* 방문 통계(Google Analytics) 국외 이전 고지. 항목은 2절·4절 문구와 같게 맞췄다.
            이전 국가는 Google Analytics 도움말에서 한국 이용자 데이터의 처리 위치를 확인하지 못해 국가명을 단정하지 않는다 */}
        {/* 제목 계층은 h1→h2만 쓰는 화면이라 h3 대신 굵은 문단으로 둔다 */}
        <p className="font-semibold">방문 통계(Google Analytics)의 국외 이전</p>
        <PolicyList>
          <li>이전받는 자: Google(Google LLC 등 Google Analytics를 운영하는 Google 계열 회사)</li>
          <li>
            이전되는 항목: 방문한 화면 주소(검색어처럼 주소 뒤에 붙는 값은 제외), 방문 횟수 같은
            이용 통계, 대략적인 지역, 브라우저·기기 등 접속 환경 정보, 유입 경로, 방문 통계 쿠키로
            만든 익명 식별 값. 이메일 주소와 회원 번호는 보내지 않습니다.
          </li>
          <li>이전 목적: 방문자 수와 이용 현황 파악</li>
          <li>
            이전 국가: Google의 데이터센터가 있는 국가로 이전될 수 있으며, 미국 등이 포함될 수
            있습니다. 정확한 국가는 Google이 정하며 서비스가 확인하지 못했습니다.
          </li>
          <li>
            이전 시기와 방법: 방문 통계에 동의한 뒤 화면을 방문할 때마다 이용자의 브라우저가
            네트워크(인터넷)를 통해 Google로 직접 전송합니다. 동의하기 전에는 전송하지 않습니다.
          </li>
          <li>보유·이용 기간: {GA_RETENTION_NOTE}</li>
          <li>
            거부 방법: 화면 아래 안내에서 &ldquo;거부&rdquo;를 고르거나, 동의한 뒤에는 위 4절의
            &ldquo;방문 통계 설정&rdquo;에서 철회할 수 있습니다. Google Analytics 차단 브라우저
            부가기능으로도 막을 수 있습니다. 거부해도 서비스 이용에 제한이 없습니다. 문의는{' '}
            <ContactEmailLink />
            (9절의 개인정보 보호책임자 연락처)로 해 주세요.
          </li>
        </PolicyList>
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
          시행일은 <span className="whitespace-nowrap">{effectiveDateText}</span>입니다.
        </p>
        <p>
          <span className="whitespace-nowrap">{previousDateText}</span> 변경: 알림 메일 제공에 맞춰
          알림 메일의 이용 목적과 기록, 처리 위탁 내용을 추가하고 고쳤습니다.
        </p>
        <p>
          <span className="whitespace-nowrap">{effectiveDateText}</span> 변경: 방문 통계(Google
          Analytics)와 서버 로그 보관(Grafana Cloud)을 추가했습니다.
        </p>
        {/* 시행일은 아직 확정 전이라 상수(PRIVACY_EFFECTIVE_DATE)를 따른다. 배포일이 다르면 사용자 결정에 따라 legalInfo.ts에서 고친다 */}
        <p>
          <span className="whitespace-nowrap">{effectiveDateText}</span> 변경: 방문 통계 수집을
          동의를 받은 경우에만 하도록 바꾸고, 동의 철회·변경 방법과 거부 시 6개월 뒤 다시 묻는다는
          내용을 추가했습니다.
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
