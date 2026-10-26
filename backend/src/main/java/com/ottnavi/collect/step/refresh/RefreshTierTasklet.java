package com.ottnavi.collect.step.refresh;

import com.ottnavi.catalog.service.TitleRefreshTierService;
import com.ottnavi.catalog.service.model.RefreshTierRealignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/** 수집 첫 Step(scope 없는 실행): 찜 현황에 맞춰 작품의 refresh_tier를 벌크 쿼리로 한 번에 재맞춤한다(Task 077) */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTierTasklet implements Tasklet {

    private final TitleRefreshTierService titleRefreshTierService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 1. 찜 행이 있는 작품은 DAILY, 없는 작품은 WEEKLY로 맞춘다
        RefreshTierRealignment result = titleRefreshTierService.realignByWishlist();

        // 2. 바뀐 건수를 남긴다
        contribution.incrementWriteCount(result.promotedToDaily() + result.demotedToWeekly());
        log.info("갱신 주기 재맞춤: DAILY로 올린 작품 {}건, WEEKLY로 되돌린 작품 {}건",
                result.promotedToDaily(), result.demotedToWeekly());
        return RepeatStatus.FINISHED;
    }
}
