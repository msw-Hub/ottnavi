import type { PlanMailPreviewMail } from '@/api/generated/model'

/*
 * 알림 메일 예시 팝업(Task 110) 확인용 고정 메일 두 통이다.
 * 실제 서버 메일처럼 600px 폭 표 레이아웃 + 흰 바탕이고, 링크에는 target을 일부러 달지 않았다
 * (서버 템플릿도 target이 없어 프론트가 <base target="_blank">를 끼우는 동작을 목업에서도 그대로 확인하기 위함).
 */

function buildMailHtml(heading: string, body: string, buttonLabel: string): string {
  return `<!doctype html>
<html lang="ko">
<head><meta charset="utf-8"><title>${heading}</title></head>
<body style="margin:0;padding:24px 0;background:#f3f4f6;font-family:Arial,sans-serif;color:#1f2937;">
  <table role="presentation" width="600" align="center" cellpadding="0" cellspacing="0" style="width:600px;max-width:100%;background:#ffffff;border-radius:8px;">
    <tr><td style="padding:24px 32px 8px;font-size:20px;font-weight:bold;">${heading}</td></tr>
    <tr><td style="padding:8px 32px 16px;font-size:15px;line-height:1.6;">${body}</td></tr>
    <tr><td style="padding:0 32px 24px;"><a href="https://ottnavi.shop/plan" style="display:inline-block;padding:13px 28px;font-size:15px;font-weight:bold;color:#ffffff;background:#0369a1;text-decoration:none;border-radius:6px;">${buttonLabel}</a></td></tr>
    <tr><td style="padding:16px 32px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">이 알림은 OTT내비의 <a href="https://ottnavi.shop/settings/notifications" style="color:#0369a1;">알림 설정</a> 화면에서 언제든 끌 수 있어요.</td></tr>
  </table>
</body>
</html>`
}

// 구독 안내 메일(플랜 저장 직후 도착). 이번 달에 새로 구독할 서비스를 알린다
export const MOCK_SUBSCRIBE_GUIDE_MAIL: PlanMailPreviewMail = {
  type: 'SUBSCRIBE_GUIDE',
  subject: '[OTT내비] 이번 달에는 넷플릭스를 구독하세요',
  html: buildMailHtml(
    '이번 달 구독 안내',
    '저장하신 추천형 플랜에 따라 이번 달에는 <strong>넷플릭스</strong>를 구독하면 꼭 볼 작품 2편을 볼 수 있어요.<br>월 요금은 17,000원이에요.',
    '내 플랜 보기',
  ),
}

// 월초 안내 메일(매월 1일 도착). 이번 달 구독 일정과 달라진 점을 알린다
export const MOCK_MONTHLY_MAIL: PlanMailPreviewMail = {
  type: 'MONTHLY',
  subject: '[OTT내비] 10월 구독 플랜이 다시 계산됐어요',
  html: buildMailHtml(
    '10월 플랜 안내',
    '매월 1일에 최신 제공 정보로 플랜을 다시 계산해 알려 드려요.<br>이번 달에는 <strong>티빙</strong>을 구독하는 것을 추천해요.',
    '변경 내역 보기',
  ),
}
