package com.ottnavi.admin.service.model;

import java.time.Instant;
import java.util.List;

/**
 * 서버의 메모리·스레드 상태를 한 번 측정한 값. 숫자는 바이트 단위이고, 읽을 수 없는 값은 null이다.
 *
 * @param jvm        JVM이 스스로 보고하는 값
 * @param process    운영체제가 보는 프로세스 메모리(Linux의 /proc/self/status). 읽지 못하면 모든 값이 null
 * @param container  컨테이너(cgroup) 메모리 사용량과 한도. 읽지 못하면 모든 값이 null
 * @param measuredAt 측정 시각
 */
public record SystemMetricsView(JvmView jvm, ProcessView process, ContainerView container, Instant measuredAt) {

	/** JVM 지표 묶음 */
	public record JvmView(
			HeapView heap,
			NonHeapView nonHeap,
			List<MemoryPoolView> memoryPools,
			List<BufferPoolView> buffers,
			ThreadView threads,
			long classesLoaded,
			GcView gc,
			long uptimeSeconds) {
	}

	/** 힙 메모리. max는 한도를 알 수 없으면 null */
	public record HeapView(long used, long committed, Long max) {
	}

	/** 힙 밖 메모리(메타스페이스·코드 캐시 등의 합) */
	public record NonHeapView(long used, long committed) {
	}

	/** 메모리 풀 하나(예: G1 Eden Space, Metaspace). isHeap은 힙 영역 여부 */
	public record MemoryPoolView(String name, boolean isHeap, long used, long committed) {
	}

	/** 버퍼 풀 하나(direct, mapped). 힙 밖 네이티브 메모리를 쓰는 NIO 버퍼 */
	public record BufferPoolView(String id, long memoryUsed, long totalCapacity, long count) {
	}

	/** 스레드 수 */
	public record ThreadView(long live, long peak, long daemon) {
	}

	/** 가비지 컬렉션 누적(모든 수집기 합계) */
	public record GcView(long collectionCount, long collectionTimeMillis) {
	}

	/** 프로세스 메모리. rss는 실제로 올라가 있는 물리 메모리, peak는 그 최고치, anon은 힙·스택 같은 익명 메모리, file은 파일 매핑 */
	public record ProcessView(Long rssBytes, Long peakRssBytes, Long rssAnonBytes, Long rssFileBytes) {
	}

	/** 컨테이너 메모리 사용량과 한도(한도가 없으면 limit이 null) */
	public record ContainerView(Long memoryUsedBytes, Long memoryLimitBytes) {
	}
}
