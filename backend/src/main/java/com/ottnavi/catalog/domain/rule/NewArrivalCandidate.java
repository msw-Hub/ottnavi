package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.ArrivalType;
import java.time.LocalDate;

/** "새로 볼 수 있게 된 작품" 후보 한 건: 작품이 서비스에서 어떤 이유로 어느 날짜 기준으로 새로 보이는지 */
public record NewArrivalCandidate(
        long titleId,
        long ottServiceId,
        ArrivalType type,
        LocalDate date) { // ARRIVAL이면 있음으로 바뀐 것을 확인한 날(KST), RELEASE면 공개일
}
