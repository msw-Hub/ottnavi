package com.ottnavi.collect.step.candidate;

import com.ottnavi.catalog.service.TitleCollectService;
import com.ottnavi.catalog.service.model.CandidateTitle;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/** Step 2·3의 Writer: chunk(한 쪽) 단위로 새 후보 작품만 title에 저장한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateWriter implements ItemWriter<CandidateTitle> {

    private final TitleCollectService titleCollectService;

    @Override
    public void write(Chunk<? extends CandidateTitle> chunk) {
        List<CandidateTitle> candidates = List.copyOf(chunk.getItems());
        int saved = titleCollectService.saveCandidates(candidates, Instant.now());
        log.debug("후보 저장: 받은 {}건 중 새 작품 {}건(나머지는 요약 값 갱신)", candidates.size(), saved);
    }
}
