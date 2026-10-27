package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.enums.ArrivalType;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.repository.AvailabilityChangeRepository;
import com.ottnavi.catalog.domain.rule.NewArrivalCandidate;
import com.ottnavi.catalog.domain.rule.NewArrivalPicker;
import com.ottnavi.catalog.service.model.NewlyAvailableItemView;
import com.ottnavi.catalog.service.model.NewlyAvailableResult;
import com.ottnavi.catalog.service.model.TitleCards;
import com.ottnavi.catalog.service.model.TitleSearchItemView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새로 볼 수 있게 된 작품(Task 107, FR-22). 두 경우를 묶는다.
 * 1. 입고(ARRIVAL): 최근 arrivalDays일 안에 제공 상태가 없음·모름에서 있음으로 바뀐 기록(첫 적재는 입고가 아니다).
 * 2. 공개(RELEASE): 첫 적재(이전 상태 없음)로 바로 있음이 됐지만 공개일이 최근 releaseDays일 이내인 작품. 공개일을 모르면 뺀다.
 * 같은 작품·서비스는 가장 최근 한 건만 남긴다. 날짜는 우리가 확인한 범위의 날짜이지 실제 서비스 입고일이 아니다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NewlyAvailableQueryService {

    private final AvailabilityChangeRepository availabilityChangeRepository;
    private final TitleCardAssembler titleCardAssembler;
    private final Clock clock;

    /**
     * 새로 볼 수 있게 된 작품을 최대 limit건 돌려준다. 작품 개봉일이 최근 6개월 안인 작품이 먼저이고 각 그룹 안은 날짜 최신순이다.
     *
     * @param ottServiceId 이 서비스만 보려면 서비스 ID, 전체면 null
     * @param arrivalDays  입고로 보는 최근 일수(오늘 포함 앞쪽으로 이 일수 전 0시부터)
     * @param releaseDays  공개로 보는 최근 일수(공개일이 오늘-releaseDays일 ~ 오늘)
     */
    public NewlyAvailableResult find(Long ottServiceId, int arrivalDays, int releaseDays, int limit) {
        // 1. 기준 날짜와 입고 기준 시각(KST 0시)을 정한다
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Instant arrivalSince = today.minusDays(arrivalDays).atStartOfDay(zone).toInstant();

        // 2. 입고·공개 후보 기록을 각각 한 번의 쿼리로 읽는다
        List<AvailabilityChange> arrivals = availabilityChangeRepository.findArrivalsSince(arrivalSince);
        List<AvailabilityChange> releases = availabilityChangeRepository
                .findFirstLoadsReleasedBetween(today.minusDays(releaseDays), today);

        // 3. 서비스 필터를 적용해 후보로 바꾼다(작품은 다시 읽지 않으려고 맵에 모아 둔다)
        Map<Long, Title> titlesById = new HashMap<>();
        List<NewArrivalCandidate> candidates = new ArrayList<>();
        for (AvailabilityChange change : arrivals) {
            if (!matchesService(change, ottServiceId)) {
                continue;
            }
            Title title = change.getWatchUnit().getTitle();
            titlesById.put(title.getId(), title);
            candidates.add(new NewArrivalCandidate(title.getId(), change.getOttService().getId(), ArrivalType.ARRIVAL,
                    change.getChangedAt().atZone(zone).toLocalDate(), title.getReleaseDate()));
        }
        for (AvailabilityChange change : releases) {
            if (!matchesService(change, ottServiceId)) {
                continue;
            }
            Title title = change.getWatchUnit().getTitle();
            titlesById.put(title.getId(), title);
            candidates.add(new NewArrivalCandidate(title.getId(), change.getOttService().getId(), ArrivalType.RELEASE,
                    releaseDateOf(change), title.getReleaseDate()));
        }

        // 4. 같은 작품·서비스는 대표 한 건만 남기고 작품 개봉일 그룹·날짜 최신순으로 정렬해 자른다
        List<NewArrivalCandidate> picked = NewArrivalPicker.pick(candidates, limit, today);

        // 5. 고른 작품들(중복 없이 등장 순서대로)을 카드로 만든다
        Map<Long, Title> orderedTitles = new LinkedHashMap<>();
        for (NewArrivalCandidate candidate : picked) {
            orderedTitles.put(candidate.titleId(), titlesById.get(candidate.titleId()));
        }
        TitleCards cards = titleCardAssembler.assemble(new ArrayList<>(orderedTitles.values()));
        Map<Long, TitleSearchItemView> cardByTitleId = new HashMap<>();
        int index = 0;
        for (Long titleId : orderedTitles.keySet()) {
            cardByTitleId.put(titleId, cards.items().get(index));
            index++;
        }

        // 6. 후보마다 응답 한 줄을 만든다
        List<NewlyAvailableItemView> items = new ArrayList<>();
        for (NewArrivalCandidate candidate : picked) {
            items.add(new NewlyAvailableItemView(cardByTitleId.get(candidate.titleId()), candidate.ottServiceId(),
                    candidate.type(), candidate.date()));
        }
        return new NewlyAvailableResult(items, cards.dataAsOf());
    }

    private boolean matchesService(AvailabilityChange change, Long ottServiceId) {
        return ottServiceId == null || ottServiceId.equals(change.getOttService().getId());
    }

    /** 공개일: 영화는 작품의 개봉일, 드라마는 시즌(시청 단위)의 방영일. 쿼리가 null을 이미 걸러냈다 */
    private LocalDate releaseDateOf(AvailabilityChange change) {
        Title title = change.getWatchUnit().getTitle();
        if (title.getMediaType() == MediaType.MOVIE) {
            return title.getReleaseDate();
        }
        return change.getWatchUnit().getAirDate();
    }
}
