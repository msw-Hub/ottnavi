package com.ottnavi.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.ApiDocs;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** docs/api/error-codes.md의 "공통 코드" 표와 ErrorCode enum이 같은지 검사한다 */
class ErrorCodeDocumentTest {

	// 표 행: | `CODE` | 400 ... | — 상태 칸은 첫 숫자만 읽는다
	private static final Pattern ROW = Pattern.compile("^\\|\\s*`([A-Z_]+)`\\s*\\|\\s*(\\d{3})");
	private static final String COMMON_SECTION = "## 공통 코드";

	@Test
	void 공통_코드_표와_ErrorCode_enum이_코드와_HTTP_상태까지_같다() throws IOException {
		Map<String, Integer> documented = readCommonSection(Files.readAllLines(ApiDocs.resolve("error-codes.md")));
		Map<String, Integer> implemented = Arrays.stream(ErrorCode.values())
				.collect(Collectors.toMap(Enum::name, code -> code.getStatus().value()));

		assertThat(documented).isNotEmpty();
		assertThat(implemented).containsExactlyInAnyOrderEntriesOf(documented);
	}

	/** "## 공통 코드" 다음 줄부터 다음 "## " 제목 전까지의 표 행을 읽는다 */
	private Map<String, Integer> readCommonSection(List<String> lines) {
		Map<String, Integer> codes = new LinkedHashMap<>();
		boolean inSection = false;
		for (String line : lines) {
			if (line.startsWith("## ")) {
				inSection = line.startsWith(COMMON_SECTION);
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
