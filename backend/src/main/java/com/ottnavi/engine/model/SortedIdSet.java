package com.ottnavi.engine.model;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 상품 ID를 오름차순으로 보관하는 불변 집합. 이미 정렬된 집합임을 {@link Selection}이 알아볼 수 있게 하려고 만들었다.
 * Selection은 생성 때마다 달별 집합을 정렬 복사하는데(비교기 ⑥이 비교할 때 정렬하지 않도록), 후보 집합을 이 클래스로 한 번만 만들어 두면
 * 수백만 번 반복되는 조합 생성에서 복사를 건너뛴다. 읽기 전용이라 여러 조합이 같은 인스턴스를 공유해도 안전하다.
 */
public final class SortedIdSet extends AbstractSet<Long> {

	public static final SortedIdSet EMPTY = new SortedIdSet(new Long[0]);

	private final Long[] ids; // 오름차순, 중복·null 없음. 만든 뒤 바뀌지 않는다

	private SortedIdSet(Long[] ids) {
		this.ids = ids;
	}

	/** 상품 ID를 정렬·중복 제거해 만든다. null이 있으면 IllegalArgumentException. */
	public static SortedIdSet of(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return EMPTY;
		}
		Long[] sorted = new Long[productIds.size()];
		int index = 0;
		for (Long productId : productIds) {
			if (productId == null) {
				throw new IllegalArgumentException("상품 ID 집합에 null이 있다");
			}
			sorted[index++] = productId;
		}
		Arrays.sort(sorted);
		// 같은 값이 이어진 것을 한 번만 남긴다(입력이 Set이면 중복이 없어 그대로다)
		int unique = 1;
		for (int i = 1; i < sorted.length; i++) {
			if (!sorted[i].equals(sorted[unique - 1])) {
				sorted[unique++] = sorted[i];
			}
		}
		return new SortedIdSet(unique == sorted.length ? sorted : Arrays.copyOf(sorted, unique));
	}

	@Override
	public int size() {
		return ids.length;
	}

	@Override
	public boolean contains(Object value) {
		return value instanceof Long id && Arrays.binarySearch(ids, id) >= 0;
	}

	@Override
	public Iterator<Long> iterator() {
		return new Iterator<>() {
			private int next = 0;

			@Override
			public boolean hasNext() {
				return next < ids.length;
			}

			@Override
			public Long next() {
				if (next >= ids.length) {
					throw new NoSuchElementException();
				}
				return ids[next++];
			}
		};
	}
}
