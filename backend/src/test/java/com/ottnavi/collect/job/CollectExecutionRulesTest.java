package com.ottnavi.collect.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.collect.job.CollectExecutionRules.DateVerdict;
import com.ottnavi.collect.job.CollectExecutionRules.LaunchVerdict;
import com.ottnavi.collect.job.CollectExecutionRules.ScopeVerdict;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

/** 수집 실행 상태 판정과 시작·재시작·버튼 규칙(Task 045 결정 1·2·6·7·12). Spring 컨텍스트 없이 시험한다 */
class CollectExecutionRulesTest {

    private static final LocalDateTime BOOTED_AT = LocalDateTime.of(2026, 10, 15, 9, 0, 0);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    // 상태 판정

    @Test
    void 완료된_Batch_실행은_COMPLETED다() {
        // given
        CollectExecutionRecord record = record(BatchStatus.COMPLETED, "COMPLETED", null, BOOTED_AT.minusDays(1));

        // when
        CollectExecutionStatus status = CollectExecutionRules.statusOf(record, BOOTED_AT);

        // then
        assertThat(status).isEqualTo(CollectExecutionStatus.COMPLETED);
    }

    @Test
    void 실행_중_기록이고_기동_시각_이후에_만들어졌으면_RUNNING이다() {
        // given
        BatchStatus[] runningStatuses = {BatchStatus.STARTING, BatchStatus.STARTED, BatchStatus.STOPPING};

        for (BatchStatus batchStatus : runningStatuses) {
            // when
            CollectExecutionRecord record = record(batchStatus, "EXECUTING", null, BOOTED_AT.plusMinutes(1));
            CollectExecutionStatus status = CollectExecutionRules.statusOf(record, BOOTED_AT);

            // then
            assertThat(status).as(batchStatus.name()).isEqualTo(CollectExecutionStatus.RUNNING);
        }
    }

    @Test
    void 실행_중_기록인데_기동_시각보다_먼저_만들어졌으면_STOPPED다() {
        // given
        BatchStatus[] runningStatuses = {BatchStatus.STARTING, BatchStatus.STARTED, BatchStatus.STOPPING};

        for (BatchStatus batchStatus : runningStatuses) {
            // when
            CollectExecutionRecord record = record(batchStatus, "EXECUTING", null, BOOTED_AT.minusMinutes(1));
            CollectExecutionStatus status = CollectExecutionRules.statusOf(record, BOOTED_AT);

            // then
            assertThat(status).as(batchStatus.name()).isEqualTo(CollectExecutionStatus.STOPPED);
        }
    }

    @Test
    void 실패_계열_Batch_상태는_모두_FAILED다() {
        // given
        BatchStatus[] failedStatuses = {BatchStatus.FAILED, BatchStatus.STOPPED, BatchStatus.ABANDONED,
                BatchStatus.UNKNOWN};

        for (BatchStatus batchStatus : failedStatuses) {
            // when
            CollectExecutionRecord record = record(batchStatus, "FAILED", null, BOOTED_AT.plusMinutes(1));
            CollectExecutionStatus status = CollectExecutionRules.statusOf(record, BOOTED_AT);

            // then
            assertThat(status).as(batchStatus.name()).isEqualTo(CollectExecutionStatus.FAILED);
        }
    }

    @Test
    void 종료_코드가_RECOVERED인_실패는_FAILED이고_종료_시각은_null이다() {
        // given
        CollectExecutionRecord record = record(BatchStatus.FAILED, "RECOVERED", "무시되는 메시지",
                BOOTED_AT.minusDays(1));

        // when
        CollectExecutionStatus status = CollectExecutionRules.statusOf(record, BOOTED_AT);
        OffsetDateTime endedAt = CollectExecutionRules.endedAtOf(record, status);

        // then
        assertThat(status).isEqualTo(CollectExecutionStatus.FAILED);
        assertThat(endedAt).isNull();
    }

    @Test
    void 스스로_실패한_실행은_종료_시각이_있다() {
        // given
        CollectExecutionRecord record = record(BatchStatus.FAILED, "FAILED", "오류", BOOTED_AT.minusDays(1));

        // when
        OffsetDateTime endedAt = CollectExecutionRules.endedAtOf(record, CollectExecutionStatus.FAILED);

        // then
        assertThat(endedAt).isNotNull();
    }

    @Test
    void 시작_시각이_없으면_생성_시각을_시작_시각으로_쓴다() {
        // given
        LocalDateTime createTime = LocalDateTime.of(2026, 10, 14, 3, 0, 0);
        CollectExecutionRecord record = new CollectExecutionRecord(1, 1, TODAY, CollectTier.ALL, BatchStatus.FAILED,
                "FAILED", "오류", createTime, null, null, true);

        // when
        OffsetDateTime startedAt = CollectExecutionRules.startedAtOf(record);

        // then
        assertThat(startedAt).isEqualTo(CollectExecutionRules.toKstOffset(createTime));
    }

