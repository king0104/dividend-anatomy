package com.dividendanatomy.domain.safety;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 5개 재무 지표(배당성향·FCF 대비 배당 비율·ROE·부채비율(D/E)·이자보상배율)를
 * 각 20점 만점으로 선형보간해 0~100점 하나로 합산한다. 계산식·임계값의
 * 출처는 전부 docs/decisions/14-dividend-safety-score-formula.md에 근거를
 * 남겼다 — 임의로 정한 숫자가 아니다. 입력은 null을 받지 않는다(5개 원자재
 * 값 중 하나라도 없으면 호출부(서비스 계층)가 이 계산기를 아예 호출하지
 * 않고 "점수 계산 불가"로 처리한다 — CLAUDE.md 데이터 불완전 원칙).
 */
public final class DividendSafetyScoreCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);

    private static final BigDecimal PAYOUT_SAFE = new BigDecimal("0.60");
    private static final BigDecimal PAYOUT_RISK = new BigDecimal("1.00");
    private static final BigDecimal FCF_PAYOUT_SAFE = new BigDecimal("0.70");
    private static final BigDecimal FCF_PAYOUT_RISK = new BigDecimal("1.00");
    private static final BigDecimal ROE_SAFE = new BigDecimal("0.15");
    private static final BigDecimal ROE_RISK = BigDecimal.ZERO;
    private static final BigDecimal DEBT_TO_EQUITY_SAFE = new BigDecimal("1.00");
    private static final BigDecimal DEBT_TO_EQUITY_RISK = new BigDecimal("2.00");
    private static final BigDecimal INTEREST_COVERAGE_SAFE = new BigDecimal("3.00");
    private static final BigDecimal INTEREST_COVERAGE_RISK = new BigDecimal("1.50");

    private static final BigDecimal GREEN_MIN = BigDecimal.valueOf(61);
    private static final BigDecimal YELLOW_MIN = BigDecimal.valueOf(41);

    private DividendSafetyScoreCalculator() {
    }

    public static DividendSafetyScoreResult calculate(
            BigDecimal payoutRatio,
            BigDecimal fcfPayoutRatio,
            BigDecimal returnOnEquity,
            BigDecimal debtToEquity,
            BigDecimal interestCoverage) {
        requireNonNull(payoutRatio, "payoutRatio");
        requireNonNull(fcfPayoutRatio, "fcfPayoutRatio");
        requireNonNull(returnOnEquity, "returnOnEquity");
        requireNonNull(debtToEquity, "debtToEquity");
        requireNonNull(interestCoverage, "interestCoverage");

        BigDecimal payoutSubScore = linearScore(payoutRatio, PAYOUT_SAFE, PAYOUT_RISK, false);
        BigDecimal fcfPayoutSubScore = linearScore(fcfPayoutRatio, FCF_PAYOUT_SAFE, FCF_PAYOUT_RISK, false);
        BigDecimal roeSubScore = linearScore(returnOnEquity, ROE_SAFE, ROE_RISK, true);
        BigDecimal debtToEquitySubScore = linearScore(debtToEquity, DEBT_TO_EQUITY_SAFE, DEBT_TO_EQUITY_RISK, false);
        BigDecimal interestCoverageSubScore = linearScore(interestCoverage, INTEREST_COVERAGE_SAFE, INTEREST_COVERAGE_RISK, true);

        BigDecimal totalScore = payoutSubScore
                .add(fcfPayoutSubScore, MC)
                .add(roeSubScore, MC)
                .add(debtToEquitySubScore, MC)
                .add(interestCoverageSubScore, MC);

        SafetyBand band = bandOf(totalScore);

        return new DividendSafetyScoreResult(
                payoutRatio, payoutSubScore,
                fcfPayoutRatio, fcfPayoutSubScore,
                returnOnEquity, roeSubScore,
                debtToEquity, debtToEquitySubScore,
                interestCoverage, interestCoverageSubScore,
                totalScore, band);
    }

    /**
     * 안전 경계(safeEdge)보다 좋으면 20점, 위험 경계(riskEdge)보다 나쁘면
     * 0점으로 클램프하고 그 사이는 선형보간한다. higherIsBetter=true면
     * safeEdge가 riskEdge보다 큰 값(ROE·이자보상배율), false면 safeEdge가
     * riskEdge보다 작은 값(배당성향·FCF대비배당·부채비율)이다.
     */
    private static BigDecimal linearScore(
            BigDecimal value, BigDecimal safeEdge, BigDecimal riskEdge, boolean higherIsBetter) {
        if (higherIsBetter) {
            if (value.compareTo(safeEdge) >= 0) {
                return TWENTY;
            }
            if (value.compareTo(riskEdge) <= 0) {
                return BigDecimal.ZERO;
            }
            return TWENTY.multiply(value.subtract(riskEdge, MC), MC).divide(safeEdge.subtract(riskEdge, MC), MC);
        } else {
            if (value.compareTo(safeEdge) <= 0) {
                return TWENTY;
            }
            if (value.compareTo(riskEdge) >= 0) {
                return BigDecimal.ZERO;
            }
            return TWENTY.multiply(riskEdge.subtract(value, MC), MC).divide(riskEdge.subtract(safeEdge, MC), MC);
        }
    }

    private static SafetyBand bandOf(BigDecimal totalScore) {
        if (totalScore.compareTo(GREEN_MIN) >= 0) {
            return SafetyBand.GREEN;
        }
        if (totalScore.compareTo(YELLOW_MIN) >= 0) {
            return SafetyBand.YELLOW;
        }
        return SafetyBand.RED;
    }

    private static void requireNonNull(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + "는 null일 수 없다");
        }
    }
}
