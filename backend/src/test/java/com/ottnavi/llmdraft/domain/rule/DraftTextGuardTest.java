package com.ottnavi.llmdraft.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 번역 초안 자동 검사: 통과 사례, 이유별 실패 사례, 경고(주의) 사례, 실제 번역문 회귀 (순수 Java, 컨텍스트 없음) */
class DraftTextGuardTest {

    // 한글 비율 0.6 이상, 길이 비율 0.3~2.0, 최대 1000자 (application.yml 기본값과 같다)
    private final DraftTextGuard guard = new DraftTextGuard(new GuardLimits(0.6, 0.3, 2.0, 1000));

    private static final String ORIGINAL_50 = "word ".repeat(10);   // 50자 영어 원문

    @Test
    void 정상_번역은_통과한다() {
        // given
        String original = "A detective hunts a killer in a quiet seaside town.";
        String translated = "한 형사가 조용한 해안 마을에서 살인범을 쫓는다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void 원문의_숫자가_모두_번역문에_있으면_통과한다() {
        // given
        String original = "In 1999 and 2005 there were 3 friends.";
        String translated = "1999년과 2005년에 친구 3명이 있었다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.passed()).isTrue();
    }

    @Test
    void 원문에_이미_있던_주소는_번역문에_그대로_있어도_실패가_아니다() {
        // given
        String original = "See https://example.com for the story of a detective.";
        String translated = "형사 이야기는 https://example.com 에서 볼 수 있다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).doesNotContain(GuardFailure.CONTAINS_URL);
    }

