package com.dividendanatomy.domain.dividend;

/**
 * 정기/특별배당 구분. CLAUDE.md: "특별배당은 정기 배당과 분리한다 —
 * 자동으로 합산하지 않는다." 분류 로직 자체는 이 열거형의 책임이 아니다.
 */
public enum DividendType {
    REGULAR,
    SPECIAL
}
