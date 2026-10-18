package com.ottnavi.catalog.domain.rule;

import java.util.List;

/**
 * 한 서비스에서 독점으로 확인된 작품 하나.
 * 드라마는 시즌 단위로 판정하므로 같은 작품의 독점 시즌들을 한 묶음으로 모은다.
 */
public record ExclusiveTitleGroup(
        long titleId, // 작품 ID
        List<Integer> seasonNumbers, // 독점인 시즌 번호(오름차순). 영화는 null
        int comparedServiceCount, // 비교한 다른 서비스 수(구조적 공백은 뺀다). 시청 단위가 여럿이면 가장 큰 값
        int unknownElsewhereCount) { // 비교한 다른 서비스 중 "모름"인 곳의 수. 시청 단위가 여럿이면 가장 큰 값
}
