package com.ottnavi.catalog.service.model;

import java.util.List;

/**
 * 번역 되돌리기 한 묶음의 결과.
 *
 * @param keys          이 묶음에서 읽은 작품 키(상세 캐시를 지울 대상. 그 사이 번역이 아니게 된 작품이 섞여도 지워서 해가 없다)
 * @param revertedCount 실제로 되돌린 작품 수
 */
public record TranslatedRevertBatch(List<TitleKey> keys, int revertedCount) {
}
