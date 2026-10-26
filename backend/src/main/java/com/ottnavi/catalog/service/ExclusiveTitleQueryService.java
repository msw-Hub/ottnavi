package com.ottnavi.catalog.service;

import com.ottnavi.catalog.service.model.ExclusiveTitleItem;
import com.ottnavi.catalog.service.model.ExclusiveTitleList;
import com.ottnavi.catalog.service.model.ExclusiveTitlePageView;
import com.ottnavi.provider.exception.OttServiceNotFoundException;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 서비스별 독점 목록 공개 조회(Task 078). 캐시된 목록을 페이지로 잘라 돌려준다 */
@Service
@RequiredArgsConstructor
public class ExclusiveTitleQueryService {

    private final OttServiceQueryService ottServiceQueryService;
    private final ExclusiveTitleCacheService cacheService;

    /**
     * 서비스 한 곳의 독점 목록 한 페이지를 돌려준다. 없는 서비스면 OttServiceNotFoundException(404).
     * 데이터 부족 서비스(쿠팡플레이)도 같은 규칙으로 판정한 목록(영화 제외, 드라마 시즌만)을 돌려준다.
     */
    public ExclusiveTitlePageView getPage(long ottServiceId, int page, int size) {
        // 1. 서비스가 있는지 확인한다(없으면 404)
        if (ottServiceQueryService.findById(ottServiceId).isEmpty()) {
            throw new OttServiceNotFoundException("서비스를 찾을 수 없습니다: " + ottServiceId);
        }

        // 2. 캐시된 전체 목록을 읽는다(비어 있으면 즉석 계산)
        ExclusiveTitleList list = cacheService.get(ottServiceId);

        // 3. 요청한 페이지만 잘라 낸다(범위를 벗어난 페이지는 빈 목록)
        List<ExclusiveTitleItem> all = list.items();
        long total = all.size();
        int totalPages = (int) ((total + size - 1) / size);
        List<ExclusiveTitleItem> content = new ArrayList<>();
        long from = (long) page * size;
        for (long i = from; i < total && i < from + size; i++) {
            content.add(all.get((int) i));
        }
        return new ExclusiveTitlePageView(ottServiceId, list.judged(), list.dataAsOf(), content, page, size, total,
                totalPages);
    }
}
