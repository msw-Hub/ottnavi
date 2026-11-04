package com.ottnavi.collect.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.collect.job.CollectProgressRegistry.LiveStepProgress;
import com.ottnavi.collect.job.CollectProgressRules.Progress;
import com.ottnavi.collect.job.CollectProgressRules.StepProgress;
import com.ottnavi.collect.job.CollectProgressRules.StepRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

/** 수집 실행의 단계별 진행 계산(퍼센트, 단계 목록, 단계 상태). Spring 컨텍스트 없이 시험한다 */
class CollectProgressRulesTest {

    // 퍼센트

    @Test
    void 퍼센트는_버림이라_199_200은_99다() {
        // when
        Integer percent = CollectProgressRules.percentOf(199, 200);

        // then
        assertThat(percent).isEqualTo(99);
    }

    @Test
    void 처리_수가_전체와_같으면_100이다() {
        // when
        Integer percent = CollectProgressRules.percentOf(200, 200);

        // then
        assertThat(percent).isEqualTo(100);
    }

    @Test
    void 처리_수가_전체보다_크면_100에서_멈춘다() {
        // when
        Integer percent = CollectProgressRules.percentOf(210, 200);

        // then
        assertThat(percent).isEqualTo(100);
    }

    @Test
    void 전체가_0건이면_할_일이_없어_100이다() {
        // when
        Integer percent = CollectProgressRules.percentOf(0, 0);

        // then
        assertThat(percent).isEqualTo(100);
    }

    @Test
    void 전체를_모르면_퍼센트도_null이다() {
        // when
        Integer percent = CollectProgressRules.percentOf(50, null);

        // then
        assertThat(percent).isNull();
    }

    @Test
    void 후보_퍼센트는_끝낸_서비스와_지금_서비스의_쪽_진행을_더한다() {
        // given: 7곳 중 2곳 끝, 지금 서비스 100쪽 중 50쪽 → (2 + 0.5) / 7 = 35.7%

        // when
        Integer percent = CollectProgressRules.candidatePercentOf(2, 7, 50, 100);

        // then
        assertThat(percent).isEqualTo(35);
    }

    @Test
    void 후보_퍼센트는_마지막_쪽을_모르면_끝낸_서비스_몫만_센다() {
        // when
        Integer percent = CollectProgressRules.candidatePercentOf(2, 7, 0, null);

        // then
        assertThat(percent).isEqualTo(28);
    }

    // 단계 목록

    @Test
    void DAILY_실행은_후보_발견을_건너뛰어_3단계다() {
        // when
        List<CollectStepCode> steps = CollectProgressRules.plannedSteps(CollectTier.DAILY, null);

        // then
        assertThat(steps).containsExactly(CollectStepCode.MOVIE_DETAIL, CollectStepCode.TV_DETAIL,
                CollectStepCode.AVAILABILITY);
    }

    @Test
    void WEEKLY_실행은_후보_발견부터_5단계다() {
        // when
        List<CollectStepCode> steps = CollectProgressRules.plannedSteps(CollectTier.WEEKLY, null);

        // then
        assertThat(steps).containsExactly(CollectStepCode.MOVIE_CANDIDATE, CollectStepCode.TV_CANDIDATE,
                CollectStepCode.MOVIE_DETAIL, CollectStepCode.TV_DETAIL, CollectStepCode.AVAILABILITY);
    }

    @Test
    void 시즌_판정만_하는_실행은_판정_1단계다() {
        // when
        List<CollectStepCode> steps = CollectProgressRules.plannedSteps(CollectTier.ALL, CollectScope.AVAILABILITY_TV);

        // then
        assertThat(steps).containsExactly(CollectStepCode.AVAILABILITY);
    }

    // 단계 상태

    @Test
    void 재시작_전_실행이_끝내지_못한_단계는_이_실행에서는_대기다() {
        // given
        StepRecord record = new StepRecord(CollectStepCode.TV_DETAIL, BatchStatus.FAILED, 30, false);

        // when
        CollectStepStatus status = CollectProgressRules.stepStatusOf(record, CollectExecutionStatus.RUNNING);

        // then
        assertThat(status).isEqualTo(CollectStepStatus.PENDING);
    }