    @Test
    void 시각은_KST_오프셋으로_바뀐다() {
        // given
        LocalDateTime time = LocalDateTime.of(2026, 10, 15, 12, 0, 0);

        // when
        OffsetDateTime kst = CollectExecutionRules.toKstOffset(time);

        // then
        assertThat(kst.getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        assertThat(kst.toInstant()).isEqualTo(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    // 기준일 판정

    @Test
    void 운영_모드에서_오늘이_아니면_NOT_TODAY다() {
        // given
        LocalDate yesterday = TODAY.minusDays(1);
        LocalDate tomorrow = TODAY.plusDays(1);

        // when, then
        assertThat(CollectExecutionRules.judgeDate(yesterday, TODAY, false)).isEqualTo(DateVerdict.NOT_TODAY);
        assertThat(CollectExecutionRules.judgeDate(tomorrow, TODAY, false)).isEqualTo(DateVerdict.NOT_TODAY);
        assertThat(CollectExecutionRules.judgeDate(TODAY, TODAY, false)).isEqualTo(DateVerdict.OK);
    }

    @Test
    void 로컬_모드에서_미래_날짜는_FUTURE이고_과거는_OK다() {
        // when, then
        assertThat(CollectExecutionRules.judgeDate(TODAY.plusDays(1), TODAY, true)).isEqualTo(DateVerdict.FUTURE);
        assertThat(CollectExecutionRules.judgeDate(TODAY.minusDays(30), TODAY, true)).isEqualTo(DateVerdict.OK);
        assertThat(CollectExecutionRules.judgeDate(TODAY, TODAY, true)).isEqualTo(DateVerdict.OK);
    }

    // 실행 범위 판정

    @Test
    void scope가_없으면_운영에서도_OK이고_있으면_로컬에서만_OK다() {
        // when, then
        assertThat(CollectExecutionRules.judgeScope(null, false)).isEqualTo(ScopeVerdict.OK);
        assertThat(CollectExecutionRules.judgeScope(null, true)).isEqualTo(ScopeVerdict.OK);
        assertThat(CollectExecutionRules.judgeScope(CollectScope.AVAILABILITY_TV, true)).isEqualTo(ScopeVerdict.OK);
        assertThat(CollectExecutionRules.judgeScope(CollectScope.AVAILABILITY_TV, false))
                .isEqualTo(ScopeVerdict.NOT_ALLOWED);
    }

    // 시작 판정

    @Test
    void 같은_키가_실행_중이면_ALREADY_RUNNING이다() {
        // when
        LaunchVerdict verdict = CollectExecutionRules.judgeLaunch(CollectExecutionStatus.RUNNING, true);

        // then
        assertThat(verdict).isEqualTo(LaunchVerdict.ALREADY_RUNNING);
    }

    @Test
    void 같은_키가_완료면_다른_실행이_돌아도_ALREADY_COMPLETED다() {
        // when
        LaunchVerdict verdict = CollectExecutionRules.judgeLaunch(CollectExecutionStatus.COMPLETED, true);

        // then
        assertThat(verdict).isEqualTo(LaunchVerdict.ALREADY_COMPLETED);
    }

    @Test
    void 같은_키가_중단됨이면_RECOVERY_REQUIRED다() {
        // when
        LaunchVerdict verdict = CollectExecutionRules.judgeLaunch(CollectExecutionStatus.STOPPED, false);

        // then
        assertThat(verdict).isEqualTo(LaunchVerdict.RECOVERY_REQUIRED);
    }

    @Test
    void 같은_키가_실패이고_다른_날짜가_실행_중이면_OTHER_RUNNING이다() {
        // when
        LaunchVerdict verdict = CollectExecutionRules.judgeLaunch(CollectExecutionStatus.FAILED, true);

        // then
        assertThat(verdict).isEqualTo(LaunchVerdict.OTHER_RUNNING);
    }

    @Test
    void 같은_키_실행이_없으면_ALLOWED다() {
        // when, then
        assertThat(CollectExecutionRules.judgeLaunch(null, false)).isEqualTo(LaunchVerdict.ALLOWED);
        assertThat(CollectExecutionRules.judgeLaunch(null, true)).isEqualTo(LaunchVerdict.OTHER_RUNNING);
        assertThat(CollectExecutionRules.judgeLaunch(CollectExecutionStatus.FAILED, false))
                .isEqualTo(LaunchVerdict.ALLOWED);
    }

    // 버튼

    @Test
    void canRestart는_최신_실패에만_참이다() {
        // when
        boolean isAllowed = CollectExecutionRules.canRestart(CollectExecutionStatus.FAILED, BatchStatus.FAILED, true,
                DateVerdict.OK, false);

        // then
        assertThat(isAllowed).isTrue();
        assertThat(CollectExecutionRules.canRestart(CollectExecutionStatus.COMPLETED, BatchStatus.COMPLETED, true,
                DateVerdict.OK, false)).isFalse();
        assertThat(CollectExecutionRules.canRestart(CollectExecutionStatus.STOPPED, BatchStatus.STARTED, true,
                DateVerdict.OK, false)).isFalse();
    }

    @Test
    void 최신이_아닌_실패는_canRestart가_거짓이다() {
        // when
        boolean isAllowed = CollectExecutionRules.canRestart(CollectExecutionStatus.FAILED, BatchStatus.FAILED, false,
                DateVerdict.OK, false);

        // then
        assertThat(isAllowed).isFalse();
    }

    @Test
    void 운영에서_어제_실패는_canRestart가_거짓이다() {
        // given
        DateVerdict dateVerdict = CollectExecutionRules.judgeDate(TODAY.minusDays(1), TODAY, false);

        // when
        boolean isAllowed = CollectExecutionRules.canRestart(CollectExecutionStatus.FAILED, BatchStatus.FAILED, true,
                dateVerdict, false);

        // then
        assertThat(isAllowed).isFalse();
    }

    @Test
    void 다른_수집이_실행_중이면_canRestart가_거짓이다() {
        // when
        boolean isAllowed = CollectExecutionRules.canRestart(CollectExecutionStatus.FAILED, BatchStatus.FAILED, true,
                DateVerdict.OK, true);

        // then
        assertThat(isAllowed).isFalse();
    }

    @Test
    void ABANDONED는_canRestart가_거짓이다() {
        // when
        boolean isAllowed = CollectExecutionRules.canRestart(CollectExecutionStatus.FAILED, BatchStatus.ABANDONED,
                true, DateVerdict.OK, false);

        // then
        assertThat(isAllowed).isFalse();
    }

    @Test
    void canRecover는_STOPPED에만_참이다() {
        // when, then
        assertThat(CollectExecutionRules.canRecover(CollectExecutionStatus.STOPPED)).isTrue();
        assertThat(CollectExecutionRules.canRecover(CollectExecutionStatus.RUNNING)).isFalse();
        assertThat(CollectExecutionRules.canRecover(CollectExecutionStatus.FAILED)).isFalse();
        assertThat(CollectExecutionRules.canRecover(CollectExecutionStatus.COMPLETED)).isFalse();
    }

    // 실패 사유

    @Test
    void 실패_사유는_스택_첫_줄의_짧은_클래스명과_메시지다() {
        // given
        String exitMessage = "org.springframework.web.client.HttpServerErrorException: 500 서버 오류\n"
                + "\tat com.ottnavi.Foo.bar(Foo.java:10)";
        CollectExecutionRecord record = record(BatchStatus.FAILED, "FAILED", exitMessage, BOOTED_AT);

        // when
        String reason = CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.FAILED);

        // then
        assertThat(reason).isEqualTo("HttpServerErrorException: 500 서버 오류");
    }

    @Test
    void 실패_사유는_300자로_자른다() {
        // given
        String longMessage = "java.lang.IllegalStateException: " + "가".repeat(500);
        CollectExecutionRecord record = record(BatchStatus.FAILED, "FAILED", longMessage, BOOTED_AT);

        // when
        String reason = CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.FAILED);

        // then
        assertThat(reason).hasSize(300);
        assertThat(reason).startsWith("IllegalStateException: ");
    }

    @Test
    void 메시지_안의_점은_클래스명_줄이기에_쓰지_않는다() {
        // given
        String exitMessage = "java.lang.IllegalStateException: host api.themoviedb.org 응답 없음 v1.2.3";
        CollectExecutionRecord record = record(BatchStatus.FAILED, "FAILED", exitMessage, BOOTED_AT);

        // when
        String reason = CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.FAILED);

        // then
        assertThat(reason).isEqualTo("IllegalStateException: host api.themoviedb.org 응답 없음 v1.2.3");
    }

