package com.ottnavi.admin.service;

import com.ottnavi.admin.service.model.SystemMetricsView;
import com.ottnavi.admin.service.model.SystemMetricsView.BufferPoolView;
import com.ottnavi.admin.service.model.SystemMetricsView.ContainerView;
import com.ottnavi.admin.service.model.SystemMetricsView.GcView;
import com.ottnavi.admin.service.model.SystemMetricsView.HeapView;
import com.ottnavi.admin.service.model.SystemMetricsView.JvmView;
import com.ottnavi.admin.service.model.SystemMetricsView.MemoryPoolView;
import com.ottnavi.admin.service.model.SystemMetricsView.NonHeapView;
import com.ottnavi.admin.service.model.SystemMetricsView.ProcessView;
import com.ottnavi.admin.service.model.SystemMetricsView.ThreadView;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 운영 서버의 메모리 구성을 읽는 관리자용 지표 조회. 읽기 전용이며 비밀 값은 담지 않는다.
 * 운영 컨테이너에는 jcmd가 없고 브라우저에서 /actuator를 부를 수 없어 API로 연 것이다.
 * JVM 값은 java.lang.management(JMX)에서 직접 읽는다. Micrometer jvm.memory.* 지표가 같은 MXBean에서 오므로
 * 값이 같고, 레지스트리 태그 검색 없이 단위 테스트하기 쉽다.
 * 운영체제 파일(/proc, cgroup)은 Linux 컨테이너에만 있어, 없으면 null로 두고 오류를 내지 않는다.
 */
@Service
public class SystemMetricsService {

	private final Path procStatusPath;
	private final Path cgroupV2CurrentPath;
	private final Path cgroupV2MaxPath;
	private final Path cgroupV1UsagePath;
	private final Path cgroupV1LimitPath;

	/** 운영 기본 경로로 만든다 */
	@Autowired
	public SystemMetricsService() {
		this(Path.of("/proc/self/status"),
				Path.of("/sys/fs/cgroup/memory.current"), Path.of("/sys/fs/cgroup/memory.max"),
				Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes"),
				Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes"));
	}

	/** 테스트에서 샘플 파일 경로를 넣을 수 있게 열어 둔 생성자 */
	SystemMetricsService(Path procStatusPath, Path cgroupV2CurrentPath, Path cgroupV2MaxPath,
			Path cgroupV1UsagePath, Path cgroupV1LimitPath) {
		this.procStatusPath = procStatusPath;
		this.cgroupV2CurrentPath = cgroupV2CurrentPath;
		this.cgroupV2MaxPath = cgroupV2MaxPath;
		this.cgroupV1UsagePath = cgroupV1UsagePath;
		this.cgroupV1LimitPath = cgroupV1LimitPath;
	}

	/** JVM·프로세스·컨테이너 메모리를 지금 시점으로 측정한다 */
	public SystemMetricsView measure() {
		// 1. JVM 값(JMX)
		JvmView jvm = measureJvm();
		// 2. 프로세스 메모리(/proc/self/status, Linux만)
		ProcessView process = SystemMetricsParser.parseProcStatus(readFileOrNull(procStatusPath));
		// 3. 컨테이너 메모리(cgroup v2 → 없으면 v1)
		ContainerView container = measureContainer();
		return new SystemMetricsView(jvm, process, container, Instant.now());
	}

	/** 프로세스 RSS 바이트. 지표 게이지용이며 값을 읽을 수 없는 환경(Windows·로컬)에서는 NaN이다 */
	public double readProcessRssBytes() {
		Long rss = SystemMetricsParser.parseProcStatus(readFileOrNull(procStatusPath)).rssBytes();
		if (rss == null) {
			return Double.NaN;
		}
		return rss;
	}

	/** 컨테이너(cgroup) 메모리 사용 바이트. 지표 게이지용이며 값을 읽을 수 없으면 NaN이다 */
	public double readContainerMemoryUsedBytes() {
		Long used = measureContainer().memoryUsedBytes();
		if (used == null) {
			return Double.NaN;
		}
		return used;
	}

	private JvmView measureJvm() {
		// 1. 힙·힙 밖 합계
		MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
		MemoryUsage heapUsage = memory.getHeapMemoryUsage();
		MemoryUsage nonHeapUsage = memory.getNonHeapMemoryUsage();
		Long heapMax = null;
		if (heapUsage.getMax() >= 0) {
			heapMax = heapUsage.getMax(); // 한도를 알 수 없으면 JVM이 -1을 준다
		}
		HeapView heap = new HeapView(heapUsage.getUsed(), heapUsage.getCommitted(), heapMax);
		NonHeapView nonHeap = new NonHeapView(nonHeapUsage.getUsed(), nonHeapUsage.getCommitted());

		// 2. 메모리 풀별 값(G1 Eden/Old/Survivor, Metaspace, CodeHeap, Compressed Class Space 등)
		List<MemoryPoolView> pools = new ArrayList<>();
		for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
			MemoryUsage usage = pool.getUsage();
			if (usage == null) {
				continue; // 더는 유효하지 않은 풀
			}
			pools.add(new MemoryPoolView(pool.getName(), pool.getType() == MemoryType.HEAP,
					usage.getUsed(), usage.getCommitted()));
		}

		// 3. 버퍼 풀(direct, mapped)
		List<BufferPoolView> buffers = new ArrayList<>();
		for (BufferPoolMXBean buffer : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
			buffers.add(new BufferPoolView(buffer.getName(), buffer.getMemoryUsed(), buffer.getTotalCapacity(),
					buffer.getCount()));
		}

		// 4. 스레드·로드된 클래스
		ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
		ThreadView threads = new ThreadView(threadBean.getThreadCount(), threadBean.getPeakThreadCount(),
				threadBean.getDaemonThreadCount());
		long classesLoaded = ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();

		// 5. GC 누적(수집기가 값을 줄 수 없으면 -1이라 더하지 않는다)
		long gcCount = 0;
		long gcTimeMillis = 0;
		for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (gcBean.getCollectionCount() > 0) {
				gcCount += gcBean.getCollectionCount();
			}
			if (gcBean.getCollectionTime() > 0) {
				gcTimeMillis += gcBean.getCollectionTime();
			}
		}

		// 6. 가동 시간
		long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
		return new JvmView(heap, nonHeap, pools, buffers, threads, classesLoaded, new GcView(gcCount, gcTimeMillis),
				uptimeSeconds);
	}

	/** cgroup v2 파일이 읽히면 그 값을, 아니면 v1 파일을, 둘 다 없으면 null 두 개를 돌려준다 */
	private ContainerView measureContainer() {
		String v2Current = readFileOrNull(cgroupV2CurrentPath);
		if (v2Current != null) {
			return new ContainerView(SystemMetricsParser.parseCgroupValue(v2Current),
					SystemMetricsParser.parseCgroupValue(readFileOrNull(cgroupV2MaxPath)));
		}
		String v1Usage = readFileOrNull(cgroupV1UsagePath);
		if (v1Usage != null) {
			return new ContainerView(SystemMetricsParser.parseCgroupValue(v1Usage),
					SystemMetricsParser.parseCgroupValue(readFileOrNull(cgroupV1LimitPath)));
		}
		return new ContainerView(null, null);
	}

	/** 파일 내용을 읽는다. 없거나 읽지 못하면 null (Windows 로컬에서도 오류가 나지 않게) */
	private String readFileOrNull(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			return null;
		}
	}
}
