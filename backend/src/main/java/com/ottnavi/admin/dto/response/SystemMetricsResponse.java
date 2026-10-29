package com.ottnavi.admin.dto.response;

import com.ottnavi.admin.service.model.SystemMetricsView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 시스템 지표 응답(계약 SystemMetrics). 숫자는 바이트 단위이고 읽을 수 없는 값은 null이다. 비밀 값은 담지 않는다.
 *
 * @param jvm        JVM이 스스로 보고하는 값
 * @param process    운영체제가 보는 프로세스 메모리(Linux만, 아니면 값이 모두 null)
 * @param container  컨테이너(cgroup) 메모리 사용량·한도(없으면 값이 모두 null)
 * @param measuredAt 측정 시각(KST 오프셋)
 */
public record SystemMetricsResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) JvmMetrics jvm,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProcessMetrics process,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ContainerMetrics container,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-28T23:50:00+09:00") OffsetDateTime measuredAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static SystemMetricsResponse from(SystemMetricsView view) {
		// 1. JVM 지표
		SystemMetricsView.JvmView jvmView = view.jvm();
		List<MemoryPoolUsage> pools = new ArrayList<>();
		for (SystemMetricsView.MemoryPoolView pool : jvmView.memoryPools()) {
			MemoryArea area = MemoryArea.NON_HEAP;
			if (pool.isHeap()) {
				area = MemoryArea.HEAP;
			}
			pools.add(new MemoryPoolUsage(pool.name(), area, pool.used(), pool.committed()));
		}
		List<BufferPoolUsage> buffers = new ArrayList<>();
		for (SystemMetricsView.BufferPoolView buffer : jvmView.buffers()) {
			buffers.add(new BufferPoolUsage(buffer.id(), buffer.memoryUsed(), buffer.totalCapacity(), buffer.count()));
		}
		JvmMetrics jvm = new JvmMetrics(
				new HeapUsage(jvmView.heap().used(), jvmView.heap().committed(), jvmView.heap().max()),
				new NonHeapUsage(jvmView.nonHeap().used(), jvmView.nonHeap().committed()),
				pools, buffers,
				new ThreadStats(jvmView.threads().live(), jvmView.threads().peak(), jvmView.threads().daemon()),
				jvmView.classesLoaded(),
				new GcStats(jvmView.gc().collectionCount(), jvmView.gc().collectionTimeMillis()),
				jvmView.uptimeSeconds());
		// 2. 프로세스·컨테이너 지표
		ProcessMetrics process = new ProcessMetrics(view.process().rssBytes(), view.process().peakRssBytes(),
				view.process().rssAnonBytes(), view.process().rssFileBytes());
		ContainerMetrics container = new ContainerMetrics(view.container().memoryUsedBytes(),
				view.container().memoryLimitBytes());
		// 3. 측정 시각을 KST로
		return new SystemMetricsResponse(jvm, process, container, view.measuredAt().atZone(KST).toOffsetDateTime());
	}

	/** 메모리 풀이 속한 영역 */
	public enum MemoryArea {
		HEAP, NON_HEAP
	}

	/** JVM 지표 묶음 */
	public record JvmMetrics(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) HeapUsage heap,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) NonHeapUsage nonHeap,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MemoryPoolUsage> memoryPools,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BufferPoolUsage> buffers,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ThreadStats threads,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "14200") long classesLoaded,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) GcStats gc,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "86400") long uptimeSeconds) {
	}

	/** 힙 메모리(바이트). max는 한도를 알 수 없으면 null */
	public record HeapUsage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "120000000") long used,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "200000000") long committed,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "268435456") Long max) {
	}

	/** 힙 밖 메모리 합계(바이트) */
	public record NonHeapUsage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "90000000") long used,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100000000") long committed) {
	}

	/** 메모리 풀 하나(바이트). Micrometer jvm.memory.used·committed의 id 태그와 같은 이름이다 */
	public record MemoryPoolUsage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "G1 Eden Space") String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) MemoryArea area,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000000") long used,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "80000000") long committed) {
	}

	/** 버퍼 풀 하나(바이트). id는 direct 또는 mapped */
	public record BufferPoolUsage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "direct") String id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1048576") long memoryUsed,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1048576") long totalCapacity,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") long count) {
	}

	/** 스레드 수 */
	public record ThreadStats(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "45") long live,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "60") long peak,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") long daemon) {
	}

	/** 가비지 컬렉션 누적 */
	public record GcStats(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "34") long collectionCount,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "420") long collectionTimeMillis) {
	}

	/** 프로세스 메모리(바이트). Linux가 아니거나 읽지 못하면 null */
	public record ProcessMetrics(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "520000000") Long rssBytes,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "560000000") Long peakRssBytes,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "480000000") Long rssAnonBytes,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "40000000") Long rssFileBytes) {
	}

	/** 컨테이너 메모리(바이트). cgroup 파일이 없거나 한도가 "max"(제한 없음)이면 null */
	public record ContainerMetrics(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "700000000") Long memoryUsedBytes,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1073741824") Long memoryLimitBytes) {
	}
}
