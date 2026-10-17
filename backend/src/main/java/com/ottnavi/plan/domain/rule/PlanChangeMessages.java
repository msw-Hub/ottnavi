package com.ottnavi.plan.domain.rule;

import com.ottnavi.plan.domain.enums.PlanTitleChangeType;
import java.util.List;

/**
 * 변경 요약·이유 문장을 만든다(Task 068 결정 6: 문장은 서버가 만든다). 순수 문자열 규칙이다.
 * 조사(이/가, 을/를, 으로/로)는 앞 단어의 받침으로 고른다.
 *
 * <pre>
 * 이전 [넷플릭스] → 새 [디즈니+], 11월  → "11월 구독이 넷플릭스에서 디즈니+로 바뀌었어요"
 * 제목 "카지노", 시즌 1, 서비스 "디즈니+" → "'카지노 시즌 1'이 디즈니+에서 볼 수 있는 것으로 확인됐어요"
 * </pre>
 */
public final class PlanChangeMessages {

	private PlanChangeMessages() {
	}

	/** 달 변화 요약 문장. 이전·새 구독 서비스 이름 목록의 비어 있음에 따라 네 가지 문장 중 하나를 쓴다 */
	public static String monthSummary(int monthValue, List<String> previousNames, List<String> currentNames) {
		String month = monthValue + "월";
		if (previousNames.isEmpty() && currentNames.isEmpty()) {
			return month + " 구독 요금이 바뀌었어요";
		}
		if (previousNames.isEmpty()) {
			String now = join(currentNames);
			return month + "에 " + now + eulReul(now) + " 새로 구독하게 됐어요";
		}
		if (currentNames.isEmpty()) {
			return month + "에는 " + join(previousNames) + " 구독이 빠졌어요";
		}
		String before = join(previousNames);
		String after = join(currentNames);
		if (before.equals(after)) {
			return month + " " + before + " 구독 요금이 바뀌었어요";
		}
		return month + " 구독이 " + before + "에서 " + after + euroRo(after) + " 바뀌었어요";
	}

	/** 작품 변화 요약 문장 (작품 기준 요약, 달 변화가 하나도 없을 때 쓴다) */
	public static String titleSummary(PlanTitleChangeType type, String titleLabel, Integer previousMonthValue,
			Integer currentMonthValue, String previousServiceName, String currentServiceName) {
		String quoted = "'" + titleLabel + "'";
		if (type == PlanTitleChangeType.ADDED) {
			return quoted + eulReul(titleLabel) + " " + currentMonthValue + "월에 " + currentServiceName + "에서 보도록 추가했어요";
		}
		if (type == PlanTitleChangeType.REMOVED) {
			return quoted + eulReul(titleLabel) + " 플랜에서 뺐어요";
		}
		if (type == PlanTitleChangeType.MOVED_MONTH) {
			return quoted + " 시청이 " + previousMonthValue + "월에서 " + currentMonthValue + "월"
					+ euroRo(currentMonthValue + "월") + " 옮겨졌어요";
		}
		return quoted + eulReul(titleLabel) + " " + previousServiceName + " 대신 " + currentServiceName + "에서 보도록 바뀌었어요";
	}

	/** 요약에 "(외 N건)"을 붙인다. 다른 변화가 없으면 그대로 돌려준다 */
	public static String withOthers(String sentence, int others) {
		if (others <= 0) {
			return sentence;
		}
		return sentence + " (외 " + others + "건)";
	}

	/** 이유: 요금 변경 (예: "디즈니+ 스탠다드 요금이 9,900원에서 11,900원으로 바뀌었어요") */
	public static String priceChanged(String productName, int previousPrice, int currentPrice, int others) {
		String after = won(currentPrice);
		String sentence = productName + " 요금이 " + won(previousPrice) + "에서 " + after + euroRo(after) + " 바뀌었어요";
		return withOthers(sentence, others);
	}

	/** 이유: 작품이 서비스에서 빠짐 */
	public static String titleLeftService(String titleLabel, String serviceName, int others) {
		return withOthers("'" + titleLabel + "'" + iGa(titleLabel) + " " + serviceName + "에서 빠진 것으로 확인됐어요", others);
	}

	/** 이유: 작품이 서비스에 새로 들어옴 */
	public static String titleJoinedService(String titleLabel, String serviceName, int others) {
		return withOthers("'" + titleLabel + "'" + iGa(titleLabel) + " " + serviceName + "에서 볼 수 있는 것으로 확인됐어요", others);
	}

	/** 이유: 다 봤음 체크 */
	public static String markedWatched(String titleLabel, int others) {
		return withOthers("'" + titleLabel + "'" + eulReul(titleLabel) + " 다 봤음으로 체크해 계산에서 뺐어요", others);
	}

	/** 제목에 시즌 번호를 붙인 표시 이름 (예: "카지노 시즌 1", 영화는 제목 그대로) */
	public static String titleLabel(String title, Integer seasonNumber) {
		if (seasonNumber == null) {
			return title;
		}
		return title + " 시즌 " + seasonNumber;
	}

	// 조사 선택

	/** 이/가 */
	static String iGa(String word) {
		if (hasBatchim(word)) {
			return "이";
		}
		return "가";
	}

	/** 을/를 */
	static String eulReul(String word) {
		if (hasBatchim(word)) {
			return "을";
		}
		return "를";
	}

	/** 으로/로 (받침이 없거나 ㄹ 받침이면 로) */
	static String euroRo(String word) {
		if (!hasBatchim(word) || endsWithRieul(word)) {
			return "로";
		}
		return "으로";
	}

	/**
	 * 마지막 글자에 받침이 있는지. 한글은 유니코드 계산, 숫자는 읽는 소리(영·일·삼·육·칠·팔)로 판단한다.
	 * 로마자·기호(예: 디즈니+의 +는 "플러스"로 읽어 받침 없음)는 받침 없음으로 본다.
	 */
	static boolean hasBatchim(String word) {
		char last = lastChar(word);
		if (last >= 0xAC00 && last <= 0xD7A3) {
			return (last - 0xAC00) % 28 != 0;
		}
		if (last >= '0' && last <= '9') {
			return "013678".indexOf(last) >= 0;
		}
		return false;
	}

	private static boolean endsWithRieul(String word) {
		char last = lastChar(word);
		if (last >= 0xAC00 && last <= 0xD7A3) {
			return (last - 0xAC00) % 28 == 8;
		}
		return last == '1' || last == '7' || last == '8'; // 일·칠·팔은 ㄹ 받침
	}

	private static char lastChar(String word) {
		if (word == null || word.isEmpty()) {
			return ' ';
		}
		return word.charAt(word.length() - 1);
	}

	private static String join(List<String> names) {
		return String.join("·", names);
	}

	private static String won(int amount) {
		return String.format("%,d원", amount);
	}
}