    @Test
    void 실행이_중단됨이면_진행_중으로_남은_단계는_STOPPED다() {
        // given
        StepRecord record = new StepRecord(CollectStepCode.MOVIE_DETAIL, BatchStatus.STARTED, 300, true);

        // when
        CollectStepStatus status = CollectProgressRules.stepStatusOf(record, CollectExecutionStatus.STOPPED);

        // then
        assertThat(status).isEqualTo(CollectStepStatus.STOPPED);
    }

    // 실행 전체 진행

    @Test
    void 완료된_DAILY_실행은_3단계가_모두_완료이고_읽은_수가_전체다() {
        // given
        Map<CollectStepCode, StepRecord> records = new HashMap<>();
        records.put(CollectStepCode.MOVIE_DETAIL, new StepRecord(CollectStepCode.MOVIE_DETAIL, BatchStatus.COMPLETED, 40, true));
        records.put(CollectStepCode.TV_DETAIL, new StepRecord(CollectStepCode.TV_DETAIL, BatchStatus.COMPLETED, 25, true));
        records.put(CollectStepCode.AVAILABILITY, new StepRecord(CollectStepCode.AVAILABILITY, BatchStatus.COMPLETED, 90, true));

        // when
        Progress progress = CollectProgressRules.progressOf(CollectTier.DAILY, null, CollectExecutionStatus.COMPLETED,
                records, new HashMap<>());

        // then
        assertThat(progress.totalStepCount()).isEqualTo(3);
        assertThat(progress.completedStepCount()).isEqualTo(3);
        assertThat(progress.currentStep()).isNull();
        StepProgress movie = progress.steps().get(0);
        assertThat(movie.processedCount()).isEqualTo(40);
        assertThat(movie.totalCount()).isEqualTo(40);
        assertThat(movie.percent()).isEqualTo(100);
    }

    @Test
    void 실행_중인_상세_단계는_실시간_값으로_퍼센트를_내고_지금_단계가_된다() {
        // given: 후보 두 단계가 끝났고 영화 상세가 2400건 중 600건째
        Map<CollectStepCode, StepRecord> records = new HashMap<>();
        records.put(CollectStepCode.MOVIE_CANDIDATE, new StepRecord(CollectStepCode.MOVIE_CANDIDATE, BatchStatus.COMPLETED, 900, true));
        records.put(CollectStepCode.TV_CANDIDATE, new StepRecord(CollectStepCode.TV_CANDIDATE, BatchStatus.COMPLETED, 500, true));
        records.put(CollectStepCode.MOVIE_DETAIL, new StepRecord(CollectStepCode.MOVIE_DETAIL, BatchStatus.STARTED, 590, true));
        Map<CollectStepCode, LiveStepProgress> live = new HashMap<>();
        live.put(CollectStepCode.MOVIE_DETAIL, new LiveStepProgress(600, 2400, null, null, null, null));

        // when
        Progress progress = CollectProgressRules.progressOf(CollectTier.WEEKLY, null, CollectExecutionStatus.RUNNING,
                records, live);

        // then
        assertThat(progress.completedStepCount()).isEqualTo(2);
        assertThat(progress.currentStep()).isEqualTo(CollectStepCode.MOVIE_DETAIL);
        StepProgress movieDetail = progress.steps().get(2);
        assertThat(movieDetail.processedCount()).isEqualTo(600);
        assertThat(movieDetail.totalCount()).isEqualTo(2400);
        assertThat(movieDetail.percent()).isEqualTo(25);
    }

    @Test
    void 실패한_단계는_전체와_퍼센트를_모른다() {
        // given
        Map<CollectStepCode, StepRecord> records = new HashMap<>();
        records.put(CollectStepCode.MOVIE_DETAIL, new StepRecord(CollectStepCode.MOVIE_DETAIL, BatchStatus.FAILED, 120, true));

        // when
        Progress progress = CollectProgressRules.progressOf(CollectTier.DAILY, null, CollectExecutionStatus.FAILED,
                records, new HashMap<>());

        // then
        StepProgress movieDetail = progress.steps().get(0);
        assertThat(movieDetail.status()).isEqualTo(CollectStepStatus.FAILED);
        assertThat(movieDetail.processedCount()).isEqualTo(120);
        assertThat(movieDetail.totalCount()).isNull();
        assertThat(movieDetail.percent()).isNull();
    }
}
