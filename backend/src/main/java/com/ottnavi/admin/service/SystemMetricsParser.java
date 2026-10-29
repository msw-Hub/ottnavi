package com.ottnavi.admin.service;

import com.ottnavi.admin.service.model.SystemMetricsView.ProcessView;

/**
 * /proc/self/status와 cgroup 파일의 글자를 숫자로 바꾼다. 파일을 읽지 않는 순수 함수라 샘플 문자열로 테스트한다.
 * 파일이 없거나 형식이 깨졌으면 예외 대신 null을 돌려준다(지표 조회가 500이 되면 안 된다).
 */
final class SystemMetricsParser {

	private static final long BYTES_PER_KB = 1024L;
	private static final long UNLIMITED_THRESHOLD = Long.MAX_VALUE / 2; // cgroup v1은 한도가 없으면 9223372036854771712 같은 큰 수를 준다

	private SystemMetricsParser() {
	}

	/**
	 * /proc/self/status 내용에서 메모리 값 4개를 바이트로 뽑는다. 줄은 "VmRSS:\t  123456 kB" 형태다.
	 * 예: "VmRSS:   2048 kB" → rssBytes 2097152. 내용이 null이거나 해당 줄이 없으면 그 값은 null이다.
	 */
	static ProcessView parseProcStatus(String content) {
		if (content == null) {
			return new ProcessView(null, null, null, null);
		}
		return new ProcessView(
				findKbLine(content, "VmRSS"),
				findKbLine(content, "VmHWM"),
				findKbLine(content, "RssAnon"),
				findKbLine(content, "RssFile"));
	}

	/**
	 * cgroup 파일 한 줄("1073741824\n" 또는 "max\n")을 바이트 수로 바꾼다.
	 * 숫자가 아니거나("max"), 음수이거나, 한도 없음을 뜻하는 큰 수이면 null이다.
	 */
	static Long parseCgroupValue(String content) {
		if (content == null) {
			return null;
		}
		String trimmed = content.strip();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			long value = Long.parseLong(trimmed);
			if (value < 0 || value >= UNLIMITED_THRESHOLD) {
				return null;
			}
			return value;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 줄 이름(예: VmRSS)에 해당하는 "N kB" 값을 바이트로 바꾼다. 줄이 없거나 형식이 틀리면 null */
	private static Long findKbLine(String content, String key) {
		String prefix = key + ":";
		for (String line : content.split("\n")) {
			if (!line.startsWith(prefix)) {
				continue;
			}
			String[] parts = line.substring(prefix.length()).strip().split("\\s+");
			if (parts.length != 2 || !parts[1].equals("kB")) {
				return null;
			}
			try {
				long kb = Long.parseLong(parts[0]);
				if (kb < 0) {
					return null;
				}
				return kb * BYTES_PER_KB;
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}
}
