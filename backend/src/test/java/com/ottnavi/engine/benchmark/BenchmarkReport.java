package com.ottnavi.engine.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 측정 결과를 마크다운 표로 모아 콘솔에 출력하고 build/benchmark-results/에 저장한다. */
final class BenchmarkReport {

	private static final Path OUTPUT_FILE = Path.of("build", "benchmark-results", "engine-benchmark-result.md"); // Gradle test의 작업 폴더는 backend/

	private final StringBuilder body = new StringBuilder();

	BenchmarkReport() {
		Runtime runtime = Runtime.getRuntime();
		body.append("# 엔진 성능 측정 결과 (Task 040)\n\n");
		body.append("> 주의: 이 값은 측정한 로컬 PC의 값이다. 운영(Cloudtype 프리티어 1GB)과 CPU·메모리·JIT 조건이 달라 절대값은 다르다. 상대 비교와 증가 추세로만 읽는다.\n\n");
		body.append("## 측정 환경\n\n");
		body.append("- 측정 일시: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
		body.append("- JVM: ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.version")).append('\n');
		body.append("- 최대 힙(-Xmx): ").append(runtime.maxMemory() / (1024 * 1024)).append(" MB\n");
		body.append("- JVM 인자: ").append(ManagementFactory.getRuntimeMXBean().getInputArguments()).append('\n');
		body.append("- CPU 코어 수: ").append(runtime.availableProcessors()).append('\n');
		body.append("- OS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append('\n');
		body.append("- 측정 방식: 워밍업 1회 후 3회 중앙값(워밍업이 15초를 넘는 입력은 워밍업 1회 값, 표에 \"(1회)\"로 표시). 단일 스레드\n");
		body.append("- 힙 칸은 \"풀이 중 used 최대 MB / 풀이 직전 GC 후 used MB\"이며, 최대값에는 아직 수거되지 않은 쓰레기도 포함된다\n\n");
	}

	/** 제목, 설명(비어 있을 수 있음), 표 하나를 추가한다. */
	void table(String title, String note, List<String> header, List<List<String>> rows) {
		body.append("## ").append(title).append("\n\n");
		if (note != null && !note.isBlank()) {
			body.append(note).append("\n\n");
		}
		body.append("| ").append(String.join(" | ", header)).append(" |\n");
		body.append("|").append("---|".repeat(header.size())).append('\n');
		for (List<String> row : rows) {
			body.append("| ").append(String.join(" | ", row)).append(" |\n");
		}
		body.append('\n');
	}

	/** 표 없이 문단만 추가한다. */
	void paragraph(String title, List<String> lines) {
		body.append("## ").append(title).append("\n\n");
		for (String line : lines) {
			body.append("- ").append(line).append('\n');
		}
		body.append('\n');
	}

	/** 콘솔에 출력하고 파일에 저장한다. */
	void write() {
		System.out.println(body);
		try {
			Files.createDirectories(OUTPUT_FILE.getParent());
			Files.writeString(OUTPUT_FILE, body.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("측정 결과를 저장하지 못했다: " + OUTPUT_FILE, e);
		}
		System.out.println("결과 저장: " + OUTPUT_FILE.toAbsolutePath());
	}
}
