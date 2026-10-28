package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.model.NotificationProductItem;
import com.ottnavi.notification.service.model.NotificationTitleItem;
import com.ottnavi.notification.service.model.RenderedMail;
import com.ottnavi.notification.service.model.SubscribeGuidePayload;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 메일 템플릿 렌더링을 확인한다(Thymeleaf + Spring 7 조합이 실제로 렌더링되는지 포함).
 * 막으려는 사고: 유형별 제목이 바뀌는 일, "확인된 범위" 고지·출처·기준일이 빠지는 일, payload의 HTML이 그대로 실려 나가는 일.
 */
class NotificationMailRendererTest extends IntegrationTestSupport {

    private static final String GUIDE_PAYLOAD = """
            {"userName":"홍길동","dataDate":"2026-11-03","summary":"11월에는 넷플릭스 하나로 꼭 볼 작품을 모두 볼 수 있어요.",
             "joinProducts":[{"productName":"넷플릭스 스탠다드","serviceNames":["넷플릭스"],"appliedPrice":13500}],
             "cancelReserveProducts":[{"productName":"웨이브 스탠다드","serviceNames":["웨이브"],"appliedPrice":10900}],
             "titles":[{"title":"카지노","seasonNumber":1,"serviceName":"넷플릭스"}],"monthCost":13500}""";

    private static final String CHANGE_PAYLOAD = """
            {"userName":"홍길동","dataDate":"2026-11-18","summary":"이번 달에 볼 작품이 배정된 서비스에서 빠졌어요.",
             "leftTitles":[{"title":"카지노","seasonNumber":1,"serviceName":"넷플릭스"}],
             "changeReasons":["카지노가 넷플릭스에서 제공되지 않게 되었어요."],
             "currentProducts":[{"productName":"웨이브 스탠다드","serviceNames":["웨이브"],"appliedPrice":10900}]}""";

    @Autowired
    private NotificationMailRenderer renderer;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private Clock clock;

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
    void 값이_있으면_구역이_그려지고_금액에_쉼표와_시즌이_붙는다() {
        // when
        String guide = renderer.render(NotificationType.SUBSCRIBE_GUIDE, GUIDE_PAYLOAD).htmlBody();
        String change = renderer.render(NotificationType.PLAN_CHANGE, CHANGE_PAYLOAD).htmlBody();

        // then
        assertThat(guide).contains("이번 달 구독할 서비스", "이번 달에 볼 작품", "넷플릭스 스탠다드", "13,500", "카지노", "시즌 1",
                "[해지 예약 권장]", "웨이브 스탠다드", "10,900", "내 플랜 자세히 보기");
        assertThat(change).contains("[변경] 배정된 서비스에서 빠진 작품", "넷플릭스", "에서 제공 종료", "바뀐 이유", "바뀐 플랜 확인하기");
    }

    @Test
    void 값이_없는_구역은_소제목까지_그려지지_않는다() {
        // when
        String guide = renderer.render(NotificationType.SUBSCRIBE_GUIDE, "{\"joinProducts\":[],\"cancelReserveProducts\":null}").htmlBody();
        String monthly = renderer.render(NotificationType.MONTHLY, "{}").htmlBody();
        String change = renderer.render(NotificationType.PLAN_CHANGE, "{\"changeReasons\":[]}").htmlBody();

        // then
        assertThat(guide).doesNotContain("이번 달 구독할 서비스", "해지 예약 권장", "이번 달 비용");
        assertThat(monthly).doesNotContain("이번 달에 볼 작품", "지난 플랜과 달라진 이유", "이미 구독 중이라");
        assertThat(change).doesNotContain("[변경]", "바뀐 이유", "바뀐 플랜의 이번 달 구독");
    }

    @Test
    void 작품_제목의_HTML도_글자로_나온다() {
        // when
        String html = renderer.render(NotificationType.MONTHLY,
                "{\"titles\":[{\"title\":\"<script>alert(1)</script>\",\"seasonNumber\":null,\"serviceName\":\"웨이브\"}]}").htmlBody();

        // then
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("시즌");
    }

    @Test
    void 링크는_기본_주소_끝_슬래시와_상관없이_고정_경로로_만들어진다() {
        // given: 기본 주소 끝에 슬래시가 여러 개 붙은 경우
        NotificationMailRenderer slashRenderer = new NotificationMailRenderer(templateEngine, clock, "https://ottnavi.example//");

        // when
        String html = slashRenderer.render(NotificationType.MONTHLY, "{\"userName\":\"x\"}").htmlBody();

        // then
        assertThat(html).contains("href=\"https://ottnavi.example/plan\"")
                .contains("href=\"https://ottnavi.example/settings/notifications\"")
                .doesNotContain("example//");
    }

    @Test
    void payload에_이름이_없으면_기본_인사말을_쓴다() {
        // when
        RenderedMail mail = renderer.render(NotificationType.MONTHLY, "{}");

        // then
        assertThat(mail.htmlBody()).contains("회원");
    }

    @Test
    void payload_record를_넘기면_JSON으로_바꿔_같은_메일을_만든다() {
        // given
        SubscribeGuidePayload payload = new SubscribeGuidePayload(SubscribeGuidePayload.CURRENT_VERSION, "홍길동",
                "2026-11-03", "2026-11", "11월에는 넷플릭스 스탠다드에 가입해 작품 1개를 볼 수 있어요.",
                List.of(new NotificationProductItem("넷플릭스 스탠다드", List.of("넷플릭스"), 13_500)), List.of(),
                List.of(new NotificationTitleItem("카지노", 1, "넷플릭스")), 13_500);

        // when
        RenderedMail mail = renderer.render(NotificationType.SUBSCRIBE_GUIDE, payload);

        // then
        assertThat(mail.subject()).isEqualTo("[OTT내비] 저장한 구독 플랜 안내");
        assertThat(mail.htmlBody()).contains("홍길동", "넷플릭스 스탠다드", "카지노", "2026-11-03");
    }
}