    @Test
    void 번역문이_비어_있으면_EMPTY_하나만_돌려준다() {
        // given, when
        GuardResult result = guard.check(ORIGINAL_50, "   ");

        // then
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).containsExactly(GuardFailure.EMPTY);
    }

    @Test
    void 번역문이_최대_글자_수를_넘으면_TOO_LONG이다() {
        // given
        String original = "word ".repeat(200);   // 1000자
        String translated = "가".repeat(1001);

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.TOO_LONG);
    }

    @Test
    void 원문보다_너무_짧으면_LENGTH_RATIO_LOW이다() {
        // given
        String original = "word ".repeat(20);   // 100자
        String translated = "한글".repeat(5);    // 10자, 비율 0.1

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.LENGTH_RATIO_LOW);
    }

    @Test
    void 원문보다_너무_길면_LENGTH_RATIO_HIGH이다() {
        // given
        String original = "word ".repeat(4);          // 20자
        String translated = "한글입니다".repeat(10);    // 50자, 비율 2.5

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.LENGTH_RATIO_HIGH);
    }

    @Test
    void 영어가_그대로_남아_한글_비율이_낮으면_HANGUL_RATIO_LOW이다() {
        // given
        String translated = "plain english text ".repeat(2);

        // when
        GuardResult result = guard.check(ORIGINAL_50, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.HANGUL_RATIO_LOW);
    }

    @Test
    void 번역문에_주소가_새로_들어가면_CONTAINS_URL이다() {
        // given
        String translated = "가".repeat(20) + " https://a.com/b";

        // when
        GuardResult result = guard.check(ORIGINAL_50, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.CONTAINS_URL);
    }

    @Test
    void 마크다운_기호가_들어가면_CONTAINS_MARKDOWN이다() {
        // given
        String translated = "**" + "가".repeat(20) + "**";

        // when
        GuardResult result = guard.check(ORIGINAL_50, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.CONTAINS_MARKDOWN);
    }

    @Test
    void 번역_머리말이_들어가면_CONTAINS_PREAMBLE이다() {
        // given
        String translated = "번역: " + "가".repeat(20);

        // when
        GuardResult result = guard.check(ORIGINAL_50, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.CONTAINS_PREAMBLE);
    }

    @Test
    void 영어_머리말_Here_is도_CONTAINS_PREAMBLE이다() {
        // given
        String translated = "Here is the translation. " + "가".repeat(40);

        // when
        GuardResult result = guard.check(ORIGINAL_50, translated);

        // then
        assertThat(result.failures()).contains(GuardFailure.CONTAINS_PREAMBLE);
    }

    @Test
    void 원문의_숫자가_번역문에_없으면_NUMBER_MISSING이다() {
        // given
        String original = "In 1999 and 2005 there were 3 friends.";
        String translated = "1999년에 해커 친구들이 있었다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.NUMBER_MISSING);
    }

    @Test
    void 여러_이유가_겹치면_모두_모아_돌려준다() {
        // given: 영어 그대로(한글 비율 낮음) + 주소 + 숫자 누락
        String original = "In 1999 a detective hunts a killer.";
        String translated = "A detective hunts a killer. https://a.com";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(GuardFailure.HANGUL_RATIO_LOW, GuardFailure.CONTAINS_URL,
                GuardFailure.NUMBER_MISSING);
    }

    // 경고(주의) 규칙 ---------------------------------------------------------------------

    @Test
    void 실패도_경고도_없는_정상_번역은_경고가_비어_있다() {
        // given
        String original = "A detective hunts a killer in a quiet seaside town.";
        String translated = "한 형사가 조용한 해안 마을에서 살인범을 쫓는다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.passed()).isTrue();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void 문장_수_차이가_2_이상이면_SENTENCE_COUNT_MISMATCH_경고이고_반영을_막지_않는다() {
        // given: 원문 1문장, 번역문 4문장
        String original = "A detective hunts a killer in a quiet seaside town.";
        String translated = "형사가 있다. 마을이 있다. 살인범이 있다. 바다가 있다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.warnings()).containsExactly(GuardWarning.SENTENCE_COUNT_MISMATCH);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void 문장_수_차이가_1이면_경고가_아니다() {
        // given: 원문 1문장, 번역문 2문장
        String original = "A detective hunts a killer in a quiet seaside town.";
        String translated = "한 형사가 조용한 해안 마을에서 살인범을 쫓는다. 마을은 조용하다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.warnings()).doesNotContain(GuardWarning.SENTENCE_COUNT_MISMATCH);
    }

    @Test
    void 말줄임과_이니셜은_문장_끝으로_세지_않는다() {
        // given, when, then
        assertThat(DraftTextGuard.splitSentences("It is all work... During each episode we watch.")).hasSize(1);
        assertThat(DraftTextGuard.splitSentences("Waves, crabs and your mortality…it's all in a day's work. Then home.")).hasSize(2);
        assertThat(DraftTextGuard.splitSentences("John \"J.D.\" Dorian learns the ways of medicine.")).hasSize(1);
        assertThat(DraftTextGuard.splitSentences("He lives in the U.S. Army towns. She does not.")).hasSize(2);
        assertThat(DraftTextGuard.splitSentences("Hello. J.D. Dorian arrives! Does he stay? Yes.")).hasSize(4);
        assertThat(DraftTextGuard.splitSentences("끝 기호 없이 끝난다")).hasSize(1);
    }

    @Test
    void 한다체_문장이_80퍼센트_미만이면_STYLE_NOT_PLAIN_경고이다() {
        // given: 3문장 모두 "~요."로 끝난다
        String original = "A detective hunts a killer. The town is quiet. The sea is blue.";
        String translated = "형사가 마을에서 살인범을 쫓아요. 마을은 조용해요. 바다가 보여요.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.warnings()).containsExactly(GuardWarning.STYLE_NOT_PLAIN);
    }

    @Test
    void 한다체_문장이_정확히_80퍼센트면_경고가_아니다() {
        // given: 5문장 중 4문장이 "~다."로 끝난다
        String original = "He runs. She hides. They fight. We win. All end.";
        String translated = "그가 달린다. 그녀가 숨는다. 그들이 싸운다. 우리가 이긴다. 모두 끝났어요.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.warnings()).doesNotContain(GuardWarning.STYLE_NOT_PLAIN);
    }

    @Test
    void 한_글자_낱말_뒤에_어미로_끝나는_짧은_낱말이_떨어져_있으면_SUSPICIOUS_SPACING_경고이다() {
        // given
        String original = "A man takes his family to Los Angeles and starts again.";
        String translated = "한 남자가 가족을 데 이고 로스앤젤레스로 가서 다시 시작한다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.warnings()).containsExactly(GuardWarning.SUSPICIOUS_SPACING);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void 정상적인_한_글자_낱말과_숫자_뒤_단위는_SUSPICIOUS_SPACING_경고가_아니다() {
        // given: 한, 두, 번, 미, 매, 그리고 숫자 뒤 단위 한 글자("줄")
        String original = "A man on an island divorced twice in the Midwest, every episode, 2 lines.";
        String translated = "한 섬에서 두 번 이혼했으며 미 중서부에서 매 에피소드마다 2 줄 이다.";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.warnings()).doesNotContain(GuardWarning.SUSPICIOUS_SPACING);
    }

    @Test
    void 길이_비율이_하한에_가까우면_LENGTH_RATIO_NEAR_LIMIT_경고이다() {
        // given: 원문 100자, 번역문 35자(비율 0.35, 하한 0.3과 0.4 사이)
        String original = "word ".repeat(20);
        String translated = "가".repeat(34) + "다";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).isEmpty();
        assertThat(result.warnings()).contains(GuardWarning.LENGTH_RATIO_NEAR_LIMIT);
    }

    @Test
    void 길이_비율이_상한에_가까우면_LENGTH_RATIO_NEAR_LIMIT_경고이다() {
        // given: 원문 100자, 번역문 170자(비율 1.7, 1.6과 상한 2.0 사이)
        String original = "word ".repeat(20);
        String translated = "가".repeat(169) + "다";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).isEmpty();
        assertThat(result.warnings()).contains(GuardWarning.LENGTH_RATIO_NEAR_LIMIT);
    }

    @Test
    void 길이_비율이_한계_밖이면_경고가_아니라_실패만_난다() {
        // given: 비율 0.1(하한 미만)
        String original = "word ".repeat(20);
        String translated = "한글".repeat(5);

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.failures()).containsExactly(GuardFailure.LENGTH_RATIO_LOW);
        assertThat(result.warnings()).doesNotContain(GuardWarning.LENGTH_RATIO_NEAR_LIMIT);
    }

    @Test
    void 길이_비율이_적당하면_LENGTH_RATIO_NEAR_LIMIT_경고가_아니다() {
        // given: 비율 1.0
        String original = "word ".repeat(20);
        String translated = "가".repeat(99) + "다";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.warnings()).doesNotContain(GuardWarning.LENGTH_RATIO_NEAR_LIMIT);
    }

    @Test
    void 실패와_경고가_함께_있으면_둘_다_돌려주고_passed는_false이다() {
        // given: 번역문에 주소가 들어 있고(실패) 깨진 띄어쓰기도 있다(경고)
        String original = "A man takes his family to Los Angeles and starts again.";
        String translated = "한 남자가 가족을 데 이고 가서 다시 시작한다. https://a.com";

        // when
        GuardResult result = guard.check(original, translated);

        // then
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(GuardFailure.CONTAINS_URL);
        assertThat(result.warnings()).contains(GuardWarning.SUSPICIOUS_SPACING);
    }

    @Test
    void 번역문이_비어_있으면_경고도_없다() {
        // given, when
        GuardResult result = guard.check(ORIGINAL_50, "");

        // then
        assertThat(result.warnings()).isEmpty();
    }

    // 실제 번역문 10개 회귀(오탐 방지) ----------------------------------------------------------

    private static final String[][] SAMPLES = {
            {"In the unreal world of Sacred Heart Hospital, John \"J.D.\" Dorian learns the ways of medicine, friendship and life.",
                    "세이크리드 하트 병원의 비현실적인 세계에서, 존 \"J.D.\" 도리안은 의술과 우정, 그리고 인생을 배워간다."},
            {"WWE Superstars is a professional wrestling television program produced by WWE that originally aired on WGN America in the United States. It debuted on April 16, 2009 and ended its domestic broadcasting on April 7, 2011. After the final domestic TV broadcast the show moved to an internet broadcast format while maintaining a traditional television broadcast in international markets. The show features mid-to-low card WWE superstars and divas, in a format similar to the former show WWE Heat which served the same purpose. Big names such as John Cena and Randy Orton previously appeared on the show at its beginning. The show also previously featured talent from the now-defunct ECW brand.",
                    "WWE 슈퍼스타즈는 WWE가 제작하는 프로레슬링 텔레비전 프로그램으로, 미국에서는 원래 WGN 아메리카를 통해 방영되었다. 2009년 4월 16일에 첫 방송을 시작하여 2011년 4월 7일에 국내 방송을 종료했다. 마지막 국내 TV 방영 이후, 이 프로그램은 해외 시장에서는 전통적인 텔레비전 방송 형식을 유지하면서 인터넷 방송 형식으로 전환되었다. 이 프로그램은 같은 목적을 수행했던 이전 프로그램인 WWE 히트와 유사한 형식으로, 미드-투-로우 카드 수준의 WWE 슈퍼스타들과 디바들을 출연시킨다. 존 시나와 랜디 오턴 같은 거물급 선수들도 프로그램 초기에 출연한 적이 있다. 또한 이 프로그램에는 지금은 사라진 ECW 브랜드의 선수들도 과거에 출연했다."},
            {"The nicest guy in the Midwest moves his family into a tough neighborhood in Los Angeles where not everyone appreciates his extreme neighborliness. That includes their new next-door neighbor Calvin.",
                    "미 중서부에서 가장 친절한 남자가 가족을 데 이고 로스앤젤레스의 험난한 동네로 이사온다. 그곳의 모든 사람이 그의 극진한 친절을 반기지는 않으며, 그중에는 새로운 이웃인 캘빈도 포함되어 있다."},
            {"Forty-foot waves, 700 pound crab pots, freezing temperatures and your mortality staring you in the face…it's all in a day's work for these modern day prospectors. During each episode we will watch crews race to meet their quota and make it home safely.",
                    "40피트의 파도, 700파운드의 게 통발, 영하의 기온, 그리고 눈앞에 닥친 죽음의 공포... 이 모든 것은 현대의 개척자들에게는 일상적인 일일 뿐이다. 매 에피소드마다 우리는 선원들이 할당량을 채우고 무사히 집으로 돌아가기 위해 사투를 벌이는 모습을 지켜보게 된다."},
            {"Animated preschool series about a clubhouse that is run by a big dog called Duggee.",
                    "큰 개 더기가 운영하는 클럽하우스를 배경으로 한 미취학 아동용 애니메이션 시리즈이다."},
            {"After striking it rich on a large gold strike, a prospector retires to the quiet life on a Caribbean island but ends up coping with an alcoholic wife, a headstrong daughter, and Miami mobsters who want to build a casino.",
                    "대규모 금광 채굴로 일확천금을 얻은 한 광부가 카리브해의 한 섬에서 조용한 삶을 살기 위해 은퇴하지만, 알코올 중독에 걸린 아내, 고집 센 딸, 그리고 카지노를 건설하려는 마이애미 폭력배들을 상대해야 하는 처지에 놓인다."},
            {"Kemuning studied Masters in Türkiye. However, Ning remained steadfast in waiting faithfully for his true love, Raditya, who promised to propose to her one day. Even though in order to fulfill his promise, Ning rejects the love of Dewo, who turns out to be friends with Raditya. Raditya visits Dewo to tell him that he will marry the girl his mother chose, and must leave Ning. This news devastated Ning.",
                    "케무닝은 튀르키예에서 석사 과정을 공부했다. 하지만 닝은 언젠가 자신에게 청혼하겠다고 약속한 진정한 사랑 라디티야를 굳건히 기다렸다. 그 약속을 지키기 위해 닝은 라디티야의 친구인 데워의 사랑을 거절하기까지 한다. 라디티야는 데워를 찾아가 어머니가 골라준 여성과 결혼할 것이며 닝을 떠나야 한다고 말한다. 이 소식에 닝은 큰 충격을 받는다."},
            {"Comedian Franco Escamilla shares stories about parenting his children when they get into trouble, with reflections on gender, friendship and romance.",
                    "코미디언 프랑코 에스카미야가 아이들이 말썽을 피울 때 겪은 육아 이야기와 함께 성별, 우정, 로맨스에 대한 단상을 전한다."},
            {"In 1775 during the Spanish colonial period in the Philippines, nuns attempts to survive the night after an unknown evil was set free in their cloister. Meanwhile in 2025, a group of friends attend a Halloween masquerade ball which was attacked by a serial killer. Fast forward to the future in 2050, a post-apocalytic Philippines is ruled by the aswang.",
                    "스페인 식민지 시대인 1775년 필리핀, 수녀원의 수도원 안에서 정체불명의 악령이 풀려난 후 수녀들은 하룻밤 동안 살아남으려고 애쓴다. 한편 2025년, 한 무리의 친구들이 할로윈 가장무도회에 참석했다가 연쇄 살인마의 습격을 받는다. 시대를 훌쩍 뛰어넘어 2050년의 미래, 포스트 아포칼립스가 된 필리핀은 아스왕이 지배하고 있다."},
            {"A story of a mom who thought she knew everything about her daughter and a daughter who can't never understand her mom. Oh Eun-Soo (Lee Yul-Eum) is a university student. Her mother has divorced twice and Oh Eun-Soo learns that her mother will marry again. Oh Eun-Soo isn't happy about her mother's third marriage.",
                    "딸에 대해 모든 것을 알고 있다고 생각하는 엄마와 엄마를 도저히 이해할 수 없는 딸의 이야기이다. 오은수(이열음)는 대학생이다. 그녀의 엄마는 두 번 이혼했으며, 오은수는 엄마가 다시 결혼할 것이라는 사실을 알게 된다. 오은수는 엄마의 세 번째 결혼이 달갑지 않다."},
    };

    @Test
    void 실제_번역문_10개는_3번만_깨진_띄어쓰기_경고이고_나머지는_경고도_실패도_없다() {
        // given, when, then
        for (int i = 0; i < SAMPLES.length; i++) {
            GuardResult result = guard.check(SAMPLES[i][0], SAMPLES[i][1]);
            int number = i + 1;
            assertThat(result.failures()).as("샘플 %d 실패", number).isEmpty();
            if (number == 3) {
                assertThat(result.warnings()).as("샘플 3 경고").containsExactly(GuardWarning.SUSPICIOUS_SPACING);
            } else {
                assertThat(result.warnings()).as("샘플 %d 경고", number).isEmpty();
            }
        }
    }
}
