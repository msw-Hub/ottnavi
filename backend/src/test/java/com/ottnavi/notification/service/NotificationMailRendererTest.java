package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.model.RenderedMail;
import com.ottnavi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 메일 템플릿 렌더링을 확인한다(Thymeleaf + Spring 7 조합이 실제로 렌더링되는지 포함).
 * 막으려는 사고: 유형별 제목이 바뀌는 일, "확인된 범위" 고지·출처·기준일이 빠지는 일, payload의 HTML이 그대로 실려 나가는 일.
 */
class NotificationMailRendererTest extends IntegrationTestSupport {

    @Autowired
    private NotificationMailRenderer renderer;

    @Test
    void 세_유형_모두_제목이_고정_문구로_시작하고_꼬리말이_붙는다() {
        for (NotificationType type : NotificationType.values()) {
            // when
            RenderedMail mail = renderer.render(type, "{\"userName\":\"홍길동\",\"dataDate\":\"2026-11-01\"}");

            // then
            assertThat(mail.subject()).startsWith("[OTT내비]");
            assertThat(mail.htmlBody())
                    .contains("홍길동")
                    .contains("확인된 범위")
                    .contains("TMDB")
                    .contains("JustWatch")
                    .contains("데이터 기준일")
                    .contains("2026-11-01")
                    .contains("알림 설정");
        }
    }

    @Test
    void 유형마다_제목이_다르다() {
        // when
        String guide = renderer.render(NotificationType.SUBSCRIBE_GUIDE, "{}").subject();
        String monthly = renderer.render(NotificationType.MONTHLY, "{}").subject();
        String change = renderer.render(NotificationType.PLAN_CHANGE, "{}").subject();

        // then
        assertThat(guide).isNotEqualTo(monthly);
        assertThat(monthly).isNotEqualTo(change);
        assertThat(guide).isNotEqualTo(change);
    }

    @Test
    void payload의_HTML은_이스케이프되어_실행되지_않는다() {
        // when
        RenderedMail mail = renderer.render(NotificationType.MONTHLY,
                "{\"userName\":\"<script>alert(1)</script>\",\"summary\":\"<b>굵게</b>\"}");

        // then
        assertThat(mail.htmlBody()).doesNotContain("<script>alert(1)</script>").doesNotContain("<b>굵게</b>");
        assertThat(mail.htmlBody()).contains("&lt;script&gt;");
    }

    @Test
    void payload에_이름이_없으면_기본_인사말을_쓴다() {
        // when
        RenderedMail mail = renderer.render(NotificationType.MONTHLY, "{}");

        // then
        assertThat(mail.htmlBody()).contains("회원");
    }
}
