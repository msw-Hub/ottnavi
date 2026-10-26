package com.ottnavi.collect.step.detail;

import com.ottnavi.catalog.service.TitleDetailCollectService;
import com.ottnavi.catalog.service.model.TitleDetail;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/** 영화·드라마 상세 Step의 Writer: chunk 단위로 title 갱신, 장르·출연진·시청 단위를 저장한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DetailWriter implements ItemWriter<TitleDetail> {

    private final TitleDetailCollectService titleDetailCollectService;

    @Override
    public void write(Chunk<? extends TitleDetail> chunk) {
        List<TitleDetail> details = List.copyOf(chunk.getItems());
        int saved = titleDetailCollectService.saveDetails(details, Instant.now());
        log.debug("상세 저장: 받은 {}건 중 저장 {}건", details.size(), saved);
    }
}
