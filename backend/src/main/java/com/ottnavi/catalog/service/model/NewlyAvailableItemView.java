package com.ottnavi.catalog.service.model;

import com.ottnavi.catalog.domain.enums.ArrivalType;
import java.time.LocalDate;

/** 새로 볼 수 있게 된 작품 한 줄 */
public record NewlyAvailableItemView(
        TitleSearchItemView title,
        long ottServiceId, // 새로 볼 수 있게 된 서비스
        ArrivalType arrivalType,
        LocalDate availableSince) { // ARRIVAL이면 있음으로 바뀐 것을 확인한 날, RELEASE면 공개일
}
