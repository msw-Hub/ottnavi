package com.ottnavi.collect.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.batch.core.BatchStatus;

/**
 * 수집 실행의 상태 판정과 시작·재시작·버튼 허용 규칙(Task 045 결정 1·2·6·7·12).
 * 시작 API, 재시작 API, 이력 응답의 canRecover·canRestart가 모두 이 클래스의 같은 판정을 쓰도록 한곳에 둔다.
 * 시간·DB·Spring 컨텍스트에 기대지 않는 순수 규칙이라 값만 넘겨 시험할 수 있다.
 */
public final class CollectExecutionRules {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int FAILURE_REASON_MAX_LENGTH = 300;
    private static final String FAILURE_REASON_EMPTY = "실패 사유가 기록되지 않았습니다.";

    /** 수집 기준일 판정 결과 */
    public enum DateVerdict {
        OK,
        NOT_TODAY, // 운영 모드에서 오늘(KST)이 아님
        FUTURE // 지정 허용 모드에서 오늘보다 뒤
    }

    /** 실행 범위(scope) 판정 결과 */
    public enum ScopeVerdict {
        OK,
        NOT_ALLOWED // 운영 모드에서 전체가 아닌 일부 범위를 요청함
    }

    /** 시작·재시작 가능 여부 판정 결과 */
    public enum LaunchVerdict {
        ALLOWED,
        ALREADY_RUNNING, // 같은 날짜·범위가 실행 중
        RECOVERY_REQUIRED, // 같은 날짜·범위가 중단됨 상태라 복구가 먼저 필요
        ALREADY_COMPLETED, // 같은 날짜·범위가 이미 완료
        OTHER_RUNNING // 다른 날짜·범위의 수집이 실행 중(전체 동시 실행 금지)
    }

    private CollectExecutionRules() {
    }

    /**
     * Batch 기록을 화면 상태로 바꾼다.
     * 실행 중 상태(STARTING·STARTED·STOPPING)인데 서버 기동 시각보다 먼저 만들어졌다면
     * 그 실행을 돌리던 서버가 이미 죽은 것이므로 STOPPED로 본다.
     */
    public static CollectExecutionStatus statusOf(CollectExecutionRecord record, LocalDateTime bootedAt) {
        BatchStatus batchStatus = record.batchStatus();
        if (batchStatus == BatchStatus.COMPLETED) {
            return CollectExecutionStatus.COMPLETED;
        }
        if (batchStatus == BatchStatus.STARTING || batchStatus == BatchStatus.STARTED
                || batchStatus == BatchStatus.STOPPING) {
            if (record.createTime().isBefore(bootedAt)) {
                return CollectExecutionStatus.STOPPED;
            }
            return CollectExecutionStatus.RUNNING;
        }
        // FAILED, Batch의 STOPPED, ABANDONED, UNKNOWN은 모두 실패로 보여 준다
        return CollectExecutionStatus.FAILED;
    }

    /** 수집 기준일이 허용되는지 판정한다. 운영은 오늘(KST)만, 지정 허용 모드는 미래만 막는다 */
    public static DateVerdict judgeDate(LocalDate targetDate, LocalDate today, boolean isOverrideAllowed) {
        if (!isOverrideAllowed) {
            if (!targetDate.equals(today)) {
                return DateVerdict.NOT_TODAY;
            }
            return DateVerdict.OK;
        }
        if (targetDate.isAfter(today)) {
            return DateVerdict.FUTURE;
        }
        return DateVerdict.OK;
    }

    /**
     * 실행 범위가 허용되는지 판정한다. 비어 있으면(전체) 항상 허용하고, 값이 있으면 지정 허용 모드(local)에서만 허용한다.
     * 날짜 직접 지정과 같은 스위치를 쓴다: 일부 Step만 돌리는 것은 로컬 재현·재판정용이기 때문이다.
     */
    public static ScopeVerdict judgeScope(CollectScope scope, boolean isOverrideAllowed) {
        if (scope == null || isOverrideAllowed) {
            return ScopeVerdict.OK;
        }
        return ScopeVerdict.NOT_ALLOWED;
    }

    /**
     * 시작·재시작 가능 여부를 판정한다. 같은 날짜·범위의 최신 상태를 먼저 보고, 그 뒤 다른 수집이 도는지 본다.
     *
     * @param latestOfSameKey 같은 targetDate+tier의 가장 최근 실행 상태. 실행 기록이 없으면 null
     */
    public static LaunchVerdict judgeLaunch(CollectExecutionStatus latestOfSameKey, boolean isAnotherRunning) {
        if (latestOfSameKey == CollectExecutionStatus.RUNNING) {
            return LaunchVerdict.ALREADY_RUNNING;
        }
        if (latestOfSameKey == CollectExecutionStatus.STOPPED) {
            return LaunchVerdict.RECOVERY_REQUIRED;
        }
        if (latestOfSameKey == CollectExecutionStatus.COMPLETED) {
            return LaunchVerdict.ALREADY_COMPLETED;
        }
        if (isAnotherRunning) {
            return LaunchVerdict.OTHER_RUNNING;
        }
        return LaunchVerdict.ALLOWED;
    }

