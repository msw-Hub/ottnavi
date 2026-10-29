package com.ottnavi.llmdraft.service;

import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.llmdraft.repository.DraftRepository;
import com.ottnavi.llmdraft.service.model.BulkRunStatus;
import com.ottnavi.llmdraft.service.model.DraftGrade;
import com.ottnavi.llmdraft.service.model.DraftPageView;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import com.ottnavi.llmdraft.service.model.DraftStatusFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 번역 초안 목록 조회. 초안은 Redis에만 있어 트랜잭션이 필요 없다.
 * Redis에서 사라진 초안은 목록에서 조용히 빠진다(오류가 아니다).
 */
@Service
@RequiredArgsConstructor
public class DraftQueryService {

    private final DraftRepository draftRepository;
    private final DraftBulkService draftBulkService;

    private final Random random = new Random();

    /**
     * 초안 목록을 만든 시각 최신순으로 한 페이지 돌려준다.
     * status는 등급으로 거른다: PASSED는 성공(실패도 경고도 없음)만, WARNING은 주의(경고만 있음)만, FAILED는 실패만.
     * sample이 있으면 성공(PASSED) 초안 중 무작위 sample개를 돌려준다(표본 확인용, 페이지는 쓰지 않는다).
     * 표본은 성공한 초안만 뽑으므로 status가 FAILED·WARNING이면 VALIDATION_FAILED다.
     */
    public DraftPageView list(DraftStatusFilter status, Integer sample, int page, int size) {
        // 1. 표본과 FAILED·WARNING 필터는 함께 쓸 수 없다
        if (sample != null && (status == DraftStatusFilter.FAILED || status == DraftStatusFilter.WARNING)) {
            throw new FieldValidationException("sample", "표본은 성공한 초안에서만 뽑을 수 있어 status=FAILED·WARNING과 함께 쓸 수 없습니다.");
        }

        // 2. 필터에 맞는 초안만 모은다
        List<DraftRecord> filtered = new ArrayList<>();
        for (DraftRecord draft : draftRepository.findAll()) {
            if (matches(draft, status, sample)) {
                filtered.add(draft);
            }
        }
        BulkRunStatus bulkRun = null;
        Optional<BulkRunStatus> latest = draftBulkService.findLatestStatus();
        if (latest.isPresent()) {
            bulkRun = latest.get();
        }

        // 3. 표본이면 무작위로 섞어 앞에서 sample개를 자른다
        if (sample != null) {
            Collections.shuffle(filtered, random);
            List<DraftRecord> picked = new ArrayList<>(filtered.subList(0, Math.min(sample, filtered.size())));
            int totalPages = 1;
            if (picked.isEmpty()) {
                totalPages = 0;
            }
            return new DraftPageView(picked, 0, sample, picked.size(), totalPages, bulkRun);
        }

        // 4. 일반 목록은 페이지로 자른다
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        int totalPages = (filtered.size() + size - 1) / size;
        return new DraftPageView(new ArrayList<>(filtered.subList(from, to)), page, size, filtered.size(), totalPages, bulkRun);
    }

    private boolean matches(DraftRecord draft, DraftStatusFilter status, Integer sample) {
        if (sample != null || status == DraftStatusFilter.PASSED) {
            return draft.grade() == DraftGrade.PASSED;
        }
        if (status == DraftStatusFilter.WARNING) {
            return draft.grade() == DraftGrade.WARNING;
        }
        if (status == DraftStatusFilter.FAILED) {
            return draft.grade() == DraftGrade.FAILED;
        }
        return true;
    }
}
