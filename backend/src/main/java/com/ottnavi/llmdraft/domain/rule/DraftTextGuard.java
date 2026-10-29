package com.ottnavi.llmdraft.domain.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 번역 초안을 서버 코드만으로 기계적으로 검사한다(LLM에게 다시 검증시키지 않는다). 프레임워크 의존이 없는 순수 Java다.
 * 사람이 초안을 하나씩 읽을 수 없으므로 결과를 세 등급으로 나눈다.
 * 실패(failures가 있음)는 반영할 수 없고, 주의(실패 없이 warnings만 있음)는 사람이 승인해야 반영되며,
 * 성공(둘 다 없음)만 "통과분 전부 반영" 대상이 된다.
 *
 * <pre>
 * 원문 "In 1999, a hacker learns the truth." / 번역문 "1999년, 한 해커가 진실을 알게 된다."
 *   → 길이 비율·한글 비율·숫자(1999) 모두 통과, 경고도 없음 → 성공
 * 번역문 "Here is the translation: 1999년 ..."
 *   → CONTAINS_PREAMBLE 로 실패
 * 번역문 "가족을 데 이고 이사한다."
 *   → 실패는 없지만 SUSPICIOUS_SPACING 경고 → 주의
 * </pre>
 */
public class DraftTextGuard {

    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.)\\S+");
    // 굵게/기울임/코드/취소선 기호, 줄 머리의 제목(#)·목록(-, *, +) 기호, 링크 형식 "]("
    private static final Pattern MARKDOWN = Pattern.compile(
            "(\\*\\*|__|`|~~|\\]\\(|(?m)^\\s{0,3}#{1,6}\\s|(?m)^\\s*[-*+]\\s)");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern WHITESPACES = Pattern.compile("\\s+");
    // 머리말·설명 문구. 소문자로 바꿔 비교한다. 원문에도 같은 문구가 있으면 번역문의 잘못이 아니므로 건너뛴다
    private static final List<String> PREAMBLES = List.of(
            "번역:", "번역문:", "번역 결과", "번역한 결과", "다음은 번역", "아래는 번역", "물론입니다", "알겠습니다",
            "here is", "here's", "translation:", "translated text", "sure,", "<original>", "</original>");

    // 한 글자 한글 낱말인데 정상으로 보는 것(관형사·접두 성격의 낱말, 흔한 한 글자 명사·부사, 단위).
    // 이 목록에 없을 때만 깨진 띄어쓰기를 의심한다("데 이고" ← "데리고")
    private static final Set<String> NORMAL_SINGLE_SYLLABLES = Set.of(
            "한", "두", "세", "네", "몇", "첫", "새", "각", "이", "그", "저", "미", "매", "번", "전", "총", "약", "등",
            "안", "못", "더", "큰", "온", "외", "본", "현", "신", "구", "내", "제", "또", "잘", "꼭", "곧", "늘", "왜",
            "뭘", "뭐", "난", "날", "별", "옛", "헌", "갓", "참", "단", "순", "대", "중", "후", "속", "간", "밖", "뒤",
            "앞", "위", "옆", "곳", "때", "수", "것", "점", "개", "명", "편", "달", "살", "분", "초", "시", "권", "장",
            "층", "회", "부", "차", "채", "척", "쌍", "벌", "병", "잔", "및", "즉", "센");
    // 다음 낱말이 이 글자로 끝나면 어미처럼 본다
    private static final String ENDING_CHARS = "고며서니다면지";
    private static final int MAX_ENDING_WORD_LENGTH = 4;   // 어미로 끝나는 "짧은 낱말"의 최대 글자 수
    private static final String TRAILING_MARKS = ".,!?…\"'”’)」』";   // 문장·낱말 끝에 붙는 기호

    private final GuardLimits limits;

    public DraftTextGuard(GuardLimits limits) {
        this.limits = limits;
    }

    /** 원문과 번역문을 비교해 걸린 이유(실패)와 의심 신호(경고)를 모두 모아 돌려준다. 번역문이 비어 있으면 EMPTY 하나만 돌려준다 */
    public GuardResult check(String original, String translated) {
        // 1. 번역문이 비어 있으면 다른 검사를 할 수 없다
        List<GuardFailure> failures = new ArrayList<>();
        List<GuardWarning> warnings = new ArrayList<>();
        if (translated == null || translated.isBlank()) {
            failures.add(GuardFailure.EMPTY);
            return new GuardResult(false, failures, warnings);
        }
        String originalText = "";
        if (original != null) {
            originalText = original;
        }

        // 2. 길이: 절대 길이와 원문 대비 비율(한계 밖이면 실패, 한계에 가까우면 경고)
        if (translated.length() > limits.maxLength()) {
            failures.add(GuardFailure.TOO_LONG);
        }
        if (!originalText.isBlank()) {
            double ratio = (double) translated.length() / originalText.length();
            if (ratio < limits.minLengthRatio()) {
                failures.add(GuardFailure.LENGTH_RATIO_LOW);
            } else if (ratio < limits.minLengthRatio() + limits.lengthRatioLowMargin()) {
                warnings.add(GuardWarning.LENGTH_RATIO_NEAR_LIMIT);
            } else if (ratio > limits.maxLengthRatio()) {
                failures.add(GuardFailure.LENGTH_RATIO_HIGH);
            } else if (ratio > limits.maxLengthRatio() - limits.lengthRatioHighMargin()) {
                warnings.add(GuardWarning.LENGTH_RATIO_NEAR_LIMIT);
            }
        }

        // 3. 한글 비율
        if (hangulRatio(translated) < limits.minHangulRatio()) {
            failures.add(GuardFailure.HANGUL_RATIO_LOW);
        }

        // 4. 주소·마크다운·머리말 (원문에 이미 있던 것은 번역문의 잘못이 아니므로 센다)
        if (URL.matcher(translated).find() && !URL.matcher(originalText).find()) {
            failures.add(GuardFailure.CONTAINS_URL);
        }
        if (MARKDOWN.matcher(translated).find() && !MARKDOWN.matcher(originalText).find()) {
            failures.add(GuardFailure.CONTAINS_MARKDOWN);
        }
        if (hasPreamble(originalText, translated)) {
            failures.add(GuardFailure.CONTAINS_PREAMBLE);
        }

        // 5. 숫자 보존: 원문의 연속된 숫자(연도·회차 등)가 번역문에 모두 있어야 한다
        if (!containsAllNumbers(originalText, translated)) {
            failures.add(GuardFailure.NUMBER_MISSING);
        }

        // 6. 경고: 실패와 별개로 문장 수·문체·띄어쓰기를 본다(반영을 막지 않고 "주의"로만 표시한다)
        collectSentenceWarnings(originalText, translated, warnings);
        if (hasSuspiciousSpacing(translated)) {
            warnings.add(GuardWarning.SUSPICIOUS_SPACING);
        }

        return new GuardResult(failures.isEmpty(), failures, warnings);
    }

    /** 문장 수 차이가 허용치를 넘으면 SENTENCE_COUNT_MISMATCH, 번역문의 "~다." 꼴 문장 비율이 낮으면 STYLE_NOT_PLAIN을 더한다 */
    private void collectSentenceWarnings(String original, String translated, List<GuardWarning> warnings) {
        List<String> translatedSentences = splitSentences(translated);
        if (!original.isBlank()) {
            int difference = Math.abs(splitSentences(original).size() - translatedSentences.size());
            if (difference > limits.sentenceCountTolerance()) {
                warnings.add(GuardWarning.SENTENCE_COUNT_MISMATCH);
            }
        }
        if (translatedSentences.isEmpty()) {
            return;
        }
        int plain = 0;
        for (String sentence : translatedSentences) {
            if (stripTrailingMarks(sentence).endsWith("다")) {
                plain++;
            }
        }
        if ((double) plain / translatedSentences.size() < limits.minPlainStyleRatio()) {
            warnings.add(GuardWarning.STYLE_NOT_PLAIN);
        }
    }

    /**
     * 글을 문장으로 나눈다. 문장 끝은 ".", "!", "?" 뒤에 공백이 오거나 글이 끝날 때다.
     * 말줄임("...")과 이니셜("J.D.", "U.S."처럼 대문자 한 글자 뒤의 점이 이어진 것)은 문장 끝으로 세지 않는다("…"도 끝 기호가 아니다).
     *
     * <pre>
     * "It is all work... During each episode we watch." → "work... During"의 말줄임은 끝이 아니라서 1개
     * "Hello. J.D. Dorian arrives."                    → "Hello." / "J.D. Dorian arrives." 2개
     * </pre>
     */
    static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && isSentenceEnd(text, i)) {
                addIfNotBlank(sentences, text.substring(start, i + 1));
                start = i + 1;
            }
        }
        addIfNotBlank(sentences, text.substring(start));
        return sentences;
    }

    private static void addIfNotBlank(List<String> sentences, String sentence) {
        String trimmed = sentence.trim();
        if (!trimmed.isEmpty()) {
            sentences.add(trimmed);
        }
    }

    /** index 위치의 ".", "!", "?"가 문장의 끝인지 */
    private static boolean isSentenceEnd(String text, int index) {
        // 1. 뒤가 공백이거나 글의 끝이어야 한다
        if (index + 1 < text.length() && !Character.isWhitespace(text.charAt(index + 1))) {
            return false;
        }
        if (text.charAt(index) == '.') {
            // 2. 말줄임("...")의 마지막 점은 끝이 아니다
            if (index > 0 && text.charAt(index - 1) == '.') {
                return false;
            }
            // 3. 이니셜("J.D.", "U.S.")의 점은 끝이 아니다: 점 바로 앞이 대문자 한 글자이고 그 앞이 또 점
            if (index >= 2 && text.charAt(index - 1) >= 'A' && text.charAt(index - 1) <= 'Z'
                    && text.charAt(index - 2) == '.') {
                return false;
            }
        }
        return true;
    }

    private static String stripTrailingMarks(String sentence) {
        int end = sentence.length();
        while (end > 0 && TRAILING_MARKS.indexOf(sentence.charAt(end - 1)) >= 0) {
            end--;
        }
        return sentence.substring(0, end);
    }

    /**
     * 한 글자 한글 낱말 바로 뒤에 어미로 끝나는 짧은 낱말이 떨어져 있으면 깨진 띄어쓰기로 의심한다("데리고" → "데 이고").
     * 정상적인 한 글자 낱말(NORMAL_SINGLE_SYLLABLES)과 숫자 바로 뒤의 단위 한 글자("3 번")는 제외한다.
     */
    private boolean hasSuspiciousSpacing(String translated) {
        String[] tokens = WHITESPACES.split(translated.trim());
        for (int i = 0; i + 1 < tokens.length; i++) {
            String current = tokens[i];
            if (!isSingleHangulSyllable(current) || NORMAL_SINGLE_SYLLABLES.contains(current)) {
                continue;
            }
            if (i > 0 && endsWithDigit(tokens[i - 1])) {
                continue;
            }
            String next = stripTrailingMarks(tokens[i + 1]);
            if (!next.isEmpty() && next.length() <= MAX_ENDING_WORD_LENGTH
                    && ENDING_CHARS.indexOf(next.charAt(next.length() - 1)) >= 0 && isAllHangul(next)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSingleHangulSyllable(String token) {
        return token.length() == 1 && isHangulSyllable(token.charAt(0));
    }

    private static boolean isAllHangul(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!isHangulSyllable(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHangulSyllable(char c) {
        return c >= '가' && c <= '힣';
    }

    private static boolean endsWithDigit(String token) {
        return !token.isEmpty() && Character.isDigit(token.charAt(token.length() - 1));
    }

    /** 글자(문자 종류가 글자인 것) 중 한글 비율. 글자가 하나도 없으면 0이다 */
    private double hangulRatio(String text) {
        int letters = 0;
        int hangul = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (!Character.isLetter(codePoint)) {
                continue;
            }
            letters++;
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL) {
                hangul++;
            }
        }
        if (letters == 0) {
            return 0.0;
        }
        return (double) hangul / letters;
    }

    private boolean hasPreamble(String original, String translated) {
        String lowerOriginal = original.toLowerCase(Locale.ROOT);
        String lowerTranslated = translated.toLowerCase(Locale.ROOT);
        for (String phrase : PREAMBLES) {
            if (lowerTranslated.contains(phrase) && !lowerOriginal.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAllNumbers(String original, String translated) {
        Set<String> translatedNumbers = new HashSet<>();
        Matcher translatedMatcher = DIGITS.matcher(translated);
        while (translatedMatcher.find()) {
            translatedNumbers.add(translatedMatcher.group());
        }
        Matcher originalMatcher = DIGITS.matcher(original);
        while (originalMatcher.find()) {
            if (!translatedNumbers.contains(originalMatcher.group())) {
                return false;
            }
        }
        return true;
    }
}
