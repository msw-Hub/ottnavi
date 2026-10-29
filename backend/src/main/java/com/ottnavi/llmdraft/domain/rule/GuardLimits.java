package com.ottnavi.llmdraft.domain.rule;

/**
 * 자동 검사 기준 수치(app.llm.guard.*).
 *
 * @param minHangulRatio         글자(문자 종류가 글자인 것) 중 한글 비율의 하한
 * @param minLengthRatio         번역문 글자 수 / 원문 글자 수의 하한
 * @param maxLengthRatio         번역문 글자 수 / 원문 글자 수의 상한
 * @param maxLength              번역문 최대 글자 수
 * @param sentenceCountTolerance 원문과 번역문의 문장 수 차이 허용치(이 값을 넘으면 경고)
 * @param minPlainStyleRatio     "~다." 꼴 문장 비율의 하한(미만이면 경고)
 * @param lengthRatioLowMargin   길이 비율 하한에서 이만큼 위까지를 "한계에 가깝다"고 본다
 * @param lengthRatioHighMargin  길이 비율 상한에서 이만큼 아래부터를 "한계에 가깝다"고 본다
 */
public record GuardLimits(double minHangulRatio, double minLengthRatio, double maxLengthRatio, int maxLength,
                          int sentenceCountTolerance, double minPlainStyleRatio, double lengthRatioLowMargin,
                          double lengthRatioHighMargin) {

    /** 경고 기준은 기본값(문장 수 허용 1, 한다체 0.8, 마진 0.1·0.4)으로 두는 간단한 생성자 */
    public GuardLimits(double minHangulRatio, double minLengthRatio, double maxLengthRatio, int maxLength) {
        this(minHangulRatio, minLengthRatio, maxLengthRatio, maxLength, 1, 0.8, 0.1, 0.4);
    }
}
