package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.repository.AvailabilityChangeRepository;
import com.ottnavi.catalog.service.model.ExecutionChange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 제공 상태 변경 이력(availability_change) 조회. 관리자 수집 이력이 실행별 변경 건수를 볼 때 쓴다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AvailabilityChangeQueryService {

    private final AvailabilityChangeRepository availabilityChangeRepository;

    /**
     * 수집 실행 ID별 변경 건수를 센다. 변경이 없는 실행은 결과에 없다(호출하는 쪽이 0으로 본다).
     * 예: [5, 6] → {5=2} (6번 실행은 변경 없음)
     */
    public Map<Long, Integer> countByJobExecutionIds(List<Long> jobExecutionIds) {
        // 1. 빈 목록이면 쿼리하지 않는다(in () 절을 피한다)
        Map<Long, Integer> counts = new HashMap<>();
        if (jobExecutionIds.isEmpty()) {
            return counts;
        }

        // 2. 실행 ID별로 묶어 센다
        List<Object[]> rows = availabilityChangeRepository.countGroupedByJobExecutionId(jobExecutionIds);

        // 3. 행을 Map으로 푼다
        for (Object[] row : rows) {
            Long jobExecutionId = (Long) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(jobExecutionId, (int) count);
        }
        return counts;
    }

    /**
     * 한 수집 실행에서 바뀐 제공 상태를 제목·시즌 번호·서비스 ID 순으로 한 페이지 돌려준다.
     * 엔티티를 값 묶음으로 바꿔 돌려주므로 호출하는 쪽은 도메인 엔티티를 몰라도 된다.
     */
    public Page<ExecutionChange> findByJobExecutionId(long jobExecutionId, int page, int size) {
        // 1. 페이지 조회(작품·시청 단위·서비스를 함께 읽어 변환 중 추가 쿼리가 없다)
        Page<AvailabilityChange> found = availabilityChangeRepository.findPageByJobExecutionId(
                jobExecutionId, PageRequest.of(page, size));

        // 2. 엔티티를 값 묶음으로 바꾼다
        List<ExecutionChange> changes = new ArrayList<>();
        for (AvailabilityChange change : found.getContent()) {
            WatchUnit unit = change.getWatchUnit();
            Title title = unit.getTitle();
            changes.add(new ExecutionChange(
                    title.getMediaType(),
                    title.getTmdbId(),
                    title.getTitle(),
                    unit.getSeasonNumber(),
                    change.getOttService().getId(),
                    change.getBeforeStatus(),
                    change.getAfterStatus()));
        }

        // 3. 전체 건수는 저장소 결과 그대로 둔다
        return new PageImpl<>(changes, found.getPageable(), found.getTotalElements());
    }
}
