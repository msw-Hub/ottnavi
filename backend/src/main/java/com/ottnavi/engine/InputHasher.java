package com.ottnavi.engine;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.WatchUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 계산 입력의 정규화 해시(input_hash)를 만든다. 같은 입력이면 순서가 달라도 같은 해시가 나와 계산 결과 캐시·비교 기록의 키로 쓴다(TECH 1절).
 * 순수 Java이며 상태가 없다.
 *
 * <p>정규화 문자열 형식(구분자 고정, 앞에 버전 {@value #FORMAT_VERSION}):
 * {@code v2|budget=…|minutes=…|asOf=…|products=id:price:condition;…|units=id:priority:minutes:watchableIds:unknown:titleId:seasonNumber;…}
 * 시청 단위는 id 오름차순, 상품은 id 오름차순, 시청 가능 상품 ID는 오름차순으로 정렬해 잇는다.
 * titleId·seasonNumber는 시즌 순서(PRD 5.4)가 배정 결과를 바꾸므로 v2에서 넣었다. 영화는 둘 다 '-'다.
 * 참고: 이 해시를 저장하는 곳(plan.input_hash 등)이 아직 없어 v1 해시와의 호환 문제는 없다. 저장을 도입한 뒤에는 형식을 바꾸면 기존 값이 무효가 된다.
 * 형식을 바꾸면 기존 해시가 모두 달라지므로 {@code FORMAT_VERSION}을 올린다.
 */
public final class InputHasher {

	private static final String FORMAT_VERSION = "v2"; // 정규화 형식 버전. 형식을 바꾸면 올린다

	private InputHasher() {
	}

	/** 입력과 데이터 기준일로 SHA-256 소문자 hex(64자) 해시를 만든다. */
	public static String hash(PlanInput input, LocalDate dataAsOf) {
		if (input == null || dataAsOf == null) {
			throw new IllegalArgumentException("입력과 데이터 기준일은 null일 수 없다");
		}
		return sha256Hex(canonicalize(input, dataAsOf));
	}

	/** 해시 직전의 정규화 문자열을 만든다(디버깅·테스트용으로 공개). */
	public static String canonicalize(PlanInput input, LocalDate dataAsOf) {
		if (input == null || dataAsOf == null) {
			throw new IllegalArgumentException("입력과 데이터 기준일은 null일 수 없다");
		}
		// PlanInput.units()는 배정 순서로 정렬돼 있어 해시용으로 id 순으로 다시 정렬한다
		String units = input.units().stream()
				.sorted(Comparator.comparingLong(WatchUnit::id))
				.map(InputHasher::canonicalize)
				.collect(Collectors.joining(";"));
		String products = input.products().stream()
				.sorted(Comparator.comparingLong(OttProduct::id))
				.map(product -> product.id() + ":" + product.monthlyPrice() + ":" + product.condition().name())
				.collect(Collectors.joining(";"));
		return FORMAT_VERSION
				+ "|budget=" + input.monthlyBudget()
				+ "|minutes=" + input.monthlyWatchMinutes()
				+ "|asOf=" + dataAsOf // LocalDate.toString()은 ISO-8601(yyyy-MM-dd)
				+ "|products=" + products
				+ "|units=" + units;
	}

	/** 시청 단위 하나를 id:우선순위:필요 분:시청 가능 상품 ID(오름차순, 쉼표):모름 여부:titleId:시즌 번호(영화는 -) 로 바꾼다. */
	private static String canonicalize(WatchUnit unit) {
		List<Long> watchableIds = unit.watchableProductIds().stream().sorted().toList();
		String ids = watchableIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		return unit.id() + ":" + unit.priority().name() + ":" + unit.requiredMinutes() + ":" + ids + ":" + unit.isProviderUnknown()
				+ ":" + (unit.titleId() == null ? "-" : unit.titleId()) + ":" + (unit.seasonNumber() == null ? "-" : unit.seasonNumber());
	}

	private static String sha256Hex(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest); // HexFormat 기본값이 소문자다
		} catch (NoSuchAlgorithmException e) {
			// SHA-256은 모든 Java 구현이 반드시 제공해야 해서 실제로는 일어나지 않는다
			throw new IllegalStateException("SHA-256을 사용할 수 없다", e);
		}
	}
}