    @Test
    void 메시지가_비어_있으면_기본_문구를_쓴다() {
        // given
        CollectExecutionRecord record = record(BatchStatus.FAILED, "FAILED", "", BOOTED_AT);

        // when
        String reason = CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.FAILED);

        // then
        assertThat(reason).isEqualTo("실패 사유가 기록되지 않았습니다.");
    }

    @Test
    void 복구된_실행의_실패_사유는_고정_문구다() {
        // given
        CollectExecutionRecord record = record(BatchStatus.FAILED, "RECOVERED", "무시되는 메시지", BOOTED_AT);

        // when
        String reason = CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.FAILED);

        // then
        assertThat(reason).isEqualTo("중단된 실행을 관리자가 실패로 복구함");
    }

    @Test
    void 실패가_아니면_실패_사유는_null이다() {
        // given
        CollectExecutionRecord record = record(BatchStatus.COMPLETED, "COMPLETED", "무시", BOOTED_AT);

        // when
        String reason = CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.COMPLETED);

        // then
        assertThat(reason).isNull();
    }

    private static CollectExecutionRecord record(BatchStatus batchStatus, String exitCode, String exitMessage,
                                                 LocalDateTime createTime) {
        return new CollectExecutionRecord(1, 1, TODAY, CollectTier.ALL, batchStatus, exitCode, exitMessage,
                createTime, createTime, createTime.plusMinutes(5), true);
    }
}