    /** 복구 버튼은 중단됨으로 판정된 실행에만 켠다 */
    public static boolean canRecover(CollectExecutionStatus status) {
        return status == CollectExecutionStatus.STOPPED;
    }

    /**
     * 재시작 버튼은 같은 날짜·범위의 최신 실패에만, 날짜가 허용되고 다른 수집이 돌지 않을 때만 켠다.
     * 복구해 실패가 된 실행도 같은 규칙을 따른다(최신이면 켜진다).
     */
    public static boolean canRestart(CollectExecutionStatus status, BatchStatus batchStatus, boolean isLatest,
                                     DateVerdict dateVerdict, boolean isAnotherRunning) {
        if (status != CollectExecutionStatus.FAILED) {
            return false;
        }
        if (batchStatus == BatchStatus.ABANDONED) {
            return false;
        }
        if (!isLatest) {
            return false;
        }
        if (dateVerdict != DateVerdict.OK) {
            return false;
        }
        return judgeLaunch(CollectExecutionStatus.FAILED, isAnotherRunning) == LaunchVerdict.ALLOWED;
    }

    /**
     * 실패 사유 한 줄을 만든다. 실패가 아니면 null이다.
     * 예: "org.springframework.batch.core.JobExecutionException: TMDB 500" → "JobExecutionException: TMDB 500"
     * 클래스 이름 부분만 줄이고 메시지 안의 점은 건드리지 않는다. 300자를 넘으면 자른다.
     */
    public static String failureReasonOf(CollectExecutionRecord record, CollectExecutionStatus status) {
        // 1. 실패가 아니면 사유가 없다
        if (status != CollectExecutionStatus.FAILED) {
            return null;
        }

        // 2. 관리자가 복구한 실행은 고정 문구
        if (CollectJobLauncher.RECOVERED_EXIT_CODE.equals(record.exitCode())) {
            return CollectJobLauncher.RECOVERED_REASON;
        }

        // 3. 스택의 첫 줄을 고른다
        String firstLine = firstLineOf(record.exitMessage());
        if (firstLine.isEmpty()) {
            return FAILURE_REASON_EMPTY;
        }

        // 4. "클래스: 메시지"에서 클래스 이름만 짧게 줄인다
        String reason = shortenClassName(firstLine);

        // 5. 길이를 자른다
        if (reason.length() > FAILURE_REASON_MAX_LENGTH) {
            return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
        }
        return reason;
    }

    /** Batch의 시각(JVM 기본 시간대)을 KST 오프셋 시각으로 바꾼다. null이면 null이다 */
    public static OffsetDateTime toKstOffset(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toOffsetDateTime();
    }

    /** 시작 시각. 시작 전에 실패한 실행은 START_TIME이 비어 있어 생성 시각을 쓴다 */
    public static OffsetDateTime startedAtOf(CollectExecutionRecord record) {
        if (record.startTime() == null) {
            return toKstOffset(record.createTime());
        }
        return toKstOffset(record.startTime());
    }

    /**
     * 종료 시각. 완료되었거나 스스로 실패한 실행만 값이 있다.
     * 복구된 실행의 END_TIME은 복구를 누른 시각이라 실제 종료가 아니므로 null이다.
     */
    public static OffsetDateTime endedAtOf(CollectExecutionRecord record, CollectExecutionStatus status) {
        if (status == CollectExecutionStatus.COMPLETED) {
            return toKstOffset(record.endTime());
        }
        if (status == CollectExecutionStatus.FAILED
                && !CollectJobLauncher.RECOVERED_EXIT_CODE.equals(record.exitCode())) {
            return toKstOffset(record.endTime());
        }
        return null;
    }

    private static String firstLineOf(String message) {
        if (message == null) {
            return "";
        }
        String[] lines = message.split("\\R");
        for (String line : lines) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private static String shortenClassName(String line) {
        int colon = line.indexOf(':');
        String head = line;
        String tail = "";
        if (colon >= 0) {
            head = line.substring(0, colon);
            tail = line.substring(colon);
        }
        // 공백이 있으면 클래스 이름이 아니라 문장이므로 그대로 둔다
        for (int i = 0; i < head.length(); i++) {
            if (Character.isWhitespace(head.charAt(i))) {
                return line;
            }
        }
        int lastDot = head.lastIndexOf('.');
        if (lastDot >= 0) {
            head = head.substring(lastDot + 1);
        }
        return head + tail;
    }
}
