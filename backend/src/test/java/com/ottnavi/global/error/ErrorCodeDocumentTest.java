package com.ottnavi.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.ApiDocs;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * docs/api/error-codes.md의 표와 ErrorCode enum을 대조한다.
 * 관계는 "공통 표 ⊆ enum ⊆ 공통 표 ∪ TECH 표"이다. TECH 표에는 아직 구현하지 않은 코드가 있어 enum이 TECH 표의 부분집합이다.
 */
class ErrorCodeDocumentTest {

	// 표 행: | `CODE` | 400 ... | — 상태 칸은 첫 숫자만 읽는다
	private static final Pattern ROW = Pattern.compile("^\\|\\s*`([A-Z_]+)`\\s*\\|\\s*(\\d{3})");
	private static final String COMMON_SECTION = "## 공통 코드";
	private static final String TECH_SECTION = "## TECH 신규 코드";

	@Test
	void 공통_표의_코드는_모두_enum에_있다() throws IOException {
		// given
		Map<String, Integer> common = readSection(Files.readAllLines(ApiDocs.resolve("error-codes.md")), COMMON_SECTION);

		// when
		Map<String, Integer> implemented = readEnum();

		// then
		assertThat(common).isNotEmpty();
		assertThat(implemented).containsAllEntriesOf(common);
	}

	@Test
	void enum의_코드는_공통_표나_TECH_표에_있고_HTTP_상태가_같다() throws IOException {
		// given
		List<String> lines = Files.readAllLines(ApiDocs.resolve("error-codes.md"));
		Map<String, Integer> documented = new LinkedHashMap<>();
		documented.putAll(readSection(lines, COMMON_SECTION));
		documented.putAll(readSection(lines, TECH_SECTION));

		// when
		Map<String, Integer> implemented = readEnum();

		// then
		assertThat(documented).containsAllEntriesOf(implemented);
	}

	private Map<String, Integer> readEnum() {
		Map<String, Integer> codes = new LinkedHashMap<>();
		for (ErrorCode code : ErrorCode.values()) {
			codes.put(code.name(), code.getStatus().value());
		}
		return codes;
	}

	/** 제목이 sectionTitle로 시작하는 "## " 절의 표 행을 다음 "## " 제목 전까지 읽는다 */
	private Map<String, Integer> readSection(List<String> lines, String sectionTitle) {
		Map<String, Integer> codes = new LinkedHashMap<>();
		boolean inSection = false;
		for (String line : lines) {
			if (line.startsWith("## ")) {
				inSection = line.startsWith(sectionTitle);
				continue;
			}
			Matcher matcher = ROW.matcher(line);
			if (inSection && matcher.find()) {
				codes.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
			}
		}
		return codes;
	}
}
